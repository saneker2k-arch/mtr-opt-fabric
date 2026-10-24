package dev.mtropt;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Замена статического списка CachedResource.CACHED_RESOURCES из MTR 4.0.5.
 *
 * Что не так в MTR: список только растёт (удаления нет). Каждый CachedResource, когда-либо созданный
 * (а цепочка кэша вагона создаёт новые дочерние ресурсы при каждой пересборке родителя), остаётся в нём навсегда
 * вместе с загрузчиком, который держит сырые данные модели. Плюс каждый тик MTR обходит весь список.
 *
 * Здесь ресурсы регистрируются слабыми ссылками, мёртвые записи удаляются, а статический список MTR очищается
 * каждый тик (держит ссылки не дольше одного тика). Все данные, у которых вышел срок, освобождаются через
 * {@link GpuRelease} с задержкой в 1-2 тика (после того как кадр, который мог их использовать, точно дорисован).
 */
public final class ResourceRegistry {

	private static final class Ref extends WeakReference<CachedResourceAccess> {

		final long sequence;

		Ref(CachedResourceAccess referent, long sequence) {
			super(referent);
			this.sequence = sequence;
		}
	}

	private static final int MAX_DEPTH = 24;
	private static final AtomicLong SEQUENCE = new AtomicLong();
	private static final ConcurrentLinkedQueue<Ref> PENDING = new ConcurrentLinkedQueue<>();
	private static final ArrayList<Ref> LIVE = new ArrayList<>();
	private static final Object RELEASE_LOCK = new Object();

	private static List<Object> releaseCurrent = new ArrayList<>();
	private static List<Object> releasePrevious = new ArrayList<>();

	private static volatile Thread renderThread;
	private static volatile long disposeAllUpTo;

	private static final long[] startNanos = new long[MAX_DEPTH];
	private static final boolean[] fetchableAtStart = new boolean[MAX_DEPTH];
	private static int depth;
	private static long spentNanos;

	private static long totalBuildNanos;
	private static long worstTickBuildNanos;
	private static int statsTicks;

	private static Field staticListField;
	private static boolean staticListFailed;

	private ResourceRegistry() {
	}

	// ---------------------------------------------------------------- регистрация

	public static void register(CachedResourceAccess resource) {
		PENDING.add(new Ref(resource, SEQUENCE.incrementAndGet()));
	}

	/** Освободить все уже созданные ресурсы (перезагрузка ресурсов MTR): их модели пересоберутся по мере надобности. */
	public static void requestDisposeAll() {
		disposeAllUpTo = SEQUENCE.get();
	}

	public static int liveCount() {
		return LIVE.size();
	}

	// ---------------------------------------------------------------- тик

	public static void tick() {
		renderThread = Thread.currentThread();
		depth = 0;
		totalBuildNanos += spentNanos;
		worstTickBuildNanos = Math.max(worstTickBuildNanos, spentNanos);
		spentNanos = 0;
		clearMtrStaticList();
		processReleases();

		Ref ref;
		while ((ref = PENDING.poll()) != null) {
			LIVE.add(ref);
		}

		final long now = System.currentTimeMillis();
		final long disposeUpTo = disposeAllUpTo;
		for (int i = LIVE.size() - 1; i >= 0; i--) {
			final Ref entry = LIVE.get(i);
			final CachedResourceAccess resource = entry.get();
			if (resource == null) {
				final int last = LIVE.size() - 1;
				LIVE.set(i, LIVE.get(last));
				LIVE.remove(last);
				continue;
			}
			if (now > resource.mtropt$getExpiry() || entry.sequence <= disposeUpTo) {
				final Object data = resource.mtropt$getData();
				if (data != null) {
					resource.mtropt$setData(null);
					resource.mtropt$setKind(0);
					queueRelease(data);
				}
			}
		}
		if (disposeUpTo != 0 && disposeAllUpTo == disposeUpTo) {
			disposeAllUpTo = 0;
		}
		VehiclePrewarm.run();
		logStats();
	}

	private static void logStats() {
		final int seconds = OptConfig.get().statsLogIntervalSeconds;
		if (seconds <= 0 || ++statsTicks < seconds * 20) {
			return;
		}
		statsTicks = 0;
		final Runtime runtime = Runtime.getRuntime();
		MtrOptClient.LOGGER.info("MTR Optimization Addon stats: cachedResources={}, modelsReleased={}, glBuffersFreed={}, modelBuildTotal={} ms, worstTickBuild={} ms, heapUsed={} MB",
				LIVE.size(), GpuRelease.releasedModels(), GpuRelease.totalFreedBuffers(),
				totalBuildNanos / 1_000_000L, worstTickBuildNanos / 1_000_000L,
				(runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
		worstTickBuildNanos = 0;
	}

	/** MTR добавляет каждый новый CachedResource в свой статический список; чистим его, чтобы он ничего не удерживал. */
	private static void clearMtrStaticList() {
		if (staticListFailed) {
			return;
		}
		try {
			if (staticListField == null) {
				final Field field = Class.forName("org.mtr.mod.resource.CachedResource").getDeclaredField("CACHED_RESOURCES");
				field.setAccessible(true);
				staticListField = field;
			}
			final Object list = staticListField.get(null);
			if (list instanceof List<?> l && !l.isEmpty()) {
				l.clear();
			}
		} catch (Throwable e) {
			staticListFailed = true;
			MtrOptClient.LOGGER.warn("Не удалось получить доступ к CachedResource.CACHED_RESOURCES, очистка списка отключена", e);
		}
	}

	// ---------------------------------------------------------------- отложенное освобождение

	public static void queueRelease(Object data) {
		synchronized (RELEASE_LOCK) {
			releaseCurrent.add(data);
		}
	}

	private static void processReleases() {
		final List<Object> batch;
		synchronized (RELEASE_LOCK) {
			batch = releasePrevious;
			releasePrevious = releaseCurrent;
			releaseCurrent = new ArrayList<>();
		}
		for (final Object data : batch) {
			GpuRelease.release(data);
		}
	}

	// ---------------------------------------------------------------- бюджет сборки моделей

	/** Вызывается в начале CachedResource.getData. */
	public static void enter(boolean canFetchNow) {
		if (Thread.currentThread() != renderThread || depth >= MAX_DEPTH) {
			return;
		}
		startNanos[depth] = System.nanoTime();
		fetchableAtStart[depth] = canFetchNow;
		depth++;
	}

	/**
	 * Вызывается в конце CachedResource.getData.
	 *
	 * @return true, если MTR нужно разрешить ещё одну сборку модели в этом тике
	 */
	public static boolean exit(boolean canFetchNow) {
		if (Thread.currentThread() != renderThread || depth <= 0) {
			return false;
		}
		depth--;
		if (!fetchableAtStart[depth] || canFetchNow) {
			return false;
		}
		// В этом вызове что-то собрали: MTR сбросил canFetchCache. Учитываем время только у внешнего вызова.
		if (depth == 0) {
			spentNanos += System.nanoTime() - startNanos[0];
			final long budget = OptConfig.get().modelBuildBudgetMillis * 1_000_000L;
			return budget > 0 && spentNanos < budget;
		}
		return false;
	}
}

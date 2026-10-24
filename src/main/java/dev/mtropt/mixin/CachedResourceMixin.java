package dev.mtropt.mixin;

import dev.mtropt.CachedResourceAccess;
import dev.mtropt.GpuRelease;
import dev.mtropt.HookStatus;
import dev.mtropt.OptConfig;
import dev.mtropt.ResourceRegistry;
import dev.mtropt.Safety;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Кэш моделей MTR 4.0.5 (org.mtr.mod.resource.CachedResource). Что здесь исправлено:
 *
 * 1. Утечка реестра. Конструктор добавляет каждый ресурс в статический список, откуда его никогда не удаляют.
 *    Цепочка кэша вагона (3 уровня + 4 карты моделей) создаёт новые дочерние ресурсы при каждой пересборке родителя,
 *    и все старые вместе с сырыми данными моделей остаются в списке навсегда. Теперь ресурсы регистрируются слабыми
 *    ссылками (ResourceRegistry), а статический список очищается каждый тик.
 * 2. Утечка видеопамяти. Когда срок хранения данных истёк (или данные заменяются новой сборкой), буферы старой
 *    модели теперь освобождаются (GpuRelease), с задержкой в 1-2 тика.
 * 3. Лаг при въезде поезда в поле зрения. MTR собирает не больше одного ресурса за тик, а на полную сборку вагона
 *    нужно 7+ последовательных сборок (3 уровня цепочки + 4 модели), поэтому вагоны появляются по одному и с рывками.
 *    Теперь после сборки разрешается ещё одна, пока не потрачен бюджет времени modelBuildBudgetMillis.
 * 4. Срок хранения цепочки кэша вагона и его GPU-моделей продлевается минимум до vehicleModelCacheMillis,
 *    поэтому поезд, который уже «зарегистрирован», не пересобирается при каждом возвращении в поле зрения.
 *    Остальные ресурсы (сырые модели, разбираемые из json) живут как в MTR.
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.resource.CachedResource", remap = false)
public abstract class CachedResourceMixin implements CachedResourceAccess {

	@Shadow(remap = false)
	private Object data;

	@Shadow(remap = false)
	private long expiry;

	@Shadow(remap = false)
	private static boolean canFetchCache;

	@Unique
	private int mtropt$kind;

	@Override
	public Object mtropt$getData() {
		return data;
	}

	@Override
	public void mtropt$setData(Object newData) {
		data = newData;
	}

	@Override
	public long mtropt$getExpiry() {
		return expiry;
	}

	@Override
	public void mtropt$setExpiry(long newExpiry) {
		expiry = newExpiry;
	}

	@Override
	public int mtropt$getKind() {
		return mtropt$kind;
	}

	@Override
	public void mtropt$setKind(int kind) {
		mtropt$kind = kind;
	}

	/** Регистрация слабой ссылкой вместо вечного хранения в статическом списке. */
	@Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
	private void mtropt$register(CallbackInfo ci) {
		if (OptConfig.get().fixResourceLeaks) {
			try {
				ResourceRegistry.register(this);
			} catch (Throwable e) {
				Safety.trip("CachedResource.<init>", e);
			}
		}
	}

	/** Замена CachedResource.tick(): тот же смысл, но с реестром без утечек и освобождением видеопамяти. */
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private static void mtropt$tick(CallbackInfo ci) {
		if (!OptConfig.get().fixResourceLeaks) {
			return;
		}
		HookStatus.mark("CachedResource.tick (registry without leaks, GPU release)");
		try {
			canFetchCache = true;
			ResourceRegistry.tick();
			ci.cancel();
		} catch (Throwable e) {
			Safety.trip("CachedResource.tick", e); // оригинальный tick() MTR выполнится как обычно
		}
	}

	@Inject(method = "getData", at = @At("HEAD"), remap = false, require = 0)
	private void mtropt$beforeGetData(boolean force, CallbackInfoReturnable<Object> cir) {
		final OptConfig config = OptConfig.get();
		if (!config.fixResourceLeaks) {
			return;
		}
		try {
			final boolean fetchNow = force || canFetchCache;
			if (fetchNow && data != null && System.currentTimeMillis() > expiry) {
				// MTR сейчас заменит данные новой сборкой: старые отправляем на освобождение.
				ResourceRegistry.queueRelease(data);
				data = null;
				mtropt$kind = 0;
			}
			if (config.modelBuildBudgetMillis > 0) {
				ResourceRegistry.enter(canFetchCache);
			}
		} catch (Throwable e) {
			Safety.trip("CachedResource.getData (head)", e);
		}
	}

	@Inject(method = "getData", at = @At("RETURN"), remap = false, require = 0)
	private void mtropt$afterGetData(boolean force, CallbackInfoReturnable<Object> cir) {
		final OptConfig config = OptConfig.get();
		HookStatus.mark("CachedResource.getData (model cache)");
		try {
			if (config.modelBuildBudgetMillis > 0 && ResourceRegistry.exit(canFetchCache)) {
				canFetchCache = true;
			}
			final int minLifespan = config.vehicleModelCacheMillis;
			if (minLifespan > 0 && data != null) {
				if (mtropt$kind == 0) {
					mtropt$kind = GpuRelease.classify(data);
				}
				if (mtropt$kind == 1) {
					final long minExpiry = System.currentTimeMillis() + minLifespan;
					if (expiry < minExpiry) {
						expiry = minExpiry;
					}
				}
			}
		} catch (Throwable e) {
			Safety.trip("CachedResource.getData (return)", e);
		}
	}
}

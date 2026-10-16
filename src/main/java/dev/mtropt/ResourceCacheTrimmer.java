package dev.mtropt;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;

/**
 * Ограничивает размер текстового кэша ресурсов MTR (CustomResourceLoader.RESOURCE_CACHE):
 * там навсегда (до перезагрузки ресурсов) хранится текст всех прочитанных json-файлов моделей и свойств.
 * Раз в ~10 секунд лишние записи удаляются; при следующем обращении файл просто читается заново.
 * Работает в главном потоке клиента (тик). По умолчанию выключено (resourceTextCacheMaxEntries = 0).
 */
public final class ResourceCacheTrimmer {

	private static Field cacheField;
	private static boolean failed;
	private static int ticks;

	private ResourceCacheTrimmer() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
	}

	private static void tick() {
		final int max = OptConfig.get().resourceTextCacheMaxEntries;
		if (max <= 0 || failed || ++ticks < 200) {
			return;
		}
		ticks = 0;
		try {
			if (cacheField == null) {
				final Field field = Class.forName("org.mtr.mod.client.CustomResourceLoader").getDeclaredField("RESOURCE_CACHE");
				field.setAccessible(true);
				cacheField = field;
			}
			final Object cache = cacheField.get(null);
			if (cache instanceof Map<?, ?> map) {
				int excess = map.size() - max;
				if (excess > 0) {
					final Iterator<?> iterator = map.keySet().iterator();
					while (excess-- > 0 && iterator.hasNext()) {
						iterator.next();
						iterator.remove();
					}
					HookStatus.mark("CustomResourceLoader.RESOURCE_CACHE (text cache trim)");
				}
			}
		} catch (Throwable e) {
			failed = true;
			MtrOptClient.LOGGER.warn("Не удалось получить доступ к кэшу ресурсов MTR, обрезка отключена", e);
		}
	}
}

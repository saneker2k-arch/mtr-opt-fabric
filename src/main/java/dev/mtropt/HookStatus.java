package dev.mtropt;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Пишет в лог один раз при первом срабатывании каждого хука: так видно, какие миксины реально применились. */
public final class HookStatus {

	private static final Set<String> FIRED = ConcurrentHashMap.newKeySet();

	private HookStatus() {
	}

	public static void mark(String hook) {
		if (FIRED.add(hook)) {
			MtrOptClient.LOGGER.info("Хук активен: {}", hook);
		}
	}
}

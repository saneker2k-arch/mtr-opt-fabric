package dev.mtropt;

/**
 * Аварийный выключатель. Хуки аддона работают внутри рендера и тика MTR, поэтому любое исключение из нашего кода
 * не должно ронять игру: оно логируется один раз, а все функции, которые лезут во внутренности MTR, отключаются до
 * перезапуска. MTR дальше работает по своей оригинальной логике.
 */
public final class Safety {

	private static boolean tripped;

	private Safety() {
	}

	public static boolean isTripped() {
		return tripped;
	}

	public static void trip(String where, Throwable e) {
		if (tripped) {
			return;
		}
		tripped = true;
		final OptConfig config = OptConfig.get();
		config.fixResourceLeaks = false;
		config.releaseGpuBuffers = false;
		config.prewarmVehicles = false;
		config.modelBuildBudgetMillis = 0;
		MtrOptClient.LOGGER.warn("Ошибка в хуке {}: исправления кэша MTR отключены до перезапуска, MTR работает по своей логике", where, e);
	}
}

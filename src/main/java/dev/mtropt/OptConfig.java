package dev.mtropt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Простой JSON-конфиг: config/mtr_opt.json. Создаётся при первом запуске.
 * Значение 0 (или меньше) у числовых параметров означает «не менять поведение MTR».
 */
public final class OptConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static OptConfig instance;

	/** Максимальная дальность отрисовки рельсов в блоках. 0 = как в MTR (дальность прорисовки игры). */
	public int maxRailRenderDistanceBlocks = 128;

	/** Максимальный радиус occlusion culling в чанках. В MTR 4 жёстко 32. 0 = не менять. */
	public int maxOcclusionRadiusChunks = 12;

	/** Пауза рабочего потока culling в миллисекундах. В MTR 4 жёстко 10. 0 = не менять. */
	public int workerThreadSleepMillis = 0;

	/**
	 * Радиус проверки «висящих» узлов рельсов, когда в руке предмет для рельсов/сигналов.
	 * В MTR 4 жёстко 16, то есть 33x33x33 = 35937 вызовов getBlockState каждый кадр. 0 = не менять.
	 */
	public int nodeCheckRadiusBlocks = 6;

	/** Время жизни динамических текстур (названия станций, знаки, PIDS) без запросов, мс. В MTR 4 жёстко 10000. 0 = не менять. */
	public int dynamicTextureLifetimeMillis = 5000;

	/**
	 * Минимальное время хранения собранных моделей поездов/объектов/рельсов в кэше, мс.
	 * В MTR 4 модель, к которой не обращались 60000 мс, сбрасывается и потом собирается заново. 0 = не менять.
	 */
	public int vehicleModelCacheMillis = 0;

	/** Быстрая замена String.format (только %s) в местах, где MTR 4 собирает строки каждый кадр: меньше мусора, меньше микрофризов. */
	public boolean fastStringFormat = true;

	/**
	 * Максимум записей в текстовом кэше ресурсов MTR (json моделей и свойств). Лишние удаляются каждые ~10 секунд.
	 * 0 = не трогать (по умолчанию). Для слабых ПК можно поставить 256-512.
	 */
	public int resourceTextCacheMaxEntries = 256;

	/** Период запроса прибытия поездов у сервера для табло, мс. В MTR 4 жёстко 3000. 0 = не менять. */
	public int arrivalsRefreshMillis = 0;

	private OptConfig() {
	}

	public static synchronized OptConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static OptConfig load() {
		final Path path = FabricLoader.getInstance().getConfigDir().resolve("mtr_opt.json");
		OptConfig config = null;
		try {
			if (Files.exists(path)) {
				config = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), OptConfig.class);
			}
		} catch (Exception e) {
			MtrOptClient.LOGGER.warn("Не удалось прочитать {}, используются значения по умолчанию", path, e);
		}
		if (config == null) {
			config = new OptConfig();
		}
		try {
			Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MtrOptClient.LOGGER.warn("Не удалось записать {}", path, e);
		}
		return config;
	}
}

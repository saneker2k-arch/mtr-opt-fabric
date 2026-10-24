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
	public int maxOcclusionRadiusChunks = 0;

	/** Пауза рабочего потока culling в миллисекундах. В MTR 4 жёстко 10. 0 = не менять. */
	public int workerThreadSleepMillis = 0;

	/**
	 * Радиус проверки «висящих» узлов рельсов, когда в руке предмет для рельсов/сигналов.
	 * В MTR 4 жёстко 16, то есть 33x33x33 = 35937 вызовов getBlockState каждый кадр. 0 = не менять.
	 */
	public int nodeCheckRadiusBlocks = 6;

	/** Время жизни динамических текстур (названия станций, знаки, PIDS) без запросов, мс. В MTR 4 жёстко 10000. 0 = не менять. */
	public int dynamicTextureLifetimeMillis = 0;

	/**
	 * Минимальное время хранения цепочки кэша вагона и его собранных GPU-моделей, мс. В MTR 4 вагон, к которому
	 * не обращались 60000 мс, сбрасывается и потом заново собирается (лаг при въезде поезда в поле зрения).
	 * Память при этом не растёт бесконтрольно: по истечении срока буферы теперь реально освобождаются
	 * (см. releaseGpuBuffers). 0 = как в MTR.
	 */
	public int vehicleModelCacheMillis = 120000;

	/** Быстрая замена String.format (только %s) в местах, где MTR 4 собирает строки каждый кадр: меньше мусора, меньше микрофризов. */
	public boolean fastStringFormat = true;

	/**
	 * Максимум записей в текстовом кэше ресурсов MTR (json моделей и свойств). Лишние удаляются каждые ~10 секунд.
	 * 0 = не трогать (по умолчанию). Для слабых ПК можно поставить 256-512.
	 */
	public int resourceTextCacheMaxEntries = 0;

	/**
	 * Исправление утечки реестра CachedResource: ресурсы регистрируются слабыми ссылками, статический список MTR
	 * очищается каждый тик, перезагрузка ресурсов освобождает старые модели. false = как в MTR.
	 */
	public boolean fixResourceLeaks = true;

	/**
	 * Освобождать видеопамять (VAO, VBO, IBO) моделей, когда MTR сбрасывает их из кэша. В MTR 4.0.5 буферы вершин
	 * и индексов не удаляются никогда. Работает только вместе с fixResourceLeaks.
	 */
	public boolean releaseGpuBuffers = true;

	/**
	 * Бюджет времени на сборку моделей за тик, мс. MTR собирает не больше одного ресурса за тик, а на вагон нужно
	 * 7+ сборок подряд, поэтому поезд при въезде в поле зрения «собирается» по частям. Пока потрачено меньше бюджета,
	 * разрешается ещё одна сборка. 0 = как в MTR (один ресурс за тик). Работает только вместе с fixResourceLeaks.
	 */
	public int modelBuildBudgetMillis = 6;

	/**
	 * Собирать модели поездов заранее, как только клиент получил поезд, а не когда он первый раз попал в кадр.
	 * Убирает основной лаг при появлении поезда в поле зрения. Пока поезд есть в списке клиента, его модель
	 * не вылетает из кэша. false = как в MTR.
	 */
	public boolean prewarmVehicles = true;

	/** Как часто (в тиках, 20 тиков = 1 секунда) просматривать список поездов для prewarmVehicles. */
	public int prewarmIntervalTicks = 10;

	/**
	 * Раз в сколько секунд писать в лог строку статистики аддона (число кэшированных ресурсов, освобождённых моделей
	 * и буферов, время сборки моделей, занятая память Java). 0 = не писать. Время сборки считается при modelBuildBudgetMillis больше 0.
	 */
	public int statsLogIntervalSeconds = 0;

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

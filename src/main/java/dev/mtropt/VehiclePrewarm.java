package dev.mtropt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * Заблаговременная сборка моделей поездов, которые клиент уже «знает», но которые ещё не в кадре.
 *
 * Модель вагона в MTR 4 собирается лениво: только когда вагон впервые попал в отрисовку (RenderVehicles.lambda$render$14
 * вызывает VehicleResource.getCachedVehicleResource(индекс вагона, число вагонов, false)). Поэтому лаг приходится именно на
 * момент появления поезда в кадре. Клиент получает список поездов (MinecraftClientData.vehicles) заранее, и здесь для каждого
 * вагона из этого списка делается тот же вызов, что делает рендер. Сборка идёт через обычный механизм кэша: не больше одного
 * ресурса за тик плюс бюджет modelBuildBudgetMillis, то есть растянута по тикам. Повторный вызов для уже собранной модели
 * дёшев и продлевает срок её хранения, пока поезд есть в списке клиента, поэтому она не вылетает из кэша.
 *
 * Всё через рефлексию с кэшем (MTR-типы при компиляции аддона недоступны). Любая ошибка отключает функцию до перезапуска.
 */
public final class VehiclePrewarm {

	private static boolean failed;
	private static boolean initialised;
	private static int tickCounter;

	private static Method getInstance;
	private static Field vehiclesField;
	private static Field extraDataField;
	private static Field carsField;
	private static Method getTransportMode;
	private static Method getVehicleId;
	private static Method getVehicleById;
	private static Method pairLeft;
	private static Method cachedResourceMethod;

	private VehiclePrewarm() {
	}

	/** Вызывается в конце тика кэша (ResourceRegistry.tick), когда canFetchCache уже сброшен в true. */
	public static void run() {
		final OptConfig config = OptConfig.get();
		if (!config.prewarmVehicles || failed) {
			return;
		}
		if (++tickCounter < Math.max(1, config.prewarmIntervalTicks)) {
			return;
		}
		tickCounter = 0;
		try {
			if (!initialised) {
				init();
			}
			final Object clientData = getInstance.invoke(null);
			if (clientData == null) {
				return;
			}
			final Object[] vehicles;
			try {
				vehicles = ((Collection<?>) vehiclesField.get(clientData)).toArray();
			} catch (RuntimeException e) {
				return; // список менялся из другого потока, попробуем в следующий раз
			}
			final HashSet<String> seen = new HashSet<>();
			for (final Object vehicle : vehicles) {
				final Object extraData = extraDataField.get(vehicle);
				if (extraData == null) {
					continue;
				}
				final List<?> cars = (List<?>) carsField.get(extraData);
				final int size = cars.size();
				final Object mode = getTransportMode.invoke(vehicle);
				for (int i = 0; i < size; i++) {
					final String vehicleId = (String) getVehicleId.invoke(cars.get(i));
					if (vehicleId == null || !seen.add(vehicleId + '/' + i + '/' + size)) {
						continue;
					}
					final Object[] resource = new Object[1];
					final int carIndex = i;
					final Consumer<Object> consumer = pair -> {
						try {
							resource[0] = pairLeft.invoke(pair);
						} catch (ReflectiveOperationException e) {
							throw new IllegalStateException(e);
						}
					};
					getVehicleById.invoke(null, mode, vehicleId, consumer);
					if (resource[0] != null) {
						cachedResourceMethod.invoke(resource[0], carIndex, size, false);
					}
				}
			}
		} catch (Throwable e) {
			failed = true;
			MtrOptClient.LOGGER.warn("Заблаговременная сборка моделей поездов отключена из-за ошибки", e);
		}
	}

	private static void init() throws ReflectiveOperationException {
		final ClassLoader loader = VehiclePrewarm.class.getClassLoader();
		final Class<?> clientData = Class.forName("org.mtr.mod.client.MinecraftClientData", false, loader);
		getInstance = clientData.getMethod("getInstance");
		vehiclesField = clientData.getField("vehicles");

		final Class<?> vehicleExtension = Class.forName("org.mtr.mod.data.VehicleExtension", false, loader);
		extraDataField = vehicleExtension.getField("vehicleExtraData");
		getTransportMode = vehicleExtension.getMethod("getTransportMode");

		final Class<?> extraData = Class.forName("org.mtr.core.data.VehicleExtraData", false, loader);
		carsField = extraData.getField("immutableVehicleCars");

		final Class<?> car = Class.forName("org.mtr.core.data.VehicleCar", false, loader);
		getVehicleId = car.getMethod("getVehicleId");

		final Class<?> resourceLoader = Class.forName("org.mtr.mod.client.CustomResourceLoader", false, loader);
		final Class<?> mode = Class.forName("org.mtr.core.data.TransportMode", false, loader);
		getVehicleById = resourceLoader.getMethod("getVehicleById", mode, String.class, Consumer.class);

		pairLeft = Class.forName("org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectBooleanImmutablePair", false, loader).getMethod("left");

		final Class<?> resource = Class.forName("org.mtr.mod.resource.VehicleResource", false, loader);
		cachedResourceMethod = resource.getMethod("getCachedVehicleResource", int.class, int.class, boolean.class);
		initialised = true;
	}
}

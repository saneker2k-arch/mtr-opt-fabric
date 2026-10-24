package dev.mtropt;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Освобождение видеопамяти моделей MTR 4.0.5.
 *
 * Что не так в MTR: OptimizedModel.close() нигде не вызывается, а VertexArray.close() удаляет только VAO,
 * но не буфер вершин (VBO) и не буфер индексов (IBO): ссылка на VBO после загрузки вообще нигде не хранится.
 * Каждая пересборка модели после истечения срока кэша оставляет в видеопамяти (и в памяти драйвера) мёртвые буферы.
 *
 * Здесь:
 *  - {@link #release(Object)} обходит данные ресурса (карты моделей, обёртки, цепочку кэша вагона) и закрывает модели;
 *  - {@link #freeBuffersOf(int)} вызывается из VertexArrayMixin и удаляет буферы, привязанные к VAO.
 *    Буферы определяются запросом к самому OpenGL, поэтому типы MTR для компиляции не нужны.
 */
public final class GpuRelease {

	private static final int MAX_DEPTH = 8;

	private static Class<?> wrapperClass;
	private static Class<?> holderClass;
	private static Field wrapperModelField;
	private static Method modelCloseMethod;
	private static Field[] holderFields;
	private static boolean failed;
	private static int releasedModels;

	private GpuRelease() {
	}

	public static void release(Object data) {
		if (data == null || failed || !OptConfig.get().releaseGpuBuffers) {
			// Даже без освобождения GPU надо снять ссылки у дочерних ресурсов цепочки: это делает cascade ниже.
			if (data != null && !failed) {
				cascadeOnly(data, 0);
			}
			return;
		}
		try {
			release(data, 0);
		} catch (Throwable e) {
			failed = true;
			MtrOptClient.LOGGER.warn("Ошибка при освобождении моделей MTR, освобождение видеопамяти отключено", e);
		}
	}

	private static void cascadeOnly(Object data, int depth) {
		if (depth > MAX_DEPTH) {
			return;
		}
		if (data instanceof CachedResourceAccess child) {
			final Object inner = child.mtropt$getData();
			child.mtropt$setData(null);
			child.mtropt$setExpiry(0);
			child.mtropt$setKind(0);
			cascadeOnly(inner, depth + 1);
		}
	}

	private static void release(Object data, int depth) throws ReflectiveOperationException {
		if (data == null || depth > MAX_DEPTH) {
			return;
		}
		if (data instanceof CachedResourceAccess child) {
			// Дочерний ресурс цепочки кэша: родитель истёк, значит дочерний больше никому не нужен.
			final Object inner = child.mtropt$getData();
			child.mtropt$setData(null);
			child.mtropt$setExpiry(0);
			child.mtropt$setKind(0);
			release(inner, depth + 1);
			return;
		}
		resolveClasses(data);
		final Class<?> type = data.getClass();
		if (type == wrapperClass) {
			closeWrapper(data);
		} else if (type == holderClass) {
			for (final Field field : holderFields) {
				release(field.get(data), depth + 1);
			}
		} else if (data instanceof Map<?, ?> map) {
			for (final Object value : new ArrayList<>(map.values())) {
				release(value, depth + 1);
			}
		} else if (type.getName().endsWith("ObjectObjectImmutablePair")) {
			// Объекты и рельсы: пара (модель, DynamicVehicleModel). Освобождаем только саму модель.
			final Object left = type.getMethod("left").invoke(data);
			if (left != null && left.getClass() == wrapperClass) {
				closeWrapper(left);
			}
		}
	}

	private static void resolveClasses(Object sample) throws ReflectiveOperationException {
		if (wrapperClass != null && holderClass != null) {
			return;
		}
		final ClassLoader loader = sample instanceof CachedResourceAccess ? sample.getClass().getClassLoader() : GpuRelease.class.getClassLoader();
		if (wrapperClass == null) {
			final Class<?> wrapper = Class.forName("org.mtr.mod.resource.OptimizedModelWrapper", false, loader);
			final Field field = wrapper.getDeclaredField("optimizedModel");
			field.setAccessible(true);
			wrapperModelField = field;
			final Class<?> modelType = field.getType();
			modelCloseMethod = modelType.getMethod("close");
			wrapperClass = wrapper;
		}
		if (holderClass == null) {
			final Class<?> holder = Class.forName("org.mtr.mod.resource.VehicleResource$VehicleResourceCacheHolder", false, loader);
			final List<Field> fields = new ArrayList<>();
			for (final Field field : holder.getDeclaredFields()) {
				if (field.getType().getName().equals("org.mtr.mod.resource.CachedResource")) {
					field.setAccessible(true);
					fields.add(field);
				}
			}
			holderFields = fields.toArray(new Field[0]);
			holderClass = holder;
		}
	}

	private static void closeWrapper(Object wrapper) throws ReflectiveOperationException {
		final Object model = wrapperModelField.get(wrapper);
		if (model != null) {
			modelCloseMethod.invoke(model);
			releasedModels++;
		}
	}

	/**
	 * Определяет, относятся ли данные ресурса к цепочке кэша вагона или к собранной GPU-модели вагона
	 * (их срок хранения можно продлевать). 1 = да, 2 = нет.
	 */
	public static int classify(Object data) {
		if (data instanceof CachedResourceAccess) {
			return 1;
		}
		if (failed) {
			return 2;
		}
		try {
			resolveClasses(data);
			final Class<?> type = data.getClass();
			if (type == holderClass) {
				return 1;
			}
			if (data instanceof Map<?, ?> map && !map.isEmpty()) {
				final Object value = map.values().iterator().next();
				return value != null && value.getClass() == wrapperClass ? 1 : 2;
			}
		} catch (Throwable e) {
			failed = true;
			MtrOptClient.LOGGER.warn("Не удалось определить тип данных кэша MTR, продление срока хранения отключено", e);
		}
		return 2;
	}

	public static int releasedModels() {
		return releasedModels;
	}

	// ---------------------------------------------------------------- GL

	private static int totalFreedBuffers;

	/**
	 * Удаляет буферы вершин и индексов, привязанные к VAO. Вызывать только в потоке рендера, до glDeleteVertexArrays.
	 * VAO у MTR привязан ровно к одному VBO (VertexAttributeMapping.setupAttributesToVao) и одному IBO.
	 */
	public static void freeBuffersOf(int vao) {
		if (vao == 0) {
			return;
		}
		final int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
		final int[] ids = new int[GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS) + 1];
		int count = 0;
		GL30.glBindVertexArray(vao);
		try {
			final int attributes = ids.length - 1;
			for (int i = 0; i < attributes; i++) {
				final int buffer = GL20.glGetVertexAttribi(i, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
				if (buffer != 0 && !contains(ids, count, buffer)) {
					ids[count++] = buffer;
				}
			}
			final int indexBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
			if (indexBuffer != 0 && !contains(ids, count, indexBuffer)) {
				ids[count++] = indexBuffer;
			}
		} finally {
			GL30.glBindVertexArray(previousVao);
		}
		for (int i = 0; i < count; i++) {
			GL15.glDeleteBuffers(ids[i]);
		}
		totalFreedBuffers += count;
	}

	public static int totalFreedBuffers() {
		return totalFreedBuffers;
	}

	private static boolean contains(int[] array, int length, int value) {
		for (int i = 0; i < length; i++) {
			if (array[i] == value) {
				return true;
			}
		}
		return false;
	}
}

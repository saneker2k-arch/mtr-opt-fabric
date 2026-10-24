import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;

public class LeakProof {
	public static void main(String[] a) throws Exception {
		Class<?> c = Class.forName("org.mtr.mod.resource.CachedResource");
		Constructor<?> ctor = c.getDeclaredConstructors()[0]; ctor.setAccessible(true);
		Field f = c.getDeclaredField("CACHED_RESOURCES"); f.setAccessible(true);
		List<?> list = (List<?>) f.get(null);
		Method tick = c.getMethod("tick");
		System.out.println("ctor: " + ctor);
		System.out.println("registry size at start: " + list.size());
		// Имитация цепочки вагона: родитель при каждой пересборке создаёт новых «детей».
		long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		for (int cycle = 0; cycle < 50; cycle++) {
			for (int i = 0; i < 2000; i++) {
				final byte[] raw = new byte[2000]; // сырые данные модели, захваченные загрузчиком
				Supplier<Object> s = () -> raw;
				Object r = ctor.newInstance(s, 60000L);
				// ресурс сразу выпадает из рук владельца, как старые дети при пересборке родителя
			}
			tick.invoke(null);
		}
		System.gc(); Thread.sleep(300); System.gc();
		long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		System.out.println("created 100000 resources, all unreachable by their owners, MTR tick() called 50 times");
		System.out.println("registry size after: " + list.size());
		System.out.println("heap growth after GC: " + (after - before) / (1024 * 1024) + " MB (captured raw data stays reachable)");
	}
}

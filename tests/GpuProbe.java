import java.lang.reflect.*;
public class GpuProbe {
	public static void main(String[] a) throws Exception {
		Method m = dev.mtropt.GpuRelease.class.getDeclaredMethod("resolveClasses", Object.class);
		m.setAccessible(true);
		try { m.invoke(null, "sample"); 
			for (String n : new String[]{"wrapperClass","holderClass","wrapperModelField","modelCloseMethod"}) { Field f = dev.mtropt.GpuRelease.class.getDeclaredField(n); f.setAccessible(true); System.out.println("  "+n+" = "+f.get(null)); }
			Field hf = dev.mtropt.GpuRelease.class.getDeclaredField("holderFields"); hf.setAccessible(true); System.out.println("  holderFields = "+((Field[])hf.get(null)).length);
			System.out.println("GpuRelease lookups OK"); }
		catch (InvocationTargetException e) { System.out.println("FAILED: "+e.getCause()); }
		// CACHED_RESOURCES static list
		Field f = Class.forName("org.mtr.mod.resource.CachedResource").getDeclaredField("CACHED_RESOURCES"); f.setAccessible(true);
		Object list = f.get(null);
		System.out.println("CACHED_RESOURCES = "+list.getClass().getName()+" instanceof java.util.List: "+(list instanceof java.util.List));
	}
}

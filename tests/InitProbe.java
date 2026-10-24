import java.lang.reflect.*;
public class InitProbe {
	public static void main(String[] a) throws Exception {
		Method m = dev.mtropt.VehiclePrewarm.class.getDeclaredMethod("init");
		m.setAccessible(true);
		try { m.invoke(null); System.out.println("init OK: all reflective lookups resolved against real MTR 4.0.5 classes"); }
		catch (InvocationTargetException e) { Throwable c=e.getCause(); System.out.println("init FAILED: "+c); StackTraceElement[] st=c.getStackTrace(); if(st.length>0) System.out.println("  at "+st[0]); }
	}
}

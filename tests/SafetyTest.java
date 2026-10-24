import dev.mtropt.*;
import java.lang.reflect.Field;
public class SafetyTest {
	static int fails=0; static void check(boolean ok,String w){System.out.println((ok?"PASS ":"FAIL ")+w); if(!ok)fails++;}
	public static void main(String[] a) throws Exception {
		OptConfig c=OptConfig.get();
		check(c.fixResourceLeaks&&c.releaseGpuBuffers&&c.prewarmVehicles&&c.modelBuildBudgetMillis>0,"defaults: all fixes enabled");
		check(c.vehicleModelCacheMillis==120000 && c.dynamicTextureLifetimeMillis==0 && c.resourceTextCacheMaxEntries==0 && c.maxOcclusionRadiusChunks==0,"defaults: risky options off");
		Safety.trip("test", new RuntimeException("boom"));
		check(Safety.isTripped()&&!c.fixResourceLeaks&&!c.releaseGpuBuffers&&!c.prewarmVehicles&&c.modelBuildBudgetMillis==0,"trip() disables all fixes");
		Safety.trip("test2", new RuntimeException("again")); // second call must be silent
		// stats path: enable and run 20 ticks
		Field f=OptConfig.class.getDeclaredField("statsLogIntervalSeconds"); f.setAccessible(true); f.setInt(c,1);
		for(int i=0;i<21;i++) ResourceRegistry.tick();
		System.out.println(fails==0?"ALL OK":"FAILURES "+fails); System.exit(fails==0?0:1);
	}
}

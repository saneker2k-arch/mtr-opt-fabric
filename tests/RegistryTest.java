import dev.mtropt.*;
import java.lang.reflect.*;
import java.util.*;

public class RegistryTest {
	static class Fake implements CachedResourceAccess {
		Object data; long expiry; int kind;
		public Object mtropt$getData(){return data;} public void mtropt$setData(Object d){data=d;}
		public long mtropt$getExpiry(){return expiry;} public void mtropt$setExpiry(long e){expiry=e;}
		public int mtropt$getKind(){return kind;} public void mtropt$setKind(int k){kind=k;}
	}
	static int fails=0;
	static void check(boolean ok,String what){ System.out.println((ok?"PASS ":"FAIL ")+what); if(!ok)fails++; }

	@SuppressWarnings("unchecked")
	static List<Object> releaseList(String name) throws Exception { Field f=ResourceRegistry.class.getDeclaredField(name); f.setAccessible(true); return (List<Object>)f.get(null); }

	public static void main(String[] a) throws Exception {
		long now=System.currentTimeMillis();
		// 1. expiry nulls data and queues it
		Fake live=new Fake(); live.data="A"; live.expiry=now+100000;
		Fake dead=new Fake(); dead.data="B"; dead.expiry=now-1;
		Fake empty=new Fake(); empty.expiry=now-1;
		ResourceRegistry.register(live); ResourceRegistry.register(dead); ResourceRegistry.register(empty);
		ResourceRegistry.tick();
		check(live.data!=null,"live resource keeps data");
		check(dead.data==null,"expired resource data nulled");
		check(releaseList("releaseCurrent").contains("B"),"expired data queued for release");
		check(ResourceRegistry.liveCount()==3,"3 live entries");

		// 2. release delay: not in 'previous' yet after first tick, moved after second
		ResourceRegistry.tick();
		check(releaseList("releasePrevious").contains("B") || releaseList("releaseCurrent").isEmpty(),"release queue advances per tick");

		// 3. weak registry drops garbage-collected resources
		for(int i=0;i<2000;i++){ Fake f=new Fake(); f.data="x"; f.expiry=now+100000; ResourceRegistry.register(f);} 
		ResourceRegistry.tick();
		int before=ResourceRegistry.liveCount();
		for(int i=0;i<5;i++){ System.gc(); Thread.sleep(50);} 
		ResourceRegistry.tick();
		int after=ResourceRegistry.liveCount();
		check(before>=2000 && after<=10,"GC'd resources removed from registry ("+before+" -> "+after+")");

		// 4. disposeAll only affects resources created before the request
		Fake old=new Fake(); old.data="OLD"; old.expiry=now+100000; ResourceRegistry.register(old);
		ResourceRegistry.requestDisposeAll();
		Fake fresh=new Fake(); fresh.data="NEW"; fresh.expiry=now+100000; ResourceRegistry.register(fresh);
		ResourceRegistry.tick();
		check(old.data==null,"disposeAll nulls older resource");
		check(fresh.data!=null,"disposeAll keeps resources created after the request");

		// 5. budget: no-op off render thread; counts on render thread (tick sets render thread = this thread)
		OptConfigAccess.setBudget(6);
		ResourceRegistry.tick();
		ResourceRegistry.enter(true);               // outer call starts, fetchable
		boolean again=ResourceRegistry.exit(false); // a build happened (flag flipped to false), took ~0ms
		check(again,"budget allows another build after a fast one");
		ResourceRegistry.enter(true);
		Thread.sleep(8);
		boolean again2=ResourceRegistry.exit(false);
		check(!again2,"budget exhausted after slow build");
		ResourceRegistry.enter(false);
		check(!ResourceRegistry.exit(false),"no build -> no extra permission");
		// nested: inner build must not re-enable, outer decides
		ResourceRegistry.tick();
		ResourceRegistry.enter(true); ResourceRegistry.enter(true);
		boolean inner=ResourceRegistry.exit(false);
		boolean outer=ResourceRegistry.exit(false);
		check(!inner && outer,"nested: only outermost call re-enables");
		// other thread must not touch the stack
		final boolean[] res=new boolean[1];
		Thread t=new Thread(() -> { ResourceRegistry.enter(true); res[0]=ResourceRegistry.exit(false); }); t.start(); t.join();
		check(!res[0],"calls from other threads are ignored");

		System.out.println(fails==0?"ALL OK":"FAILURES: "+fails);
		System.exit(fails==0?0:1);
	}
}

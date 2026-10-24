import dev.mtropt.OptConfig;
public class OptConfigAccess { public static void setBudget(int ms) throws Exception { var f=OptConfig.class.getDeclaredField("modelBuildBudgetMillis"); f.setAccessible(true); f.setInt(OptConfig.get(), ms);} }

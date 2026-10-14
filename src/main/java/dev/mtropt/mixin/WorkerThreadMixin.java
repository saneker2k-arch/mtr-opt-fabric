package dev.mtropt.mixin;

import dev.mtropt.HookStatus;
import dev.mtropt.OptConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Настройка рабочего потока occlusion culling в MTR 4 (org.mtr.mod.render.WorkerThread):
 *  - радиус culling (константа MAX_OCCLUSION_CHUNK_DISTANCE = 32 чанка, вшита в updateInstance);
 *  - пауза между итерациями (Thread.sleep(10) в runTick).
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.render.WorkerThread", remap = false)
public abstract class WorkerThreadMixin {

	@ModifyConstant(
			method = "updateInstance",
			constant = @Constant(intValue = 32),
			remap = false,
			require = 0
	)
	private int mtropt$occlusionRadiusChunks(int original) {
		HookStatus.mark("WorkerThread.updateInstance (occlusion radius)");
		final int cap = OptConfig.get().maxOcclusionRadiusChunks;
		return cap > 0 ? Math.min(original, cap) : original;
	}

	@ModifyConstant(
			method = "runTick",
			constant = @Constant(longValue = 10L),
			remap = false,
			require = 0
	)
	private long mtropt$workerSleepMillis(long original) {
		HookStatus.mark("WorkerThread.runTick (sleep)");
		final int sleep = OptConfig.get().workerThreadSleepMillis;
		return sleep > 0 ? sleep : original;
	}
}

package dev.mtropt.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mtropt.GpuRelease;
import dev.mtropt.HookStatus;
import dev.mtropt.OptConfig;
import dev.mtropt.Safety;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * org.mtr.mapping.render.object.VertexArray.close() в MTR удаляет только VAO, а буферы вершин и индексов остаются
 * в видеопамяти навсегда. Здесь перед удалением VAO удаляются и привязанные к нему буферы (GpuRelease.freeBuffersOf).
 * Повторный close() безопасен: после первого id становится 0 (так делает и сам MTR), поэтому дальше вызов отменяется.
 * Если close() вызван не из потока рендера, MTR сам ставит его в очередь рендера и вызывает повторно уже там.
 */
@Pseudo
@Mixin(targets = "org.mtr.mapping.render.object.VertexArray", remap = false)
public abstract class VertexArrayMixin {

	@Shadow(remap = false)
	private int id;

	@Inject(method = "close", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void mtropt$closeBuffers(CallbackInfo ci) {
		if (!OptConfig.get().releaseGpuBuffers || !RenderSystem.isOnRenderThread()) {
			return;
		}
		if (id == 0) {
			ci.cancel();
			return;
		}
		HookStatus.mark("VertexArray.close (VBO/IBO release)");
		try {
			GpuRelease.freeBuffersOf(id);
		} catch (Throwable e) {
			Safety.trip("VertexArray.close", e);
		}
	}
}

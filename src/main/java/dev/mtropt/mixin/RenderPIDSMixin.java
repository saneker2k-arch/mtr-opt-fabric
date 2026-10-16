package dev.mtropt.mixin;

import dev.mtropt.FastFormat;
import dev.mtropt.HookStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Табло PIDS собирает строки назначения через String.format каждый кадр.
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.render.RenderPIDS", remap = false)
public abstract class RenderPIDSMixin {

	@Redirect(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;",
					remap = false
			),
			remap = false,
			require = 0
	)
	private static String mtropt$fastFormat(String format, Object[] args) {
		HookStatus.mark("RenderPIDS.render (fast String.format)");
		return FastFormat.format(format, args);
	}
}

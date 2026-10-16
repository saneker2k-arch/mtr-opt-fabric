package dev.mtropt.mixin;

import dev.mtropt.FastFormat;
import dev.mtropt.HookStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Панель лифта собирает текст этажа через String.format каждый кадр.
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.render.RenderLiftPanel", remap = false)
public abstract class RenderLiftPanelMixin {

	@Redirect(
			method = "renderLiftDisplay",
			at = @At(
					value = "INVOKE",
					target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;",
					remap = false
			),
			remap = false,
			require = 0
	)
	private static String mtropt$fastFormat(String format, Object[] args) {
		HookStatus.mark("RenderLiftPanel.renderLiftDisplay (fast String.format)");
		return FastFormat.format(format, args);
	}
}

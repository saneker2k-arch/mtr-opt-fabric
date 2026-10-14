package dev.mtropt.mixin;

import dev.mtropt.OptConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Аналог ограничения дальности рельсов из MTR-34 (maxRailDistance).
 * В MTR 4 RenderRails.renderWithinRenderDistance(...) считает renderDistance = дальность прорисовки * 16
 * и для каждого сегмента рельса выполняет расчёты. Мы ограничиваем это значение сверху.
 *
 * Цель задаётся строкой (@Pseudo), поэтому для компиляции jar MTR не нужен;
 * если метод/класс изменится — миксин тихо не применится (require = 0), игра не упадёт.
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.render.RenderRails", remap = false)
public abstract class RenderRailsMixin {

	@ModifyVariable(
			method = "renderWithinRenderDistance",
			at = @At(value = "STORE", ordinal = 0),
			ordinal = 0,
			remap = false,
			require = 0
	)
	private static int mtropt$capRailRenderDistance(int renderDistanceBlocks) {
		final int cap = OptConfig.get().maxRailRenderDistanceBlocks;
		return cap > 0 ? Math.min(renderDistanceBlocks, cap) : renderDistanceBlocks;
	}
}

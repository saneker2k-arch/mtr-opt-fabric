package dev.mtropt.mixin;

import dev.mtropt.HookStatus;
import dev.mtropt.OptConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Оптимизации отрисовки рельсов (org.mtr.mod.render.RenderRails).
 * Цели заданы строкой (@Pseudo), поэтому для компиляции jar MTR не нужен;
 * если метод/константа изменятся, миксин тихо не применится (require = 0).
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.render.RenderRails", remap = false)
public abstract class RenderRailsMixin {

	/**
	 * Аналог ограничения дальности рельсов из MTR-34 (maxRailDistance).
	 * renderWithinRenderDistance считает renderDistance = дальность прорисовки * 16 и для каждого
	 * сегмента рельса делает расчёты; здесь значение ограничивается сверху.
	 */
	@ModifyVariable(
			method = "renderWithinRenderDistance",
			at = @At(value = "STORE", ordinal = 0),
			ordinal = 0,
			remap = false,
			require = 0
	)
	private static int mtropt$capRailRenderDistance(int renderDistanceBlocks) {
		HookStatus.mark("RenderRails.renderWithinRenderDistance (rail distance)");
		final int cap = OptConfig.get().maxRailRenderDistanceBlocks;
		return cap > 0 ? Math.min(renderDistanceBlocks, cap) : renderDistanceBlocks;
	}

	/**
	 * INVALID_NODE_CHECK_RADIUS = 16: при предмете для рельсов в руке MTR каждый кадр перебирает куб 33x33x33
	 * блоков. Здесь радиус уменьшается (верхняя граница циклов).
	 */
	@ModifyConstant(
			method = "render",
			constant = @Constant(intValue = 16),
			remap = false,
			require = 0
	)
	private static int mtropt$nodeCheckRadiusMax(int original) {
		HookStatus.mark("RenderRails.render (node check radius)");
		final int radius = OptConfig.get().nodeCheckRadiusBlocks;
		return radius > 0 ? Math.min(original, radius) : original;
	}

	/** Нижняя граница тех же циклов (-INVALID_NODE_CHECK_RADIUS). */
	@ModifyConstant(
			method = "render",
			constant = @Constant(intValue = -16),
			remap = false,
			require = 0
	)
	private static int mtropt$nodeCheckRadiusMin(int original) {
		final int radius = OptConfig.get().nodeCheckRadiusBlocks;
		return radius > 0 ? -Math.min(-original, radius) : original;
	}
}

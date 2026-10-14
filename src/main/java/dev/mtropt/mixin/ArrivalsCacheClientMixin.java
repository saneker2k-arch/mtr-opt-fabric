package dev.mtropt.mixin;

import dev.mtropt.HookStatus;
import dev.mtropt.OptConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * ArrivalsCacheClient вызывает super(3000): период запросов прибытия поездов (табло PIDS) к серверу.
 * Тип параметра в ядре (int/long) неизвестен, поэтому есть обработчики для обоих вариантов константы;
 * сработает тот, что соответствует байткоду. Обработчики статические: константа используется до вызова super().
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.data.ArrivalsCacheClient", remap = false)
public abstract class ArrivalsCacheClientMixin {

	@ModifyConstant(
			method = "<init>",
			constant = @Constant(intValue = 3000),
			remap = false,
			require = 0
	)
	private static int mtropt$arrivalsRefreshInt(int original) {
		HookStatus.mark("ArrivalsCacheClient.<init> (arrivals refresh, int)");
		final int refresh = OptConfig.get().arrivalsRefreshMillis;
		return refresh > 0 ? refresh : original;
	}

	@ModifyConstant(
			method = "<init>",
			constant = @Constant(longValue = 3000L),
			remap = false,
			require = 0
	)
	private static long mtropt$arrivalsRefreshLong(long original) {
		HookStatus.mark("ArrivalsCacheClient.<init> (arrivals refresh, long)");
		final int refresh = OptConfig.get().arrivalsRefreshMillis;
		return refresh > 0 ? refresh : original;
	}
}

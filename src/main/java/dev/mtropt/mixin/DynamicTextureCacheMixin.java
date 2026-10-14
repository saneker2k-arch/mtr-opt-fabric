package dev.mtropt.mixin;

import dev.mtropt.HookStatus;
import dev.mtropt.OptConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * DynamicTextureCache.COOLDOWN_TIME = 10000 мс: текстура, которую не запрашивали это время, выгружается,
 * а удалённая уничтожается через такой же интервал. Параметр позволяет подобрать баланс память/перерисовка.
 * Меняются обе точки: продление срока жизни в getResource и задержка уничтожения в tick.
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.client.DynamicTextureCache", remap = false)
public abstract class DynamicTextureCacheMixin {

	@ModifyConstant(
			method = {"getResource", "tick"},
			constant = @Constant(intValue = 10000),
			remap = false,
			require = 0
	)
	private int mtropt$textureLifetime(int original) {
		HookStatus.mark("DynamicTextureCache (texture lifetime)");
		final int lifetime = OptConfig.get().dynamicTextureLifetimeMillis;
		return lifetime > 0 ? lifetime : original;
	}
}

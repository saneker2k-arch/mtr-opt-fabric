package dev.mtropt.mixin;

import dev.mtropt.HookStatus;
import dev.mtropt.OptConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import dev.mtropt.FastFormat;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * DynamicTextureCache.COOLDOWN_TIME = 10000 мс (в байт-коде это long-константа, а не int; поэтому раньше хук не срабатывал): текстура, которую не запрашивали это время, выгружается,
 * а удалённая уничтожается через такой же интервал. Параметр позволяет подобрать баланс память/перерисовка.
 * Меняются обе точки: продление срока жизни в getResource и задержка уничтожения в tick.
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.client.DynamicTextureCache", remap = false)
public abstract class DynamicTextureCacheMixin {

	@ModifyConstant(
			method = {"getResource", "lambda$getResource$19", "lambda$tick$1"},
			constant = @Constant(longValue = 10000L),
			remap = false,
			require = 0
	)
	private long mtropt$textureLifetime(long original) {
		HookStatus.mark("DynamicTextureCache (texture lifetime)");
		final int lifetime = OptConfig.get().dynamicTextureLifetimeMillis;
		return lifetime > 0 ? lifetime : original;
	}

	/**
	 * Каждый get*-метод (названия станций, знаки, табло, схемы маршрутов) формирует ключ текстуры через String.format
	 * при каждом обращении, то есть каждый кадр для каждого знака. Заменяем на быструю версию (только %s).
	 */
	@Redirect(
			method = {
					"getPixelatedText", "getColorStrip", "getStationName", "getTallStationName",
					"getStationNameEntrance", "getSingleRowStationName", "getSignText", "getLiftPanelDisplay",
					"getExitSignLetter", "getRouteSquare", "getDirectionArrow", "getRouteMap"
			},
			at = @At(
					value = "INVOKE",
					target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;",
					remap = false
			),
			remap = false,
			require = 0
	)
	private static String mtropt$fastFormat(String format, Object[] args) {
		HookStatus.mark("DynamicTextureCache (fast String.format)");
		return FastFormat.format(format, args);
	}
}

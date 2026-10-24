package dev.mtropt.mixin;

import dev.mtropt.HookStatus;
import dev.mtropt.OptConfig;
import dev.mtropt.ResourceRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CustomResourceLoader.reload() (F3+T, загрузка ресурс-пака, загрузка zip в игре) заменяет все ресурсы MTR новыми,
 * а модели в видеопамяти от старых ресурсов остаются навсегда. Помечаем все уже существующие ресурсы на освобождение;
 * ресурсы, созданные во время и после reload(), не затрагиваются.
 *
 * Важно: OptimizedRendererWrapper.beginReload() для этого не годится: MTR вызывает его при каждой сборке модели
 * (это защита состояния OpenGL, а не событие перезагрузки).
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.client.CustomResourceLoader", remap = false)
public abstract class CustomResourceLoaderMixin {

	@Inject(method = "reload", at = @At("HEAD"), remap = false, require = 0)
	private static void mtropt$onReload(CallbackInfo ci) {
		if (OptConfig.get().fixResourceLeaks) {
			HookStatus.mark("CustomResourceLoader.reload (release old models)");
			ResourceRegistry.requestDisposeAll();
		}
	}
}

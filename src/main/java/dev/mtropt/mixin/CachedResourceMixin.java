package dev.mtropt.mixin;

import dev.mtropt.HookStatus;
import dev.mtropt.OptConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * «Запоминание» моделей поездов (и других ресурсов MTR), чтобы их не пересобирали при каждом возвращении в поле зрения.
 *
 * Как это устроено в MTR 4: модели вагонов, тележек, объектов и рельсов лежат в CachedResource.
 * Если к модели не обращались дольше срока жизни (VehicleModel.MODEL_LIFESPAN = 60000 мс), её данные
 * сбрасываются в null, а при следующем обращении модель собирается заново (разбор модели, сборка
 * оптимизированных буферов). При этом за тик собирается только один ресурс (флаг canFetchCache),
 * поэтому вагоны «выпрыгивают» по одному.
 *
 * Здесь после каждого обращения срок жизни продлевается минимум до vehicleModelCacheMillis.
 * Ресурсы, которые MTR и так хранит вечно (preload), не затрагиваются: срок только увеличивается.
 */
@Pseudo
@Mixin(targets = "org.mtr.mod.resource.CachedResource", remap = false)
public abstract class CachedResourceMixin {

	@Shadow(remap = false)
	private Object data;

	@Shadow(remap = false)
	private long expiry;

	@Inject(
			method = "getData",
			at = @At("RETURN"),
			remap = false,
			require = 0
	)
	private void mtropt$keepResourceCached(boolean force, CallbackInfoReturnable<Object> cir) {
		HookStatus.mark("CachedResource.getData (vehicle model cache)");
		final int minLifespan = OptConfig.get().vehicleModelCacheMillis;
		if (minLifespan > 0 && data != null) {
			final long minExpiry = System.currentTimeMillis() + minLifespan;
			if (expiry < minExpiry) {
				expiry = minExpiry;
			}
		}
	}
}

package dev.mtropt;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MtrOptClient implements ClientModInitializer {

	public static final String MOD_ID = "mtr_opt";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		final OptConfig c = OptConfig.get();
		ResourceCacheTrimmer.init();
		LOGGER.info("MTR Optimization Addon: railDistance={} blocks, occlusionRadius={} chunks, workerSleep={} ms, nodeCheckRadius={} blocks, textureLifetime={} ms, arrivalsRefresh={} ms, modelCache={} ms, fastFormat={}, textCacheMax={}",
				c.maxRailRenderDistanceBlocks, c.maxOcclusionRadiusChunks, c.workerThreadSleepMillis,
				c.nodeCheckRadiusBlocks, c.dynamicTextureLifetimeMillis, c.arrivalsRefreshMillis, c.vehicleModelCacheMillis, c.fastStringFormat, c.resourceTextCacheMaxEntries);
	}
}

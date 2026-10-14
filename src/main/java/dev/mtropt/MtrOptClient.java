package dev.mtropt;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MtrOptClient implements ClientModInitializer {

	public static final String MOD_ID = "mtr_opt";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		final OptConfig config = OptConfig.get();
		LOGGER.info("MTR Optimization Addon: rail distance={} blocks, occlusion radius={} chunks, worker sleep={} ms",
				config.maxRailRenderDistanceBlocks, config.maxOcclusionRadiusChunks, config.workerThreadSleepMillis);
	}
}

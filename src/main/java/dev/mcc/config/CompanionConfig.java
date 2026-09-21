package dev.mcc.config;

/**
 * Phase 0 stub — a full Gson POJO with clamp() lands in Phase 1.
 * Kept loadable so MccMod.onInitialize has a valid target.
 */
public final class CompanionConfig {
	private CompanionConfig() {}

	public static void load() {
		// TODO Phase 1: read config.json from FabricLoader.getConfigDir(), clamp()
	}
}
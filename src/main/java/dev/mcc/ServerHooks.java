package dev.mcc;

import dev.mcc.entity.CompanionEntity;
import dev.mcc.entity.CompanionTicker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Server lifecycle + per-tick hooks.
 *
 * Headless-critical: a vanilla MC server stops ticking entities when it decides
 * the world is "empty" (no players within simulation distance — the
 * `emptyTime < 300` + `inEntityTickingRange` gates in ServerLevel.tick). That
 * gating would stall the companion's deterministic logic during headless
 * verification. So we drive CompanionTicker ourselves from ServerTickEvents,
 * which fires unconditionally every server tick. The entity.tick() path remains
 * for when a real player IS present (client-facing rendering hooks still need
 * it), but the deterministic brain/goals/hunger run from here.
 */
public final class ServerHooks {
	private ServerHooks() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(mcServer -> {
			CompanionManager.forEachCompanion(mcServer, CompanionTicker::tick);
		});
	}

	// NOTE: LlmLifecycle (launchCommand) + ServerLifecycleEvents.SERVER_STARTED
	// for persistence load land in later phases.
}
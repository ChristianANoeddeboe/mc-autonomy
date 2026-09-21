package dev.mcc;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mcc.ChatListener;
import dev.mcc.CompanionRegistry;
import dev.mcc.ServerHooks;
import dev.mcc.command.MccCommands;
import dev.mcc.config.CompanionConfig;

/**
 * Mod entrypoint (fabric.mod.json "main").
 * Phase 0: registers the (stubbed) subsystems and logs "[MCC] init".
 */
public final class MccMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("MCC");

	@Override
	public void onInitialize() {
		CompanionConfig.load();
		CompanionRegistry.register();
		MccCommands.register();
		ChatListener.register();
		ServerHooks.register();
		LOGGER.info("[MCC] init");
	}
}
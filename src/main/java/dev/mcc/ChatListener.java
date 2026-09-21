package dev.mcc;

import dev.mcc.chat.ChatParser;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Listens for chat messages addressed to the companion and routes them into the
 * owner's brain. Headless smoke drives the same path via the `say` command
 * (console owner), so chat events are the player-facing route.
 *
 * Phase 3: parses + logs the intent; the brain's onPlayerMessage hook (Phase 6)
 * is where the parsed intent is consumed.
 */
public final class ChatListener {
	private ChatListener() {}

	public static void register() {
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			String t = message.signedContent();
			if (ChatParser.isAddressed(t)) {
				String stripped = ChatParser.strip(t);
				ChatParser.Intent intent = ChatParser.parse(stripped);
				MccMod.LOGGER.info("[MCC][hear] {}: {} (intent={})",
					sender.getName().getString(), stripped, intent.type());
				ServerPlayer player = sender;
				ServerLevel level = player.level() instanceof ServerLevel sl ? sl : null;
				if (level != null) {
					CompanionManager.forOwner(level, player.getUUID()).ifPresent(c ->
						// Phase 6: brain.onPlayerMessage(new PlayerOwner(player), stripped)
						c.say("heard: " + stripped));
				}
			}
			return true;
		});
	}
}
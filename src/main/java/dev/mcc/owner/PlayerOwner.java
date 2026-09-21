package dev.mcc.owner;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Wraps a real ServerPlayer; messages go through the player's chat. */
public final class PlayerOwner implements Owner {
	private final ServerPlayer player;

	public PlayerOwner(ServerPlayer player) {
		this.player = player;
	}

	@Override public UUID id() { return player.getUUID(); }
	@Override public String name() {
		try {
			return player.getGameProfile().name();
		} catch (Exception e) {
			return player.getUUID().toString();
		}
	}
	@Override public Vec3 pos() { return player.position(); }
	@Override public void sendMessage(String text) {
		player.sendSystemMessage(Component.literal(text));
	}
	@Override public Optional<ServerPlayer> player() { return Optional.of(player); }
}
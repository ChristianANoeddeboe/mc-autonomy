package dev.mcc.owner;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owner bound to the server console. pos is fixed at the spawn anchor. Messages
 * go to the server-wide log so headless smoke can read "[MCC][say]" etc.
 */
public final class ConsoleOwner implements Owner {
	private static final Logger LOGGER = LoggerFactory.getLogger("MCC");

	private final MinecraftServer server;
	private final UUID id;
	private final String name;
	private final Vec3 anchor;

	public ConsoleOwner(MinecraftServer server, UUID id, String name, Vec3 anchor) {
		this.server = server;
		this.id = id;
		this.name = name;
		this.anchor = anchor;
	}

	@Override public UUID id() { return id; }
	@Override public String name() { return name; }
	@Override public Vec3 pos() { return anchor; }
	@Override public void sendMessage(String text) {
		server.sendSystemMessage(Component.literal(text));
		LOGGER.info("[MCC][console] {}", text);
	}
	@Override public Optional<ServerPlayer> player() { return Optional.empty(); }
}
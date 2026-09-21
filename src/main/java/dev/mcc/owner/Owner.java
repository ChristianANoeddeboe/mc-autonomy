package dev.mcc.owner;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Abstraction over "who the companion answers to". Lets the whole system run
 * with no client: a ConsoleOwner is bound to the server console and a fixed
 * anchor point, a PlayerOwner wraps a real ServerPlayer.
 */
public interface Owner {
	UUID id();
	String name();
	Vec3 pos();
	void sendMessage(String text);
	Optional<ServerPlayer> player();
	default boolean isPlayer() { return player().isPresent(); }
}
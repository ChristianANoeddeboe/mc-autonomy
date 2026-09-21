package dev.mcc;

import dev.mcc.entity.CompanionEntity;
import dev.mcc.owner.Owner;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * Tracks owner UUID -> companion entity UUID, enforces onePerPlayer, and lets
 * commands resolve "the companion for X" on the server thread.
 */
public final class CompanionManager {
	/** Stable identity for the server console source (commands without a player). */
	public static final UUID CONSOLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
	private static final Map<UUID, UUID> BY_OWNER = new ConcurrentHashMap<>();

	private CompanionManager() {}

	public static boolean spawn(ServerLevel level, Owner owner, double x, double y, double z) {
		UUID ownerId = owner.id();
		if (BY_OWNER.containsKey(ownerId)) {
			dev.mcc.MccMod.LOGGER.info("[MCC][spawn] owner already has a companion, refusing second");
			return false;
		}
		CompanionEntity e = new CompanionEntity(CompanionRegistry.TYPE, level);
		e.setPos(x, y, z);
		e.setOwner(owner);
		level.addFreshEntity(e);
		BY_OWNER.put(ownerId, e.getUUID());
		dev.mcc.MccMod.LOGGER.info("[MCC][spawn] id={} at {},{} {} owner={}", e.getUUID(), (int)x, (int)y, (int)z, owner.name());
		return true;
	}

	public static Optional<CompanionEntity> forOwner(ServerLevel level, UUID ownerId) {
		UUID entityId = BY_OWNER.get(ownerId);
		if (entityId == null) return Optional.empty();
		net.minecraft.world.entity.Entity found = level.getEntity(entityId);
		if (found instanceof CompanionEntity ce && ce.isAlive()) {
			return Optional.of(ce);
		}
		dev.mcc.MccMod.LOGGER.info("[MCC][lookup] owner={} entity={} found={} type={} alive={}",
			ownerId, entityId, found != null, found != null ? found.getClass().getSimpleName() : "null",
			found != null && !found.isRemoved() ? found.isAlive() : "n/a");
		BY_OWNER.remove(ownerId);
		return Optional.empty();
	}

	public static boolean despawn(ServerLevel level, UUID ownerId) {
		UUID entityId = BY_OWNER.remove(ownerId);
		if (entityId == null) return false;
		net.minecraft.world.entity.Entity found = level.getEntity(entityId);
		if (found != null) {
			found.discard();
			dev.mcc.MccMod.LOGGER.info("[MCC][despawn] id={} owner={}", entityId, ownerId);
		}
		return true;
	}

	public static java.util.Set<UUID> byOwnerIds() {
		return java.util.Set.copyOf(BY_OWNER.keySet());
	}

	/** Iterate each live companion on the overworld. Used by the server tick hook. */
	public static void forEachCompanion(net.minecraft.server.MinecraftServer server,
			java.util.function.Consumer<CompanionEntity> action) {
		net.minecraft.server.level.ServerLevel level = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
		if (level == null) return;
		for (UUID ownerId : BY_OWNER.keySet()) {
			forOwner(level, ownerId).ifPresent(action);
		}
	}

	public static void removeByEntityId(UUID entityId) {
		BY_OWNER.values().removeIf(id -> id.equals(entityId));
	}
}
package com.example.companion.behavior;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Reactive behavior: sprint perpendicular to a lit creeper to avoid the explosion.
 *
 * <p>Trigger: lit {@link CreeperEntity} within {@value TRIGGER_DISTANCE} blocks.
 * <p>Action: sprint 90° away from the creeper's position.
 *
 * <p>TODO (Phase 2): implement perpendicular-dodge vector calculation.
 */
public class DodgeBehavior {

    private static final double TRIGGER_DISTANCE = 6.0;

    private final CompanionEntity entity;

    public DodgeBehavior(CompanionEntity entity) {
        this.entity = entity;
    }

    /** @return true if this behavior is active */
    public boolean tick() {
        CreeperEntity lit = findLitCreeper();
        if (lit == null) return false;

        CompanionMod.LOGGER.debug("DodgeBehavior: lit creeper at distance {}", entity.distanceTo(lit));
        entity.setSprinting(true);
        // TODO: compute perpendicular dodge vector and set navigation target
        return true;
    }

    private CreeperEntity findLitCreeper() {
        Box box = entity.getBoundingBox().expand(TRIGGER_DISTANCE);
        List<CreeperEntity> creepers = entity.getWorld().getEntitiesByClass(
                CreeperEntity.class, box,
                c -> c.getFuseSpeed() > 0 // has a lit fuse
        );
        return creepers.isEmpty() ? null : creepers.get(0);
    }
}

package com.example.companion.behavior;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.List;

/**
 * Reactive behavior: sprint perpendicular to a lit creeper to avoid the explosion.
 *
 * <p>Trigger: ignited {@link CreeperEntity} within {@value TRIGGER_DISTANCE} blocks.
 * <p>Action: sprint 90° sideways relative to the creeper's approach vector.
 */
public class DodgeBehavior {

    private static final double TRIGGER_DISTANCE = 6.0;
    private static final double DODGE_DISTANCE   = 10.0;
    private static final double SPRINT_SPEED     = 1.5;

    private final CompanionEntity entity;

    public DodgeBehavior(CompanionEntity entity) {
        this.entity = entity;
    }

    /** @return true if this behavior is active */
    public boolean tick() {
        if (!(entity.getWorld() instanceof ServerWorld sw)) return false;

        CreeperEntity lit = findLitCreeper(sw);
        if (lit == null) return false;

        entity.setSprinting(true);

        // Perpendicular dodge: rotate the threat vector 90° around Y
        Vec3d toCreeper = lit.getPos().subtract(entity.getPos());
        // Rotate 90° in the XZ plane: (x,z) → (-z,x)
        Vec3d perp = new Vec3d(-toCreeper.z, 0, toCreeper.x).normalize();
        Vec3d dodgeTarget = entity.getPos().add(perp.multiply(DODGE_DISTANCE));

        int dx = (int) dodgeTarget.x;
        int dz = (int) dodgeTarget.z;
        int dy = sw.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, dx, dz);

        entity.getNavigation().startMovingTo(dx, dy, dz, SPRINT_SPEED);
        CompanionMod.LOGGER.debug("DodgeBehavior: dodging creeper at distance {}",
                Math.round(entity.distanceTo(lit) * 10) / 10.0);
        return true;
    }

    private CreeperEntity findLitCreeper(ServerWorld sw) {
        Box box = entity.getBoundingBox().expand(TRIGGER_DISTANCE);
        List<CreeperEntity> creepers = sw.getEntitiesByClass(CreeperEntity.class, box,
                c -> c.isIgnited());
        return creepers.isEmpty() ? null : creepers.get(0);
    }
}

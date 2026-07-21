package com.example.companion.behavior;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Reactive behavior: sprint perpendicular to a lit creeper to avoid the explosion.
 *
 * <p>Trigger: ignited {@link Creeper} within {@value TRIGGER_DISTANCE} blocks.
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
        if (!(entity.level() instanceof ServerLevel sw)) return false;

        Creeper lit = findLitCreeper(sw);
        if (lit == null) return false;

        entity.setSprinting(true);

        // Perpendicular dodge: rotate the threat vector 90° around Y
        Vec3 toCreeper = lit.position().subtract(entity.position());
        // Rotate 90° in the XZ plane: (x,z) → (-z,x)
        Vec3 perp = new Vec3(-toCreeper.z, 0, toCreeper.x).normalize();
        Vec3 dodgeTarget = entity.position().add(perp.scale(DODGE_DISTANCE));

        int dx = (int) dodgeTarget.x;
        int dz = (int) dodgeTarget.z;
        int dy = sw.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dx, dz);

        entity.getNavigation().moveTo(dx, dy, dz, SPRINT_SPEED);
        CompanionMod.LOGGER.debug("DodgeBehavior: dodging creeper at distance {}",
                Math.round(entity.distanceTo(lit) * 10) / 10.0);
        return true;
    }

    private Creeper findLitCreeper(ServerLevel sw) {
        AABB box = entity.getBoundingBox().inflate(TRIGGER_DISTANCE);
        List<Creeper> creepers = sw.getEntitiesOfClass(Creeper.class, box,
                c -> c.isIgnited());
        return creepers.isEmpty() ? null : creepers.get(0);
    }
}

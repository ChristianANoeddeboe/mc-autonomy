package com.example.companion.behavior;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Reactive behavior: sprint away from threats when health is critically low.
 *
 * <p>Trigger: health ≤ {@value HEALTH_THRESHOLD} (4 hearts).
 * <p>Action: navigate to a position directly away from the nearest threat.
 */
public class FleeBehavior {

    /** 4 hearts = 8 HP */
    private static final float HEALTH_THRESHOLD = 8.0f;
    private static final double SCAN_RADIUS     = 16.0;
    private static final double FLEE_DISTANCE   = 20.0;
    private static final double SPRINT_SPEED    = 1.5;

    private final CompanionEntity entity;

    public FleeBehavior(CompanionEntity entity) {
        this.entity = entity;
    }

    /** @return true if this behavior is active */
    public boolean tick() {
        if (entity.getHealth() > HEALTH_THRESHOLD) {
            entity.setSprinting(false);
            return false;
        }

        if (!(entity.level() instanceof ServerLevel sw)) return false;

        LivingEntity threat = findNearestThreat(sw);
        if (threat == null) return false;

        entity.setSprinting(true);

        // Vector away from the threat
        Vec3 toThreat = threat.position().subtract(entity.position());
        Vec3 fleeDir  = toThreat.length() > 0 ? toThreat.scale(-1).normalize() : new Vec3(1, 0, 0);
        Vec3 fleeTarget = entity.position().add(fleeDir.scale(FLEE_DISTANCE));

        int fx = (int) fleeTarget.x;
        int fz = (int) fleeTarget.z;
        int fy = sw.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fx, fz);

        boolean moving = entity.getNavigation().moveTo(fx, fy, fz, SPRINT_SPEED);
        CompanionMod.LOGGER.debug("FleeBehavior: health={} fleeing from {} nav={}",
                entity.getHealth(), threat.getType().getDescriptionId(), moving);
        return true;
    }

    private LivingEntity findNearestThreat(ServerLevel sw) {
        AABB box = entity.getBoundingBox().inflate(SCAN_RADIUS);
        List<Mob> threats = sw.getEntitiesOfClass(Mob.class, box,
                mob -> mob.isAlive() && mob.getTarget() != null);
        return threats.stream()
                .min((a, b) -> Double.compare(entity.distanceTo(a), entity.distanceTo(b)))
                .orElse(null);
    }
}

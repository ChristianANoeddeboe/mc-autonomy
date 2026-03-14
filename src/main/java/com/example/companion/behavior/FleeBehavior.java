package com.example.companion.behavior;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

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

        if (!(entity.getWorld() instanceof ServerWorld sw)) return false;

        LivingEntity threat = findNearestThreat(sw);
        if (threat == null) return false;

        entity.setSprinting(true);

        // Vector away from the threat
        Vec3d toThreat = threat.getPos().subtract(entity.getPos());
        Vec3d fleeDir  = toThreat.length() > 0 ? toThreat.negate().normalize() : Vec3d.POSITIVE_X;
        Vec3d fleeTarget = entity.getPos().add(fleeDir.multiply(FLEE_DISTANCE));

        int fx = (int) fleeTarget.x;
        int fz = (int) fleeTarget.z;
        int fy = sw.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, fx, fz);

        boolean moving = entity.getNavigation().startMovingTo(fx, fy, fz, SPRINT_SPEED);
        CompanionMod.LOGGER.debug("FleeBehavior: health={} fleeing from {} nav={}",
                entity.getHealth(), threat.getType().getUntranslatedName(), moving);
        return true;
    }

    private LivingEntity findNearestThreat(ServerWorld sw) {
        Box box = entity.getBoundingBox().expand(SCAN_RADIUS);
        List<MobEntity> threats = sw.getEntitiesByClass(MobEntity.class, box,
                mob -> mob.isAlive() && mob.getTarget() != null);
        return threats.stream()
                .min((a, b) -> Double.compare(entity.distanceTo(a), entity.distanceTo(b)))
                .orElse(null);
    }
}

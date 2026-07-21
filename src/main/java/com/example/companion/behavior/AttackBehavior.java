package com.example.companion.behavior;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Reactive behavior: attack the nearest hostile mob within melee range.
 *
 * <p>Trigger: a {@link Mob} targeting the companion or any player within
 * {@value TRIGGER_DISTANCE} blocks.
 * <p>Action: face target, navigate into melee range, and call {@code doHurtTarget}.
 */
public class AttackBehavior {

    private static final double TRIGGER_DISTANCE = 6.0;
    private static final double MELEE_DISTANCE   = 2.5;
    private static final double CHASE_SPEED      = 1.3;

    private final CompanionEntity entity;

    public AttackBehavior(CompanionEntity entity) {
        this.entity = entity;
    }

    /**
     * @return true if this behavior is currently active (hostile target in range)
     */
    public boolean tick() {
        if (!(entity.level() instanceof ServerLevel sw)) return false;

        LivingEntity target = findNearestHostile(sw);
        if (target == null) return false;

        entity.setTarget(target);
        entity.getLookControl().setLookAt(target, 30f, 30f);

        double dist = entity.distanceTo(target);
        if (dist <= MELEE_DISTANCE) {
            entity.getNavigation().stop();
            entity.doHurtTarget(sw, target);
            CompanionMod.LOGGER.debug("AttackBehavior: attacked {}", target.getType().getDescriptionId());
        } else {
            entity.getNavigation().moveTo(target, CHASE_SPEED);
        }

        return true;
    }

    private LivingEntity findNearestHostile(ServerLevel sw) {
        AABB box = entity.getBoundingBox().inflate(TRIGGER_DISTANCE);
        // Only fight mobs that are actively targeting the companion itself
        List<Mob> targeting = sw.getEntitiesOfClass(Mob.class, box,
                mob -> mob != entity && mob.isAlive() && entity.equals(mob.getTarget()));
        return nearest(targeting);
    }

    private LivingEntity nearest(List<? extends LivingEntity> list) {
        return list.stream()
                .min((a, b) -> Double.compare(entity.distanceTo(a), entity.distanceTo(b)))
                .orElse(null);
    }
}

package com.example.companion.behavior;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.List;

/**
 * Reactive behavior: attack the nearest hostile mob within melee range.
 *
 * <p>Trigger: a {@link MobEntity} targeting the companion or any player within
 * {@value TRIGGER_DISTANCE} blocks.
 * <p>Action: face target, navigate into melee range, and call {@code tryAttack}.
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
        if (!(entity.getWorld() instanceof ServerWorld sw)) return false;

        LivingEntity target = findNearestHostile(sw);
        if (target == null) return false;

        entity.setTarget(target);
        entity.lookAt(target, 30f, 30f);

        double dist = entity.distanceTo(target);
        if (dist <= MELEE_DISTANCE) {
            entity.getNavigation().stop();
            entity.tryAttack(sw, target);
            CompanionMod.LOGGER.debug("AttackBehavior: attacked {}", target.getType().getUntranslatedName());
        } else {
            entity.getNavigation().startMovingTo(target, CHASE_SPEED);
        }

        return true;
    }

    private LivingEntity findNearestHostile(ServerWorld sw) {
        Box box = entity.getBoundingBox().expand(TRIGGER_DISTANCE);

        // Prioritise mobs that are actively targeting the companion
        List<MobEntity> targeting = sw.getEntitiesByClass(MobEntity.class, box,
                mob -> mob != entity && mob.isAlive() && entity.equals(mob.getTarget()));
        if (!targeting.isEmpty()) {
            return nearest(targeting);
        }

        // Fall back to any mob targeting a nearby player
        List<MobEntity> hostile = sw.getEntitiesByClass(MobEntity.class, box,
                mob -> mob != entity && mob.isAlive() && mob.getTarget() instanceof net.minecraft.entity.player.PlayerEntity);
        return nearest(hostile);
    }

    private LivingEntity nearest(List<? extends LivingEntity> list) {
        return list.stream()
                .min((a, b) -> Double.compare(entity.distanceTo(a), entity.distanceTo(b)))
                .orElse(null);
    }
}

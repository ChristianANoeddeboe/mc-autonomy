package com.example.companion.behavior;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Box;

import java.util.List;

/**
 * Reactive behavior: attack the nearest hostile mob within melee range.
 *
 * <p>Trigger: hostile mob within {@value TRIGGER_DISTANCE} blocks.
 * <p>Action: face target and swing weapon.
 *
 * <p>TODO (Phase 2): wire up actual attack/swing call.
 */
public class AttackBehavior {

    private static final double TRIGGER_DISTANCE = 4.0;

    private final CompanionEntity entity;
    private boolean active = false;

    public AttackBehavior(CompanionEntity entity) {
        this.entity = entity;
    }

    /** @return true if this behavior is currently active */
    public boolean tick() {
        LivingEntity target = findNearestHostile();
        if (target == null) {
            active = false;
            return false;
        }

        active = true;
        entity.lookAt(target, 30f, 30f);
        // TODO: entity.tryAttack(target) — requires ServerWorld check
        CompanionMod.LOGGER.debug("AttackBehavior: targeting {}", target.getType().getUntranslatedName());
        return true;
    }

    private LivingEntity findNearestHostile() {
        Box box = entity.getBoundingBox().expand(TRIGGER_DISTANCE);
        List<MobEntity> mobs = entity.getWorld().getEntitiesByClass(MobEntity.class, box,
                mob -> mob != entity && mob.getTarget() != null);
        if (mobs.isEmpty()) return null;
        return mobs.stream()
                .min((a, b) -> Double.compare(entity.distanceTo(a), entity.distanceTo(b)))
                .orElse(null);
    }
}

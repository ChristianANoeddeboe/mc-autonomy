package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Map;

/**
 * FOLLOW_PLAYER — continuously stay within {@value CLOSE_DISTANCE} blocks of the nearest player.
 *
 * <p>This is a continuous goal; {@link #isComplete()} always returns false.
 * It fails if the player moves beyond {@value MAX_DISTANCE} blocks or disappears.
 */
public class FollowPlayerGoal implements CompanionGoal {

    private static final double CLOSE_DISTANCE = 3.0;
    private static final double START_MOVING_DISTANCE = 5.0;
    private static final double MAX_DISTANCE = 64.0;
    private static final double WALK_SPEED = 1.2;

    private final CompanionEntity entity;
    private boolean failed = false;

    public FollowPlayerGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false;
        CompanionMod.LOGGER.info("FollowPlayerGoal started");
    }

    @Override
    public void tick() {
        PlayerEntity target = entity.getWorld().getClosestPlayer(entity, MAX_DISTANCE);
        if (target == null) {
            failed = true;
            entity.getNavigation().stop();
            return;
        }

        double dist = entity.distanceTo(target);

        if (dist > MAX_DISTANCE) {
            failed = true;
            entity.getNavigation().stop();
            return;
        }

        if (dist > START_MOVING_DISTANCE) {
            entity.getNavigation().startMovingTo(target, WALK_SPEED);
        } else if (dist <= CLOSE_DISTANCE) {
            entity.getNavigation().stop();
        }
    }

    @Override public boolean isComplete()   { return false; } // continuous
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "Player too far or unreachable"; }
    @Override public void reset()           { failed = false; entity.getNavigation().stop(); }
}

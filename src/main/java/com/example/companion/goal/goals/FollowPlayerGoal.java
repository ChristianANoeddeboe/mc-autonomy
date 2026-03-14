package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;

/**
 * FOLLOW_PLAYER — continuously navigate to stay within 3 blocks of the nearest player.
 *
 * <p>Complete when: player is within 3 blocks (continuous — never truly "done").
 * <p>Fails when: player is too far away or unreachable after timeout.
 *
 * <p>TODO (Phase 2): wire up vanilla navigation.
 */
public class FollowPlayerGoal implements CompanionGoal {

    private static final double CLOSE_DISTANCE = 3.0;
    private static final double MAX_DISTANCE = 128.0;

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
            return;
        }
        double dist = entity.distanceTo(target);
        if (dist > CLOSE_DISTANCE) {
            // TODO: entity.getNavigation().startMovingTo(target, 1.0);
        }
        if (dist > MAX_DISTANCE) {
            failed = true;
        }
    }

    @Override public boolean isComplete()   { return false; } // continuous goal
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "Player too far or unreachable"; }
    @Override public void reset() { failed = false; }
}

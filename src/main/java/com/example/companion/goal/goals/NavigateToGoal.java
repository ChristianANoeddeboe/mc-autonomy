package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;

/**
 * NAVIGATE_TO — walk to a set of world coordinates supplied in params.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code x}, {@code z} — target XZ (required)</li>
 *   <li>{@code y} — target Y; defaults to terrain surface if absent</li>
 *   <li>{@code radius} — arrival radius in blocks (default 3.0)</li>
 * </ul>
 *
 * <p>Complete when: XZ distance to target ≤ {@code radius}.
 * <p>Fails when: navigation cannot be started or times out after 2 minutes.
 */
public class NavigateToGoal implements CompanionGoal {

    private static final double DEFAULT_RADIUS = 3.0;
    private static final double WALK_SPEED     = 1.0;
    private static final int    NAV_TIMEOUT    = 20 * 120; // 2 min

    private final CompanionEntity entity;
    private boolean failed   = false;
    private boolean complete = false;

    private double targetX, targetY, targetZ;
    private double arrivalRadius;
    private int    navTicks = 0;

    public NavigateToGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false; complete = false; navTicks = 0;

        targetX       = toDouble(params.get("x"), entity.getX());
        targetZ       = toDouble(params.get("z"), entity.getZ());
        arrivalRadius = toDouble(params.get("radius"), DEFAULT_RADIUS);

        if (params.containsKey("y")) {
            targetY = toDouble(params.get("y"), entity.getY());
        } else if (entity.level() instanceof ServerLevel sw) {
            targetY = sw.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (int) targetX, (int) targetZ);
        } else {
            targetY = entity.getY();
        }

        boolean started = entity.getNavigation().moveTo(targetX, targetY, targetZ, WALK_SPEED);
        CompanionMod.LOGGER.info("NavigateToGoal: heading to ({}, {}, {}) nav={}",
                (int) targetX, (int) targetY, (int) targetZ, started);
        if (!started) failed = true;
    }

    @Override
    public void tick() {
        double dx = entity.getX() - targetX;
        double dz = entity.getZ() - targetZ;
        if (Math.sqrt(dx * dx + dz * dz) <= arrivalRadius) {
            entity.getNavigation().stop();
            complete = true;
            return;
        }

        // Navigation finished but we're not there — blocked
        if (entity.getNavigation().isDone()) {
            failed = true;
            return;
        }

        if (++navTicks > NAV_TIMEOUT) {
            CompanionMod.LOGGER.warn("NavigateToGoal: timed out navigating to ({}, {}, {})",
                    (int) targetX, (int) targetY, (int) targetZ);
            failed = true;
        }
    }

    private static double toDouble(Object val, double fallback) {
        return val instanceof Number n ? n.doubleValue() : fallback;
    }

    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "Could not navigate to target location"; }

    @Override
    public void reset() {
        failed = false; complete = false; navTicks = 0;
        entity.getNavigation().stop();
    }
}

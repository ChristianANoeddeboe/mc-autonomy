package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Random;

/**
 * EXPLORE — wander to random waypoints until the companion has moved
 * {@code targetDistance} blocks from its starting position.
 *
 * <p>Complete when: displacement from start ≥ {@code targetDistance}.
 * <p>Fails when: navigation repeatedly stalls (no progress within {@value STALL_TICKS} ticks).
 */
public class ExploreGoal implements CompanionGoal {

    private static final double DEFAULT_TARGET_DISTANCE = 64.0;
    /** Radius for picking a random next waypoint. */
    private static final double WAYPOINT_RADIUS_MIN = 16.0;
    private static final double WAYPOINT_RADIUS_MAX = 32.0;
    private static final double WALK_SPEED = 1.0;
    /** Fail if the entity doesn't move at least 2 blocks within this many ticks. */
    private static final int STALL_TICKS = 20 * 20; // 20 s

    private final CompanionEntity entity;
    private boolean failed = false;
    private boolean complete = false;

    private Vec3 startPos = null;
    private double targetDistance = DEFAULT_TARGET_DISTANCE;

    // Stall detection
    private Vec3 lastPos = null;
    private int stallTicks = 0;
    private int waypointAttempts = 0;
    private static final int MAX_WAYPOINT_ATTEMPTS = 5;

    public ExploreGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false;
        complete = false;
        startPos = entity.position();
        lastPos = startPos;
        stallTicks = 0;
        waypointAttempts = 0;
        if (params.containsKey("distance")) {
            targetDistance = ((Number) params.get("distance")).doubleValue();
        }
        CompanionMod.LOGGER.info("ExploreGoal started — target distance={}", targetDistance);
        pickNextWaypoint();
    }

    @Override
    public void tick() {
        if (startPos == null) return;

        // Completion check
        if (entity.position().distanceTo(startPos) >= targetDistance) {
            complete = true;
            entity.getNavigation().stop();
            return;
        }

        // Pick new waypoint when current one is reached
        if (entity.getNavigation().isDone()) {
            if (waypointAttempts >= MAX_WAYPOINT_ATTEMPTS) {
                failed = true;
                return;
            }
            pickNextWaypoint();
        }

        // Stall detection
        if (entity.position().distanceToSqr(lastPos) < 4.0) {
            if (++stallTicks >= STALL_TICKS) {
                CompanionMod.LOGGER.warn("ExploreGoal: stalled, giving up");
                failed = true;
                return;
            }
        } else {
            stallTicks = 0;
            lastPos = entity.position();
        }
    }

    private void pickNextWaypoint() {
        Level world = entity.level();
        Random rng = new Random();

        double angle = rng.nextDouble() * 2 * Math.PI;
        double dist = WAYPOINT_RADIUS_MIN + rng.nextDouble() * (WAYPOINT_RADIUS_MAX - WAYPOINT_RADIUS_MIN);

        int wx = (int)(entity.getX() + Math.cos(angle) * dist);
        int wz = (int)(entity.getZ() + Math.sin(angle) * dist);
        int wy = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);

        // Skip waypoints whose surface is water — the companion can't swim safely
        BlockPos surface = new BlockPos(wx, wy - 1, wz);
        if (world.getFluidState(surface).is(FluidTags.WATER)) {
            waypointAttempts++;
            CompanionMod.LOGGER.debug("ExploreGoal: skipping water waypoint ({},{},{})", wx, wy, wz);
            return;
        }

        boolean started = entity.getNavigation().moveTo(wx, wy, wz, WALK_SPEED);
        waypointAttempts++;
        CompanionMod.LOGGER.debug("ExploreGoal: waypoint ({},{},{}) nav={}", wx, wy, wz, started);
    }

    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "Could not navigate — terrain blocked"; }

    @Override
    public void reset() {
        failed = false; complete = false;
        startPos = null; lastPos = null;
        stallTicks = 0; waypointAttempts = 0;
        entity.getNavigation().stop();
    }
}

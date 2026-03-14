package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

/**
 * EXPLORE — move a configurable distance from the starting position.
 *
 * <p>Complete when: companion has moved {@code targetDistance} blocks.
 * <p>Fails when: a hazard is encountered or navigation times out.
 *
 * <p>TODO (Phase 2): random-walk navigation using vanilla navigator.
 */
public class ExploreGoal implements CompanionGoal {

    private final CompanionEntity entity;
    private boolean failed = false;
    private boolean complete = false;
    private Vec3d startPos = null;
    private double targetDistance = 64.0;

    public ExploreGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false; complete = false;
        startPos = entity.getPos();
        if (params.containsKey("distance")) {
            targetDistance = ((Number) params.get("distance")).doubleValue();
        }
        CompanionMod.LOGGER.info("ExploreGoal started — target distance={}", targetDistance);
    }

    @Override
    public void tick() {
        if (startPos == null) return;
        if (entity.getPos().distanceTo(startPos) >= targetDistance) {
            complete = true;
        }
        // TODO: pick random waypoints and navigate; detect hazards
    }

    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "Hazard encountered during exploration"; }
    @Override public void reset() { failed = false; complete = false; startPos = null; }
}

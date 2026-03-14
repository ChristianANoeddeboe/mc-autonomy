package com.example.companion.behavior;

import com.example.companion.entity.CompanionEntity;

/**
 * Runs every tick <em>before</em> the {@link com.example.companion.goal.GoalExecutor}.
 *
 * <p>Evaluates each reactive behavior in priority order. If any behavior is
 * active the current goal is paused so navigation doesn't conflict; it is
 * resumed as soon as all behaviors deactivate.
 *
 * <p>Priority (highest first):
 * <ol>
 *   <li>{@link FleeBehavior}   — health critical (overrides everything)</li>
 *   <li>{@link DodgeBehavior}  — lit creeper nearby</li>
 *   <li>{@link AttackBehavior} — hostile mob in melee range</li>
 *   <li>{@link EatBehavior}    — hungry + food available (does not pause goal)</li>
 * </ol>
 */
public class ReactiveBehaviorController {

    private final CompanionEntity entity;

    private final FleeBehavior   flee;
    private final DodgeBehavior  dodge;
    private final AttackBehavior attack;
    private final EatBehavior    eat;

    private boolean goalPaused = false;

    public ReactiveBehaviorController(CompanionEntity entity) {
        this.entity = entity;
        this.flee   = new FleeBehavior(entity);
        this.dodge  = new DodgeBehavior(entity);
        this.attack = new AttackBehavior(entity);
        this.eat    = new EatBehavior(entity);
    }

    public void tick() {
        // Flee, dodge, and attack are goal-pausing: companion cannot pursue a
        // long-term goal while sprinting for its life or fighting.
        boolean pausingActive = flee.tick() || dodge.tick() || attack.tick();

        if (pausingActive && !goalPaused) {
            entity.getGoalExecutor().pause();
            goalPaused = true;
        } else if (!pausingActive && goalPaused) {
            entity.getGoalExecutor().resume();
            goalPaused = false;
        }

        // Eating does not pause the goal — the companion can eat while idle or
        // between pathfinding steps.
        eat.tick();
    }
}

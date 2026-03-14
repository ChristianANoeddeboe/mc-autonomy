package com.example.companion.behavior;

import com.example.companion.entity.CompanionEntity;

/**
 * Runs every tick <em>before</em> the {@link com.example.companion.goal.GoalExecutor}.
 *
 * <p>Evaluates each reactive behavior in priority order. If any behavior activates
 * it can pause the active goal to avoid conflicting navigation.
 *
 * <p>Priority (highest first):
 * <ol>
 *   <li>{@link FleeBehavior} — health critical</li>
 *   <li>{@link DodgeBehavior} — lit creeper nearby</li>
 *   <li>{@link AttackBehavior} — hostile mob in melee range</li>
 *   <li>{@link EatBehavior} — hunger low, food available</li>
 * </ol>
 */
public class ReactiveBehaviorController {

    private final CompanionEntity entity;

    private final FleeBehavior flee;
    private final DodgeBehavior dodge;
    private final AttackBehavior attack;
    private final EatBehavior eat;

    private boolean goalWasPaused = false;

    public ReactiveBehaviorController(CompanionEntity entity) {
        this.entity = entity;
        this.flee   = new FleeBehavior(entity);
        this.dodge  = new DodgeBehavior(entity);
        this.attack = new AttackBehavior(entity);
        this.eat    = new EatBehavior(entity);
    }

    public void tick() {
        boolean anyActive = flee.tick() || dodge.tick() || attack.tick() || eat.tick();

        if (anyActive && !goalWasPaused) {
            // TODO: entity.getGoalExecutor().pause();
            goalWasPaused = true;
        } else if (!anyActive && goalWasPaused) {
            // TODO: entity.getGoalExecutor().resume();
            goalWasPaused = false;
        }
    }
}

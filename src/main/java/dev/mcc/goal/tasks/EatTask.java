package dev.mcc.goal.tasks;

import dev.mcc.entity.CompanionEntity;
import dev.mcc.goal.GoalSpec;
import dev.mcc.goal.GoalTask;
import dev.mcc.goal.TaskStatus;
import dev.mcc.goal.GoalType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;

/** Reflexive eat: consume the best food, drain a fixed number of ticks. */
public final class EatTask implements GoalTask {
	private final GoalSpec spec = new GoalSpec(GoalType.EAT, java.util.Map.of(), "reflex-eat");
	private int ticks;

	@Override public void start(CompanionEntity e) { ticks = 0; }

	@Override public TaskStatus tick(CompanionEntity e) {
		ticks++;
		if (ticks == 20) {
			var food = e.inventory().bestFood();
			if (food.isPresent()) {
				FoodProperties fp = food.get().get(DataComponents.FOOD);
				int nutrition = fp != null ? fp.nutrition() : 1;
				e.inventory().remove(food.get().getItem(), 1);
				e.hunger().feed(nutrition);
				e.heal(nutrition * 0.1f);
				dev.mcc.MccMod.LOGGER.info("[MCC][eat] {} hunger={}", food.get().getItem(), e.hunger().get());
			}
			return TaskStatus.DONE;
		}
		return TaskStatus.RUNNING;
	}

	@Override public void stop(CompanionEntity e) {}
	@Override public String progress() { return "EAT"; }
	@Override public GoalSpec spec() { return spec; }
}
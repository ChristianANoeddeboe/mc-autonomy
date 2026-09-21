package dev.mcc.llm;

import dev.mcc.chat.ChatParser;
import dev.mcc.goal.GoalType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 6: pure fallback planner — cold. */
class FallbackPlannerTest {

	private static Map<String, Integer> have(String k, int v) { Map<String,Integer> m = new HashMap<>(); m.put(k, v); return m; }
	private static Map<String, Boolean> gatherAvailable(String... ids) {
		Map<String,Boolean> m = new HashMap<>(); for (String i : ids) m.put(i, true); return m;
	}

	@Test void giveWhenHaveEnough() {
		var plan = FallbackPlanner.plan(parse("give me 5 oak logs"),
			have("minecraft:oak_log", 7), Map.of(), false, List.of());
		assertEquals(1, plan.size());
		assertEquals(GoalType.GIVE, plan.get(0).type());
		assertEquals(5, plan.get(0).intParam("count", 0));
	}

	@Test void givePartialWhenShort() {
		var plan = FallbackPlanner.plan(parse("give me 5 oak logs"),
			have("minecraft:oak_log", 2), Map.of(), false, List.of());
		assertEquals(1, plan.size());
		assertEquals(2, plan.get(0).intParam("count", 0));
	}

	@Test void giveGatherThenGiveWhenNoneButMappable() {
		var plan = FallbackPlanner.plan(parse("give me 5 oak logs"),
			Map.of(), gatherAvailable("minecraft:oak_log"), false, List.of());
		assertEquals(2, plan.size());
		assertEquals(GoalType.GATHER, plan.get(0).type());
		assertEquals(GoalType.GIVE, plan.get(1).type());
	}

	@Test void declineWhenUnknown() {
		var plan = FallbackPlanner.plan(parse("give me 5 diamond"),
			Map.of(), Map.of(), false, List.of());
		assertEquals(1, plan.size());
		assertEquals(GoalType.DECLINE, plan.get(0).type());
	}

	@Test void gatherRequestPlansGatherThenGive() {
		var plan = FallbackPlanner.plan(parse("mine me 10 iron ore"),
			Map.of(), gatherAvailable("minecraft:iron_ore"), false, List.of());
		assertEquals(2, plan.size());
		assertEquals(GoalType.GATHER, plan.get(0).type());
		assertEquals(10, plan.get(0).intParam("count", 0));
	}

	@Test void craftRequestPlansCraftThenGive() {
		var plan = FallbackPlanner.plan(parse("craft me 4 oak planks"),
			Map.of(), Map.of(), false, List.of());
		assertEquals(2, plan.size());
		assertEquals(GoalType.CRAFT, plan.get(0).type());
	}

	@Test void modeIntents() {
		assertEquals(GoalType.FOLLOW, FallbackPlanner.plan(parse("follow me"), Map.of(), Map.of(), false, List.of()).get(0).type());
		assertEquals(GoalType.STAY, FallbackPlanner.plan(parse("stay"), Map.of(), Map.of(), false, List.of()).get(0).type());
		assertEquals(GoalType.GUARD, FallbackPlanner.plan(parse("guard here"), Map.of(), Map.of(), false, List.of()).get(0).type());
	}

	@Test void idlePlansShelterAtNightWhenNeedy() {
		var plan = FallbackPlanner.idlePlan(true, List.of("shelter"), Map.of());
		assertEquals(GoalType.SHELTER, plan.get(0).type());
	}

	@Test void idlePlansFoodWhenHungry() {
		var plan = FallbackPlanner.idlePlan(false, List.of("food"), gatherAvailable("minecraft:wheat"));
		assertEquals(GoalType.GATHER, plan.get(0).type());
	}

	private static ChatParser.Intent parse(String s) { return ChatParser.parse(s); }
}
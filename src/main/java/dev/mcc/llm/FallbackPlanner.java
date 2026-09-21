package dev.mcc.llm;

import dev.mcc.chat.ChatParser.Intent;
import dev.mcc.chat.ChatParser.IntentType;
import dev.mcc.goal.GoalSpec;
import dev.mcc.goal.GoalType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure offline/degraded planner: given an intent + a plain inventory map, emit a
 * heuristic plan. No MC classes — the caller supplies item-have counts as
 * id->count. Used when the Brain is DEGRADED/OFFLINE so the companion still acts.
 */
public final class FallbackPlanner {

	private FallbackPlanner() {}

	/**
	 * @param intent  parsed player intent
	 * @param have    map of minecraft-item-id -> count on hand
	 * @param canGather whether we know where to get the item (block maps)
	 * @param isNight needs-shelter hint
	 * @param needs  derived needs list (may contain "shelter","food")
	 */
	public static List<GoalSpec> plan(Intent intent, Map<String, Integer> have,
			Map<String, Boolean> canGather, boolean isNight, List<String> needs) {
		List<GoalSpec> out = new ArrayList<>();
		String item = intent.itemName().isEmpty() ? "" : normalizeId(intent.itemName());
		int n = intent.count();
		String block = itemToBlock(item);

		switch (intent.type()) {
			case GIVE_REQUEST -> {
				int onHand = have.getOrDefault(item, 0);
				if (onHand >= n) {
					out.add(give(item, n));
				} else if (onHand > 0) {
					out.add(give(item, onHand));
				} else if (canGather.getOrDefault(block, false) && !block.isEmpty()) {
					out.add(gather(block, n));
					out.add(give(item, n));
				} else {
					out.add(decline("I don't have " + item + " and don't know where to get it"));
				}
			}
			case GATHER_REQUEST -> {
				if (canGather.getOrDefault(block, false)) {
					out.add(gather(block, n));
					out.add(give(item, n));
				} else {
					out.add(decline("I don't know where to find " + item));
				}
			}
			case CRAFT_REQUEST -> {
				out.add(craft(item, Math.min(n, 16)));
				out.add(give(item, Math.min(n, 16)));
			}
			case FOLLOW -> out.add(mode(GoalType.FOLLOW));
			case COME -> out.add(moveToOwner());
			case STAY -> out.add(mode(GoalType.STAY));
			case GUARD -> out.add(mode(GoalType.GUARD));
			case STOP -> { out.add(mode(GoalType.FOLLOW)); }
			case STATUS_QUERY, CONVERSATION -> { /* speak only; no goal */ }
			default -> {
				if (isNight && needs.contains("shelter")) out.add(GoalSpec.of(GoalType.SHELTER));
				else if (needs.contains("food")) out.add(gather("minecraft:wheat", 4));
				else out.add(mode(GoalType.FOLLOW));
			}
		}
		return out;
	}

	/** Night/idle provisioning plan (PERIODIC). */
	public static List<GoalSpec> idlePlan(boolean isNight, List<String> needs,
			Map<String, Boolean> canGather) {
		List<GoalSpec> out = new ArrayList<>();
		if (isNight && needs.contains("shelter")) {
			out.add(GoalSpec.of(GoalType.SHELTER));
		} else if (needs.contains("food")) {
			out.add(gather("minecraft:wheat", 4));
		} else {
			out.add(mode(GoalType.FOLLOW));
		}
		return out;
	}

	// ---- goal constructors ----------------------------------------------------

	private static GoalSpec give(String item, int n) {
		return new GoalSpec(GoalType.GIVE, Map.of("item", item, "count", Math.max(1, n)), "fallback");
	}
	private static GoalSpec gather(String block, int n) {
		return new GoalSpec(GoalType.GATHER, Map.of("block", block, "count", Math.max(1, n)), "fallback");
	}
	private static GoalSpec craft(String item, int n) {
		return new GoalSpec(GoalType.CRAFT, Map.of("item", item, "count", Math.max(1, n)), "fallback");
	}
	private static GoalSpec mode(GoalType t) { return GoalSpec.of(t); }
	private static GoalSpec moveToOwner() { return new GoalSpec(GoalType.MOVE_TO, Map.of(), "fallback-come"); }
	private static GoalSpec decline(String reason) { return new GoalSpec(GoalType.DECLINE, Map.of("reason", reason), "fallback"); }

	static String normalizeId(String phrase) {
		return phrase.contains(":") ? phrase : "minecraft:" + phrase;
	}

	/** crude item->block guess used only when we lack a resolver; caller overrides via canGather. */
	static String itemToBlock(String id) {
		// bare pass-through: caller decides gather-ability; we just suggest the same id as block
		return id;
	}
}
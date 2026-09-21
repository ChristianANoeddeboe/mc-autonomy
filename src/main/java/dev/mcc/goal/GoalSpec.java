package dev.mcc.goal;

import java.util.Map;

/** Closed-schema goal spec produced by the planner/LLM. */
public record GoalSpec(GoalType type, Map<String, Object> params, String reason) {

	public static GoalSpec of(GoalType type) {
		return new GoalSpec(type, Map.of(), "");
	}

	public static GoalSpec of(GoalType type, String reason) {
		return new GoalSpec(type, Map.of(), reason);
	}

	public int intParam(String key, int fallback) {
		Object v = params.get(key);
		return v instanceof Number n ? n.intValue() : fallback;
	}

	public double doubleParam(String key, double fallback) {
		Object v = params.get(key);
		return v instanceof Number n ? n.doubleValue() : fallback;
	}

	public String strParam(String key, String fallback) {
		Object v = params.get(key);
		return v != null ? v.toString() : fallback;
	}
}
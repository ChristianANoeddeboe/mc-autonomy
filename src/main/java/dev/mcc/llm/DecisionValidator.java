package dev.mcc.llm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mcc.goal.GoalSpec;
import dev.mcc.goal.GoalType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pure validation of the LLM's decision JSON: strips fences/finds the JSON,
 * parses the {"say","plan":[...]} envelope, validates every goal element against
 * a closed schema, clamps numbers, drops invalid elements with warnings. Never
 * throws. MC lookups (item/block resolution) are injected via Resolver so this
 * runs cold in tests.
 */
public final class DecisionValidator {

	public record Validated(List<GoalSpec> plan, String say, List<String> warnings) {
		public boolean empty() { return plan.isEmpty() && say.isEmpty(); }
	}

	/** Item/block resolution injected so the validator stays pure. */
	public interface Resolver {
		boolean itemExists(String id);
		boolean blockExists(String id);
		boolean tagExists(String id);
	}

	public record Context(Resolver resolver, int maxPlanLength) {
		public static Context of(Resolver r) { return new Context(r, 4); }
	}

	private DecisionValidator() {}

	public static Optional<Validated> validate(String rawLlm, Context ctx) {
		List<String> warnings = new ArrayList<>();
		String json = extractJson(rawLlm);
		if (json.isEmpty()) return Optional.empty();

		JsonObject root;
		try {
			root = JsonParser.parseString(json).getAsJsonObject();
		} catch (Exception e) {
			return Optional.empty();
		}

		String say = "";
		if (root.has("say") && root.get("say").isJsonPrimitive()) {
			say = root.getAsJsonPrimitive("say").getAsString();
			if (say.length() > 300) { warnings.add("say-truncate"); say = say.substring(0, 300); }
		}

		List<GoalSpec> rawPlan = new ArrayList<>();
		if (root.has("plan") && root.get("plan").isJsonArray()) {
			for (var element : root.getAsJsonArray("plan")) {
				if (rawPlan.size() >= ctx.maxPlanLength()) { warnings.add("plan-truncated"); break; }
				if (!element.isJsonObject()) { warnings.add("element-not-object"); continue; }
				validateElement(element.getAsJsonObject(), ctx, rawPlan, warnings);
			}
		}

		if (rawPlan.isEmpty() && say.isEmpty() && warnings.isEmpty()) return Optional.empty();

		// DECLINE must be the first element (or absent). A non-first decline is
		// dropped with a warning; the preceding goals stand ("remainder kept").
		List<GoalSpec> plan = new ArrayList<>();
		for (int i = 0; i < rawPlan.size(); i++) {
			GoalSpec spec = rawPlan.get(i);
			if (spec.type() == GoalType.DECLINE) {
				if (i == 0) { plan.add(spec); break; } // nothing may follow a decline
				warnings.add("decline-not-first");
				break; // drop the decline and everything after
			}
			plan.add(spec);
		}

		return Optional.of(new Validated(plan, say, warnings));
	}

	private static void validateElement(JsonObject e, Context ctx, List<GoalSpec> out, List<String> warnings) {
		if (!e.has("type") || !e.get("type").isJsonPrimitive()) { warnings.add("type-missing"); return; }
		String typeName = e.getAsJsonPrimitive("type").getAsString();
		GoalType type;
		try {
			type = GoalType.valueOf(typeName.toUpperCase());
		} catch (IllegalArgumentException ex) {
			warnings.add("unknown-type:" + typeName);
			return;
		}

		JsonObject params = (e.has("params") && e.get("params").isJsonObject())
			? e.getAsJsonObject("params") : new JsonObject();
		Map<String, Object> m = new LinkedHashMap<>();
		List<String> local = new ArrayList<>();

		switch (type) {
			case MOVE_TO -> {
				Integer x = num(params, "x", local), y = num(params, "y", local), z = num(params, "z", local);
				if (x == null || y == null || z == null) { warnings.addAll(local); return; }
				m.put("x", x); m.put("y", y); m.put("z", z);
			}
			case GATHER -> {
				String ref = str(params, "block", local);
				Integer count = countParam(params, "count", local, 64);
				if (ref == null || count == null) { warnings.addAll(local); return; }
				if (!ctx.resolver().blockExists(ref) && !ctx.resolver().tagExists(ref)) {
					warnings.add("gather-ref-unknown:" + ref); return;
				}
				m.put("block", ref); m.put("count", count);
			}
			case GIVE -> {
				String item = str(params, "item", local);
				Integer count = countParam(params, "count", local, 64);
				if (item == null || count == null) { warnings.addAll(local); return; }
				if (!ctx.resolver().itemExists(item)) { warnings.add("give-item-unknown:" + item); return; }
				m.put("item", item); m.put("count", count);
				if (params.has("target") && params.get("target").isJsonPrimitive()) {
					String t = params.getAsJsonPrimitive("target").getAsString();
					if (t.equals("chest") || t.equals("player")) m.put("target", t);
				}
			}
			case CRAFT -> {
				String item = str(params, "item", local);
				Integer count = countParam(params, "count", local, 16);
				if (item == null || count == null) { warnings.addAll(local); return; }
				if (!ctx.resolver().itemExists(item)) { warnings.add("craft-item-unknown:" + item); return; }
				m.put("item", item); m.put("count", count);
			}
			case FOLLOW, STAY, GUARD -> {
				// optional anchor
				Integer x = num(params, "x", local), y = num(params, "y", local), z = num(params, "z", local);
				if (x != null && y != null && z != null) put(m, "x", x, "y", y, "z", z);
			}
			case SHELTER -> { /* no params required */ }
			case WAIT -> {
				Integer ticks = num(params, "ticks", local);
				if (ticks == null) { warnings.addAll(local); return; }
				m.put("ticks", clamp(ticks, 20, 1200));
			}
			case DECLINE -> {
				String reason = str(params, "reason", local);
				if (reason == null || reason.length() > 200) { warnings.add("decline-reason-invalid"); return; }
				m.put("reason", reason);
			}
			// deterministic-only types shouldn't come from the LLM
			case ATTACK, EAT, FLEE, SCOUT, EXPLORE, RETURN_TO_PLAYER, USE_ITEM -> { return; }
		}
		out.add(new GoalSpec(type, m, "llm"));
	}

	// ---- helpers ------------------------------------------------------------

	private static void put(Map<String, Object> m, Object... kv) {
		for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
	}

	private static Integer num(JsonObject o, String k, List<String> w) {
		if (!o.has(k) || !o.get(k).isJsonPrimitive()) { w.add(k + "-missing-or-invalid"); return null; }
		try {
			return o.getAsJsonPrimitive(k).getAsInt();
		} catch (Exception e) { w.add(k + "-not-int"); return null; }
	}

	private static Integer countParam(JsonObject o, String k, List<String> w, int max) {
		Integer c = num(o, k, w);
		return c == null ? null : clamp(c, 1, max);
	}

	private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

	private static String str(JsonObject o, String k, List<String> w) {
		if (!o.has(k) || !o.get(k).isJsonPrimitive()) { w.add(k + "-missing"); return null; }
		return o.getAsJsonPrimitive(k).getAsString();
	}

	static String extractJson(String raw) {
		if (raw == null) return "";
		int s = raw.indexOf('{');
		int e = raw.lastIndexOf('}');
		return (s >= 0 && e > s) ? raw.substring(s, e + 1) : "";
	}
}
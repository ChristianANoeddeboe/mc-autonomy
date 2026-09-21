package dev.mcc.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mcc.chat.ChatParser.Intent;
import dev.mcc.config.CompanionConfig;
import dev.mcc.entity.CompanionEntity;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;

/**
 * Builds the flat JSON payload sent to the LLM planner. Hand-built with
 * addProperty/add/addProperty etc — no toJson on any game object, so no
 * leakage of MC structures into the wire. Overlong payloads are truncated by
 * chopping the nearbyBlocks array (drop lowest-signal data first), never by
 * wrapping.
 *
 * The entity->record reduction is intentionally simple; the payload schema is
 * what tests pin (WorldStateSchemaTest) so it stays honest.
 */
public final class WorldStateBuilder {
	private WorldStateBuilder() {}

	public static JsonObject build(CompanionEntity e, String trigger, Intent intent) {
		CompanionConfig cfg = CompanionConfig.get();
		ServerLevel level = (ServerLevel) e.level();

		JsonObject o = new JsonObject();
		o.addProperty("trigger", trigger);
		o.addProperty("tick", e.tickCount);
		o.addProperty("pos", blockPos(e));
		o.addProperty("health", (int) e.getHealth());
		o.addProperty("maxHealth", (int) e.getMaxHealth());
		o.addProperty("hunger", e.hunger().get());
		o.addProperty("mode", e.mode().toString());
		o.addProperty("llmState", "CONNECTED");
		// 1.21.11 has no Level.isDay(); derive from day time vs config night window
		long dayTime = level.getDayTime() % 24000;
		boolean night = dayTime < cfg.sleep.nightStart || dayTime > cfg.sleep.nightEnd;
		o.addProperty("timeOfDay", night ? "night" : "day");
		o.addProperty("inventoryFree", e.inventory().freeSlots());
		o.addProperty("foodUnits", e.inventory().foodUnits());

		o.add("inventory", inventory(e));
		o.add("nearbyBlocks", nearbyBlocks(e, level));
		o.add("nearbyEntities", nearbyEntities(e, level));

		e.owner().ifPresent(own -> {
			JsonObject owner = new JsonObject();
			owner.addProperty("name", own.name());
			owner.addProperty("dist", (int) e.position().distanceTo(own.pos()));
			o.add("owner", owner);
		});

		e.executor().current().ifPresent(t -> {
			JsonObject g = new JsonObject();
			g.addProperty("type", t.spec().type().toString());
			g.addProperty("progress", t.progress());
			o.add("currentGoal", g);
		});
		JsonArray queue = new JsonArray();
		e.executor().queue().forEach(s -> queue.add(s.type().toString()));
		o.add("queue", queue);

		e.executor().lastResult().ifPresent(r -> {
			JsonObject lr = new JsonObject();
			lr.addProperty("type", r.spec().type().toString());
			lr.addProperty("status", r.status().toString());
			lr.addProperty("reason", r.reason());
			o.add("lastGoalResult", lr);
		});

		if (intent != null) {
			JsonObject lm = new JsonObject();
			JsonObject inner = new JsonObject();
			inner.addProperty("type", intent.type().toString());
			inner.addProperty("item", intent.itemName());
			inner.addProperty("count", intent.count());
			lm.add("intent", inner);
			o.add("lastMessage", lm);
		} else {
			o.add("lastMessage", new JsonObject());
		}

		truncate(o, cfg.llm.payloadMaxChars);
		return o;
	}

	private static JsonArray inventory(CompanionEntity e) {
		JsonArray arr = new JsonArray();
		Map<Item, Integer> summary = e.inventory().summary();
		summary.entrySet().stream()
			.sorted(Map.Entry.<Item, Integer>comparingByValue(java.util.Comparator.reverseOrder()))
			.limit(24)
			.forEach(en -> {
				JsonObject i = new JsonObject();
				i.addProperty("item", itemId(en.getKey()));
				i.addProperty("count", en.getValue());
				arr.add(i);
			});
		return arr;
	}

	/** Aggregate non-trivial nearby blocks by type (for the model to plan gathers). */
	private static JsonArray nearbyBlocks(CompanionEntity e, ServerLevel level) {
		JsonArray arr = new JsonArray();
		int r = CompanionConfig.get().llm.payloadBlockRadius;
		java.util.Map<String, Integer> counts = new java.util.HashMap<>();
		java.util.Map<String, int[]> nearest = new java.util.HashMap<>();
		var p = e.blockPosition();
		for (net.minecraft.core.BlockPos bp : net.minecraft.core.BlockPos.betweenClosed(
				p.offset(-r, -6, -r), p.offset(r, 6, r))) {
			net.minecraft.world.level.block.state.BlockState st = level.getBlockState(bp);
			if (st.isAir()) continue;
			String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()) != null
				? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString() : "";
			if (id.isEmpty() || id.equals("minecraft:air")) continue;
			// skip common terrain noise
			if (id.equals("minecraft:dirt") || id.equals("minecraft:stone")
				|| id.equals("minecraft:grass_block") || id.equals("minecraft:bedrock")
				|| id.endsWith("deepslate")) continue;
			counts.merge(id, 1, Integer::sum);
			int[] np = nearest.get(id);
			if (np == null) { nearest.put(id, new int[]{bp.getX(), bp.getY(), bp.getZ()}); }
		}
		counts.entrySet().stream()
			.limit(16)
			.forEach(en -> {
				JsonObject j = new JsonObject();
				j.addProperty("block", en.getKey());
				j.addProperty("count", en.getValue());
				int[] np = nearest.get(en.getKey());
				j.addProperty("nearest", np[0] + "," + np[1] + "," + np[2]);
				arr.add(j);
			});
		return arr;
	}

	private static JsonArray nearbyEntities(CompanionEntity e, ServerLevel level) {
		JsonArray arr = new JsonArray();
		int r = CompanionConfig.get().llm.payloadEntityRadius;
		for (Entity ent : level.getEntitiesOfClass(Entity.class, e.getBoundingBox().inflate(r), en -> en != e)) {
			if (arr.size() >= 10) break;
			JsonObject j = new JsonObject();
			j.addProperty("type", entityId(ent));
			j.addProperty("dist", (int) e.position().distanceTo(ent.position()));
			j.addProperty("hostile", ent instanceof Monster);
			arr.add(j);
		}
		return arr;
	}

	private static void truncate(JsonObject o, int maxChars) {
		// Payload is flat JSON strings — drop the inventory tail then lastMessage if still huge.
		if (o.toString().length() <= maxChars) return;
		o.add("nearbyBlocks", new JsonArray());
		if (o.toString().length() <= maxChars) return;
		if (o.has("inventory")) {
			JsonArray inv = o.getAsJsonArray("inventory");
			while (inv.size() > 0 && o.toString().length() > maxChars) inv.remove(inv.size() - 1);
		}
	}

	private static String blockPos(CompanionEntity e) {
		var p = e.blockPosition();
		return p.getX() + "," + p.getY() + "," + p.getZ();
	}

	static String itemId(Item item) {
		var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
		return id != null ? id.toString() : item.toString();
	}

	static String entityId(Entity ent) {
		var id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(ent.getType());
		return id != null ? id.toString() : ent.getType().toString();
	}
}
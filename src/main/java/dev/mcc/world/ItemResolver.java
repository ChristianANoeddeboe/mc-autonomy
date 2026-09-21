package dev.mcc.world;

import dev.mcc.chat.ChatParser;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Resolves a normalized item phrase (or alias) to a real Item/Block via the
 * registries. MC-dependent, so not unit-tested cold; the phrase/alias mapping is
 * pure and testable via ChatParser.normalizeItem + this class's alias table.
 */
public final class ItemResolver {
	/** Phrase -> item id aliases. Defaults ship in aliases.json; loaded here. */
	private static final Map<String, String> ALIASES = new ConcurrentHashMap<>();
	static {
		ALIASES.put("wood", "minecraft:oak_log");
		ALIASES.put("iron", "minecraft:iron_ore");
		ALIASES.put("planks", "minecraft:oak_planks");
		ALIASES.put("stick", "minecraft:stick");
		ALIASES.put("food", "minecraft:bread");
		ALIASES.put("iron ore", "minecraft:iron_ore");
		ALIASES.put("bone", "minecraft:bone");
	}

	private ItemResolver() {}

	public static Map<String, String> aliases() { return Map.copyOf(ALIASES); }
	public static void setAlias(String phrase, String id) { ALIASES.put(phrase, id); }

	public static Optional<Item> item(String phrase) {
		String key = phrase == null ? "" : phrase.trim().toLowerCase();
		String id = ALIASES.getOrDefault(key, key);
		String normalized = normalizeToId(id);
		return BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(normalized));
	}

	/** Block for an item phrase: block item ids equal their block's id. */
	public static Optional<Block> block(String phrase) {
		// Fast path — resolve the id the same way item() does, then look up the Block registry.
		String key = phrase == null ? "" : phrase.trim().toLowerCase();
		String id = ALIASES.getOrDefault(key, key);
		String normalized = normalizeToId(id);
		return BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(normalized));
	}

	/** BlockState default for an item phrase, if it's a block item. */
	public static Optional<BlockState> blockState(String phrase) {
		return block(phrase).map(Block::defaultBlockState);
	}

	private static String normalizeToId(String id) {
		if (id == null || id.isEmpty()) return id;
		if (id.contains(":")) return id;
		return "minecraft:" + id;
	}

	/** Best-effort id display for a resolved item. */
	public static String idOf(Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return id != null ? id.toString() : item.toString();
	}
}
package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;
import com.example.companion.util.ScanUtils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;

import java.util.Map;

/**
 * CRAFT_ITEM — consume materials from inventory to produce a target item.
 *
 * <p>Supports a hardcoded recipe set covering the most common early-game items.
 * Recipes that require a crafting table are noted but crafted in-place here
 * (server-side shortcut — the entity is assumed to be able to find a table).
 *
 * <p>Complete when: the target item appears in inventory.
 * <p>Fails when: required materials are missing or the item is unknown.
 */
public class CraftItemGoal implements CompanionGoal {

    private record Recipe(Map<String, Integer> ingredients, String result, int count) {}

    /** All supported recipes keyed by result item registry path. */
    private static final Map<String, Recipe> RECIPES = Map.ofEntries(
            Map.entry("oak_planks",
                    new Recipe(Map.of("oak_log", 1), "oak_planks", 4)),
            Map.entry("crafting_table",
                    new Recipe(Map.of("oak_planks", 4), "crafting_table", 1)),
            Map.entry("stick",
                    new Recipe(Map.of("oak_planks", 2), "stick", 4)),
            Map.entry("torch",
                    new Recipe(Map.of("coal", 1, "stick", 1), "torch", 4)),
            Map.entry("wooden_pickaxe",
                    new Recipe(Map.of("oak_planks", 3, "stick", 2), "wooden_pickaxe", 1)),
            Map.entry("wooden_axe",
                    new Recipe(Map.of("oak_planks", 3, "stick", 2), "wooden_axe", 1)),
            Map.entry("wooden_sword",
                    new Recipe(Map.of("oak_planks", 2, "stick", 1), "wooden_sword", 1)),
            Map.entry("stone_pickaxe",
                    new Recipe(Map.of("cobblestone", 3, "stick", 2), "stone_pickaxe", 1)),
            Map.entry("stone_axe",
                    new Recipe(Map.of("cobblestone", 3, "stick", 2), "stone_axe", 1)),
            Map.entry("stone_sword",
                    new Recipe(Map.of("cobblestone", 2, "stick", 1), "stone_sword", 1)),
            Map.entry("furnace",
                    new Recipe(Map.of("cobblestone", 8), "furnace", 1)),
            Map.entry("chest",
                    new Recipe(Map.of("oak_planks", 8), "chest", 1))
    );

    private final CompanionEntity entity;
    private boolean failed = false;
    private boolean complete = false;
    private String targetItem = "wooden_pickaxe";

    public CraftItemGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false; complete = false;
        targetItem = (String) params.getOrDefault("item", "wooden_pickaxe");
        CompanionMod.LOGGER.info("CraftItemGoal started — item={}", targetItem);
    }

    @Override
    public void tick() {
        if (complete || failed) return;

        // Already in inventory?
        if (hasItem(targetItem)) {
            complete = true;
            return;
        }

        Recipe recipe = RECIPES.get(targetItem);
        if (recipe == null) {
            CompanionMod.LOGGER.warn("CraftItemGoal: no recipe for '{}'", targetItem);
            failed = true;
            return;
        }

        // Check ingredients
        for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
            int held = ScanUtils.countInInventory(entity,
                    s -> ScanUtils.itemName(s).equals(ingredient.getKey()));
            if (held < ingredient.getValue()) {
                CompanionMod.LOGGER.warn("CraftItemGoal: missing {} x{} (have {})",
                        ingredient.getKey(), ingredient.getValue(), held);
                failed = true;
                return;
            }
        }

        // Consume ingredients
        for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
            ScanUtils.consumeFromInventory(entity,
                    s -> ScanUtils.itemName(s).equals(ingredient.getKey()),
                    ingredient.getValue());
        }

        // Produce result
        Item resultItem = Registries.ITEM.getOrEmpty(
                net.minecraft.util.Identifier.of("minecraft", recipe.result()))
                .orElse(Items.AIR);

        if (resultItem == Items.AIR) {
            CompanionMod.LOGGER.error("CraftItemGoal: unknown result item '{}'", recipe.result());
            failed = true;
            return;
        }

        ItemStack output = new ItemStack(resultItem, recipe.count());
        ScanUtils.addToInventory(entity, output);
        CompanionMod.LOGGER.info("CraftItemGoal: crafted {} x{}", recipe.result(), recipe.count());
        complete = true;
    }

    private boolean hasItem(String name) {
        return ScanUtils.countInInventory(entity, s -> ScanUtils.itemName(s).equals(name)) > 0;
    }

    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "Missing materials to craft " + targetItem; }
    @Override public void reset()           { failed = false; complete = false; }
}

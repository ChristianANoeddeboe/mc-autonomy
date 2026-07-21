package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;
import com.example.companion.util.ScanUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.function.Predicate;

/**
 * CRAFT_ITEM — consume materials from inventory to produce a target item.
 *
 * <p>Ingredients use generic tag names so any wood species works:
 * <ul>
 *   <li>{@code "log"}    — any log ({@link net.minecraft.tags.ItemTags#LOGS})</li>
 *   <li>{@code "planks"} — any planks ({@link net.minecraft.tags.ItemTags#PLANKS})</li>
 *   <li>Specific names like {@code "cobblestone"}, {@code "coal"} match exactly.</li>
 * </ul>
 *
 * <p>If planks are needed but only logs are available, logs are automatically
 * converted to planks first (4 planks per log, no table required).
 *
 * <p>Complete when: the target item appears in inventory.
 * <p>Fails when: required materials are missing after auto-crafting, or item unknown.
 */
public class CraftItemGoal implements CompanionGoal {

    private record Recipe(Map<String, Integer> ingredients, String result, int resultCount) {}

    /**
     * Recipes keyed by result item name.
     * Ingredient keys: "log" and "planks" are tag-matched; everything else is exact.
     */
    private static final Map<String, Recipe> RECIPES = Map.ofEntries(
            // No-table recipes
            Map.entry("wooden_planks",    new Recipe(Map.of("log",    1), "oak_planks",    4)),
            Map.entry("oak_planks",       new Recipe(Map.of("log",    1), "oak_planks",    4)),
            Map.entry("stick",            new Recipe(Map.of("planks", 2), "stick",         4)),
            Map.entry("crafting_table",   new Recipe(Map.of("planks", 4), "crafting_table",1)),
            // Tool recipes (need crafting table, but we craft server-side)
            Map.entry("wooden_pickaxe",   new Recipe(Map.of("planks", 3, "stick", 2), "wooden_pickaxe",  1)),
            Map.entry("wooden_axe",       new Recipe(Map.of("planks", 3, "stick", 2), "wooden_axe",      1)),
            Map.entry("wooden_shovel",    new Recipe(Map.of("planks", 1, "stick", 2), "wooden_shovel",   1)),
            Map.entry("wooden_sword",     new Recipe(Map.of("planks", 2, "stick", 1), "wooden_sword",    1)),
            Map.entry("stone_pickaxe",    new Recipe(Map.of("cobblestone", 3, "stick", 2), "stone_pickaxe",   1)),
            Map.entry("stone_axe",        new Recipe(Map.of("cobblestone", 3, "stick", 2), "stone_axe",       1)),
            Map.entry("stone_shovel",     new Recipe(Map.of("cobblestone", 1, "stick", 2), "stone_shovel",    1)),
            Map.entry("stone_sword",      new Recipe(Map.of("cobblestone", 2, "stick", 1), "stone_sword",     1)),
            Map.entry("iron_pickaxe",     new Recipe(Map.of("iron_ingot", 3, "stick", 2), "iron_pickaxe",    1)),
            Map.entry("iron_axe",         new Recipe(Map.of("iron_ingot", 3, "stick", 2), "iron_axe",        1)),
            Map.entry("iron_sword",       new Recipe(Map.of("iron_ingot", 2, "stick", 1), "iron_sword",      1)),
            Map.entry("torch",            new Recipe(Map.of("coal", 1, "stick", 1), "torch",           4)),
            Map.entry("furnace",          new Recipe(Map.of("cobblestone", 8), "furnace",          1)),
            Map.entry("chest",            new Recipe(Map.of("planks", 8), "chest",           1)),
            Map.entry("bed",              new Recipe(Map.of("planks", 3, "white_wool", 3), "white_bed", 1))
    );

    private final CompanionEntity entity;
    private boolean failed   = false;
    private boolean complete = false;
    private String  targetItem = "wooden_pickaxe";
    private String  failReason = "";

    public CraftItemGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false; complete = false; failReason = "";
        targetItem = params.getOrDefault("item", "wooden_pickaxe").toString();
        CompanionMod.LOGGER.info("CraftItemGoal started — item={}", targetItem);
    }

    @Override
    public void tick() {
        if (complete || failed) return;

        if (hasItem(targetItem)) {
            complete = true;
            return;
        }

        Recipe recipe = RECIPES.get(targetItem);
        if (recipe == null) {
            failReason = "No recipe known for '" + targetItem + "'";
            CompanionMod.LOGGER.warn("CraftItemGoal: {}", failReason);
            failed = true;
            return;
        }

        // Auto-craft intermediates if needed (logs → planks, planks → sticks)
        if (!autoProvideIntermediates(recipe)) {
            failed = true;
            return;
        }

        // Final ingredient check
        for (Map.Entry<String, Integer> ing : recipe.ingredients().entrySet()) {
            int held = countIngredient(ing.getKey());
            if (held < ing.getValue()) {
                failReason = "Missing " + ing.getKey() + " x" + ing.getValue() + " (have " + held + ")";
                CompanionMod.LOGGER.warn("CraftItemGoal: {}", failReason);
                failed = true;
                return;
            }
        }

        // Consume and produce
        for (Map.Entry<String, Integer> ing : recipe.ingredients().entrySet()) {
            consumeIngredient(ing.getKey(), ing.getValue());
        }

        Item resultItem = BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath("minecraft",recipe.result()));
        if (resultItem == null || resultItem == Items.AIR) {
            failReason = "Unknown result item '" + recipe.result() + "'";
            CompanionMod.LOGGER.error("CraftItemGoal: {}", failReason);
            failed = true;
            return;
        }

        ScanUtils.addToInventory(entity, new ItemStack(resultItem, recipe.resultCount()));
        CompanionMod.LOGGER.info("CraftItemGoal: crafted {} x{}", recipe.result(), recipe.resultCount());
        complete = true;
    }

    // -----------------------------------------------------------------------
    // Intermediate crafting
    // -----------------------------------------------------------------------

    /**
     * Before executing the recipe, auto-craft any missing intermediates:
     * - If planks needed but not enough: convert available logs → planks
     * - If sticks needed but not enough: convert available planks → sticks
     *
     * @return false if there are still not enough after auto-crafting
     */
    private boolean autoProvideIntermediates(Recipe recipe) {
        // Planks needed?
        int planksNeeded = recipe.ingredients().getOrDefault("planks", 0);
        if (planksNeeded > 0) {
            int planksHeld = countIngredient("planks");
            int deficit    = planksNeeded - planksHeld;
            if (deficit > 0) {
                // Convert logs → planks (1 log = 4 planks)
                int logsNeeded = (deficit + 3) / 4;
                if (!convertLogsToOakPlanks(logsNeeded)) {
                    failReason = "Not enough logs to make planks (need " + logsNeeded + " logs)";
                    CompanionMod.LOGGER.warn("CraftItemGoal: {}", failReason);
                    return false;
                }
            }
        }

        // Sticks needed?
        int sticksNeeded = recipe.ingredients().getOrDefault("stick", 0);
        if (sticksNeeded > 0) {
            int sticksHeld = countIngredient("stick");
            int deficit    = sticksNeeded - sticksHeld;
            if (deficit > 0) {
                // Convert planks → sticks (2 planks = 4 sticks)
                int planksForSticks = ((deficit + 3) / 4) * 2;
                if (countIngredient("planks") < planksForSticks) {
                    // Try converting logs first
                    int logsNeeded = (planksForSticks + 3) / 4;
                    convertLogsToOakPlanks(logsNeeded);
                }
                if (!convertPlanksToSticks(planksForSticks)) {
                    failReason = "Not enough planks to make sticks (need " + planksForSticks + " planks)";
                    CompanionMod.LOGGER.warn("CraftItemGoal: {}", failReason);
                    return false;
                }
            }
        }

        return true;
    }

    /** Convert up to {@code logCount} logs into oak planks (4 per log). Returns true if any were converted. */
    private boolean convertLogsToOakPlanks(int logCount) {
        // Find any log type and consume them
        int converted = 0;
        for (int slot = 0; slot < entity.getCarriedInventory().getContainerSize() && converted < logCount; slot++) {
            ItemStack stack = entity.getCarriedInventory().getItem(slot);
            if (stack.isEmpty() || !stack.is(ItemTags.LOGS)) continue;
            int take = Math.min(logCount - converted, stack.getCount());
            stack.shrink(take);
            // Produce oak_planks (any log → 4 planks; we always output oak for simplicity)
            Item planks = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft","oak_planks"));
            ScanUtils.addToInventory(entity, new ItemStack(planks, take * 4));
            converted += take;
            CompanionMod.LOGGER.debug("CraftItemGoal: converted {} logs → {} oak_planks", take, take * 4);
        }
        return converted >= logCount;
    }

    /** Convert {@code plankCount} planks into sticks (2 planks = 4 sticks). Returns true if successful. */
    private boolean convertPlanksToSticks(int plankCount) {
        if (countIngredient("planks") < plankCount) return false;
        consumeIngredient("planks", plankCount);
        Item stick = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft","stick"));
        ScanUtils.addToInventory(entity, new ItemStack(stick, (plankCount / 2) * 4));
        CompanionMod.LOGGER.debug("CraftItemGoal: converted {} planks → {} sticks", plankCount, (plankCount / 2) * 4);
        return true;
    }

    // -----------------------------------------------------------------------
    // Tag-aware ingredient helpers
    // -----------------------------------------------------------------------

    /** Count inventory items matching the ingredient name (tag-aware). */
    private int countIngredient(String name) {
        return ScanUtils.countInInventory(entity, ingredientPredicate(name));
    }

    /** Consume {@code amount} items matching the ingredient name (tag-aware). */
    private void consumeIngredient(String name, int amount) {
        ScanUtils.consumeFromInventory(entity, ingredientPredicate(name), amount);
    }

    private static Predicate<ItemStack> ingredientPredicate(String name) {
        return switch (name) {
            case "log", "logs"   -> stack -> stack.is(ItemTags.LOGS);
            case "planks"        -> stack -> stack.is(ItemTags.PLANKS);
            case "wool"          -> stack -> stack.is(ItemTags.WOOL);
            default              -> stack -> ScanUtils.itemName(stack).equals(name);
        };
    }

    private boolean hasItem(String name) {
        return countIngredient(name) > 0;
    }

    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return failReason.isEmpty() ? "Missing materials for " + targetItem : failReason; }
    @Override public void reset()           { failed = false; complete = false; failReason = ""; }
}

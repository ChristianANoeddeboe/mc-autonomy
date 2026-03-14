package com.example.companion.objective;

import com.example.companion.entity.CompanionEntity;
import com.example.companion.util.ScanUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Evaluates how far the companion has progressed toward its current
 * {@link Objective} and serialises that progress into the world-state JSON.
 *
 * <p>Progress is derived entirely from the companion's inventory and the
 * world clock — no additional server queries are needed.
 */
public final class ObjectiveTracker {

    private ObjectiveTracker() {}

    /**
     * Serialise the current objective + progress into a JSON object suitable
     * for inclusion in the world-state payload sent to the sidecar.
     *
     * <pre>
     * {
     *   "type":        "BEAT_DRAGON",
     *   "display_name":"Defeat the Ender Dragon",
     *   "guidance":    "...",
     *   "milestone":   "Mine diamonds and craft diamond gear",
     *   "progress":    ["Has iron pickaxe", "Has iron sword", "Missing: diamond gear"],
     *   "complete":    false
     * }
     * </pre>
     */
    public static JsonObject serialize(CompanionEntity entity) {
        Objective obj = entity.getObjective();
        JsonObject root = new JsonObject();
        root.addProperty("type",         obj.name());
        root.addProperty("display_name", obj.displayName);
        root.addProperty("guidance",     obj.guidanceForAI);

        Progress p = evaluate(obj, entity);
        root.addProperty("milestone", p.milestone());
        root.addProperty("complete",  p.complete());

        JsonArray notes = new JsonArray();
        p.notes().forEach(notes::add);
        root.add("progress", notes);

        return root;
    }

    // ------------------------------------------------------------------
    // Evaluation
    // ------------------------------------------------------------------

    private record Progress(String milestone, List<String> notes, boolean complete) {}

    private static Progress evaluate(Objective obj, CompanionEntity entity) {
        return switch (obj) {
            case BEAT_DRAGON     -> evalBeatDragon(entity);
            case BEAT_WITHER     -> evalBeatWither(entity);
            case FULL_DIAMOND_GEAR -> evalDiamondGear(entity);
            case SURVIVE_100_DAYS  -> evalSurviveDays(entity);
            case BUILD_HOUSE       -> evalBuildHouse(entity);
            case COLLECT_ALL_ORES  -> evalAllOres(entity);
            case NONE              -> new Progress("No objective set", List.of(), false);
        };
    }

    // ------------------------------------------------------------------
    // BEAT_DRAGON
    // ------------------------------------------------------------------

    private static Progress evalBeatDragon(CompanionEntity entity) {
        List<String> notes = new ArrayList<>();
        boolean hasStoneTools  = hasAny(entity, s -> name(s).contains("stone_pickaxe")
                                                   || name(s).contains("stone_axe"));
        boolean hasIronGear    = hasItem(entity, "iron_pickaxe") && hasItem(entity, "iron_sword");
        boolean hasIronArmour  = count(entity, s -> name(s).startsWith("iron_") && isArmour(s)) >= 4;
        boolean hasDiamondGear = hasItem(entity, "diamond_pickaxe") && hasItem(entity, "diamond_sword");
        boolean hasDiamondArm  = count(entity, s -> name(s).startsWith("diamond_") && isArmour(s)) >= 4;
        boolean hasEyesOfEnder = ScanUtils.countInInventory(entity, s -> name(s).equals("ender_eye")) >= 3;
        boolean hasBlazePowder = hasItem(entity, "blaze_powder");
        boolean hasEnderPearls = ScanUtils.countInInventory(entity, s -> name(s).equals("ender_pearl")) >= 3;

        checklist(notes, hasStoneTools,  "✓ Stone tools", "✗ Stone tools");
        checklist(notes, hasIronGear,    "✓ Iron tools",  "✗ Iron tools");
        checklist(notes, hasIronArmour,  "✓ Iron armour", "✗ Iron armour");
        checklist(notes, hasDiamondGear, "✓ Diamond tools","✗ Diamond tools");
        checklist(notes, hasDiamondArm,  "✓ Diamond armour","✗ Diamond armour");
        checklist(notes, hasBlazePowder, "✓ Blaze powder","✗ Blaze powder (Nether)");
        checklist(notes, hasEnderPearls, "✓ Ender pearls","✗ Ender pearls");
        checklist(notes, hasEyesOfEnder, "✓ Eyes of Ender","✗ Eyes of Ender");

        String milestone;
        if (!hasStoneTools)       milestone = "Craft stone tools";
        else if (!hasIronGear)    milestone = "Mine iron and craft iron tools";
        else if (!hasIronArmour)  milestone = "Craft iron armour";
        else if (!hasDiamondGear) milestone = "Mine diamonds and craft diamond tools";
        else if (!hasDiamondArm)  milestone = "Craft diamond armour";
        else if (!hasBlazePowder) milestone = "Enter the Nether and collect blaze rods";
        else if (!hasEnderPearls) milestone = "Hunt Endermen for ender pearls";
        else if (!hasEyesOfEnder) milestone = "Craft Eyes of Ender from blaze powder + ender pearls";
        else                      milestone = "Find the stronghold and enter the End";

        return new Progress(milestone, notes, false); // completion requires end-game detection
    }

    // ------------------------------------------------------------------
    // BEAT_WITHER
    // ------------------------------------------------------------------

    private static Progress evalBeatWither(CompanionEntity entity) {
        List<String> notes = new ArrayList<>();
        boolean hasDiamondGear = hasItem(entity, "diamond_pickaxe") && hasItem(entity, "diamond_sword");
        boolean hasDiamondArm  = count(entity, s -> name(s).startsWith("diamond_") && isArmour(s)) >= 4;
        int skulls     = ScanUtils.countInInventory(entity, s -> name(s).equals("wither_skeleton_skull"));
        int soulSand   = ScanUtils.countInInventory(entity, s -> name(s).equals("soul_sand"));

        checklist(notes, hasDiamondGear, "✓ Diamond tools", "✗ Diamond tools");
        checklist(notes, hasDiamondArm,  "✓ Diamond armour","✗ Diamond armour");
        notes.add("Wither skulls: " + skulls + "/3");
        notes.add("Soul sand:     " + soulSand + "/4");

        String milestone;
        if (!hasDiamondGear)    milestone = "Craft diamond gear";
        else if (!hasDiamondArm)milestone = "Craft diamond armour";
        else if (skulls < 3)    milestone = "Collect " + (3 - skulls) + " more Wither Skeleton skulls";
        else if (soulSand < 4)  milestone = "Collect " + (4 - soulSand) + " more soul sand";
        else                    milestone = "Place soul sand + skulls to summon and defeat the Wither";

        return new Progress(milestone, notes, skulls >= 3 && soulSand >= 4 && hasDiamondArm);
    }

    // ------------------------------------------------------------------
    // FULL_DIAMOND_GEAR
    // ------------------------------------------------------------------

    private static final Set<String> DIAMOND_SET = Set.of(
            "diamond_sword", "diamond_pickaxe", "diamond_axe", "diamond_shovel",
            "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots");

    private static Progress evalDiamondGear(CompanionEntity entity) {
        List<String> notes = new ArrayList<>();
        int found = 0;
        for (String item : DIAMOND_SET) {
            boolean has = hasItem(entity, item);
            checklist(notes, has, "✓ " + item, "✗ " + item);
            if (has) found++;
        }
        boolean complete = (found == DIAMOND_SET.size());
        int diamonds = ScanUtils.countInInventory(entity, s -> name(s).equals("diamond"));
        notes.add("Raw diamonds: " + diamonds);

        String milestone = complete
                ? "Full diamond set obtained!"
                : "Collect diamonds and craft missing pieces (" + found + "/" + DIAMOND_SET.size() + ")";
        return new Progress(milestone, notes, complete);
    }

    // ------------------------------------------------------------------
    // SURVIVE_100_DAYS
    // ------------------------------------------------------------------

    private static Progress evalSurviveDays(CompanionEntity entity) {
        long totalTicks = entity.getWorld().getTimeOfDay();
        int days = (int)(totalTicks / 24000L);
        boolean complete = days >= 100;
        List<String> notes = List.of("Days survived: " + days + "/100");
        String milestone = complete
                ? "100 days survived!"
                : (100 - days) + " more days to go — keep surviving";
        return new Progress(milestone, notes, complete);
    }

    // ------------------------------------------------------------------
    // BUILD_HOUSE
    // ------------------------------------------------------------------

    private static Progress evalBuildHouse(CompanionEntity entity) {
        // Proxy: check if companion has the materials needed for a furnished house
        List<String> notes = new ArrayList<>();
        boolean hasBed        = hasItem(entity, "white_bed") || hasItem(entity, "red_bed")
                             || hasAny(entity, s -> name(s).endsWith("_bed"));
        boolean hasCraftTable  = hasItem(entity, "crafting_table");
        boolean hasFurnace     = hasItem(entity, "furnace");
        boolean hasChest       = hasItem(entity, "chest");
        boolean hasWoodPlanks  = ScanUtils.countInInventory(entity,
                s -> name(s).endsWith("_planks")) >= 20;
        boolean hasGlass       = hasItem(entity, "glass");

        checklist(notes, hasWoodPlanks,  "✓ Planks (20+)",    "✗ Need 20+ wood planks");
        checklist(notes, hasCraftTable,  "✓ Crafting table",  "✗ Crafting table");
        checklist(notes, hasFurnace,     "✓ Furnace",         "✗ Furnace");
        checklist(notes, hasChest,       "✓ Chest",           "✗ Chest");
        checklist(notes, hasBed,         "✓ Bed",             "✗ Bed (needs wool + planks)");
        checklist(notes, hasGlass,       "✓ Glass for windows","✗ Glass (smelt sand)");

        boolean allReady = hasCraftTable && hasFurnace && hasChest && hasBed && hasWoodPlanks;
        String milestone = allReady
                ? "Place all furniture and complete the house structure"
                : "Gather missing house materials";

        return new Progress(milestone, notes, false); // placement check needs world scan
    }

    // ------------------------------------------------------------------
    // COLLECT_ALL_ORES
    // ------------------------------------------------------------------

    private static final Set<String> ALL_ORE_ITEMS = Set.of(
            "coal", "raw_iron", "iron_ingot", "raw_copper", "copper_ingot",
            "gold_ingot", "raw_gold", "redstone", "lapis_lazuli", "emerald", "diamond");

    private static Progress evalAllOres(CompanionEntity entity) {
        List<String> notes = new ArrayList<>();
        int found = 0;
        for (String item : ALL_ORE_ITEMS) {
            boolean has = ScanUtils.countInInventory(entity, s -> name(s).equals(item)) > 0;
            checklist(notes, has, "✓ " + item, "✗ " + item);
            if (has) found++;
        }
        boolean complete = (found == ALL_ORE_ITEMS.size());
        String milestone = complete
                ? "All ore types collected!"
                : found + "/" + ALL_ORE_ITEMS.size() + " ores — keep mining different depths";
        return new Progress(milestone, notes, complete);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String name(ItemStack s) {
        return ScanUtils.itemName(s);
    }

    private static boolean hasItem(CompanionEntity entity, String itemName) {
        return ScanUtils.countInInventory(entity, s -> name(s).equals(itemName)) > 0;
    }

    private static boolean hasAny(CompanionEntity entity, Predicate<ItemStack> pred) {
        return ScanUtils.countInInventory(entity, pred) > 0;
    }

    private static int count(CompanionEntity entity, Predicate<ItemStack> pred) {
        return (int) entity.getNonEmptyStacks().stream().filter(pred).count();
    }

    private static boolean isArmour(ItemStack s) {
        String n = name(s);
        return n.endsWith("_helmet") || n.endsWith("_chestplate")
            || n.endsWith("_leggings") || n.endsWith("_boots");
    }

    private static void checklist(List<String> notes, boolean condition,
                                   String pass, String fail) {
        notes.add(condition ? pass : fail);
    }
}

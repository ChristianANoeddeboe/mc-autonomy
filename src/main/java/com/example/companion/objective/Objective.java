package com.example.companion.objective;

/**
 * Long-term objectives the companion can pursue.
 *
 * <p>An objective sits above the short-term {@link com.example.companion.goal.GoalType}
 * layer. It is included in every sidecar request so the AI can bias its
 * goal selection toward advancing the objective while still handling
 * immediate survival needs.
 */
public enum Objective {

    NONE(
            "No specific objective",
            "Free exploration and survival — do whatever seems most useful."
    ),

    BEAT_DRAGON(
            "Defeat the Ender Dragon",
            "Progress through the tech tree: wood → stone → iron → diamond gear, " +
            "then craft Eyes of Ender, locate the stronghold, and defeat the Dragon."
    ),

    BEAT_WITHER(
            "Defeat the Wither",
            "Gear up to at least diamond armour, then enter the Nether to collect " +
            "3 Wither skulls from Wither Skeletons and 4 soul sand. Summon and defeat the Wither."
    ),

    FULL_DIAMOND_GEAR(
            "Obtain a full set of diamond armour and tools",
            "Mine enough diamonds (≥24) to craft a diamond sword, pickaxe, axe, " +
            "shovel, helmet, chestplate, leggings, and boots."
    ),

    SURVIVE_100_DAYS(
            "Survive 100 in-game days",
            "Stay alive and self-sufficient for 100 Minecraft days (~33 real minutes). " +
            "Prioritise shelter, food, and basic defences."
    ),

    BUILD_HOUSE(
            "Build a fully-furnished house",
            "Construct a house with at least: a bed, a crafting table, a furnace, " +
            "a chest, proper walls/roof, and a door."
    ),

    COLLECT_ALL_ORES(
            "Mine at least one of every ore type",
            "Obtain coal, iron, copper, gold, redstone, lapis lazuli, emerald, and diamond."
    );

    // ------------------------------------------------------------------

    public final String displayName;
    public final String guidanceForAI;

    Objective(String displayName, String guidanceForAI) {
        this.displayName    = displayName;
        this.guidanceForAI  = guidanceForAI;
    }

    public static Objective fromString(String value) {
        if (value == null) return NONE;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}

package com.example.companion.goal;

/**
 * Enumeration of all high-level goals the companion can pursue.
 * Must stay in sync with the Python sidecar's GoalType enum.
 */
public enum GoalType {
    GATHER_ITEM,
    GATHER_WOOD,    // kept for backward compatibility — maps to GatherItemGoal(type=log)
    FIND_FOOD,
    BUILD_SHELTER,
    MINE_RESOURCES,
    EXPLORE,
    FOLLOW_PLAYER,
    CRAFT_ITEM,
    SLEEP,
    NAVIGATE_TO,
    IDLE;

    /** Case-insensitive parse with IDLE as fallback. */
    public static GoalType fromString(String value) {
        if (value == null) return IDLE;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return IDLE;
        }
    }
}

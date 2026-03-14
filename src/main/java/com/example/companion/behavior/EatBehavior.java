package com.example.companion.behavior;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;

import java.util.Comparator;

/**
 * Reactive behavior: consume the most nutritious food in inventory when hungry.
 *
 * <p>Trigger: hunger ≤ {@value HUNGER_THRESHOLD} AND a food item exists in inventory.
 * <p>Action: remove one food item from inventory and restore hunger.
 */
public class EatBehavior {

    private static final int    HUNGER_THRESHOLD = 8;
    /** Cooldown between eat actions — avoid eating every tick. */
    private static final int    EAT_COOLDOWN_TICKS = 40; // 2 s

    private final CompanionEntity entity;
    private int cooldownTicks = 0;

    public EatBehavior(CompanionEntity entity) {
        this.entity = entity;
    }

    /** @return true if this behavior is active */
    public boolean tick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return false;
        }

        if (entity.getHunger() > HUNGER_THRESHOLD) return false;

        ItemStack food = findBestFood();
        if (food == null) return false;

        FoodComponent fc = food.get(DataComponentTypes.FOOD);
        if (fc == null) return false;

        int restored = fc.nutrition();
        food.decrement(1);
        entity.restoreHunger(restored);
        cooldownTicks = EAT_COOLDOWN_TICKS;
        CompanionMod.LOGGER.info("EatBehavior: ate {} (+{} hunger), hunger now {}",
                food.getItem().toString(), restored, entity.getHunger());
        return true;
    }

    /**
     * Pick the food item with the highest nutrition value.
     * Returns the live {@link ItemStack} reference (not a copy) so callers
     * can decrement it directly.
     */
    private ItemStack findBestFood() {
        return entity.getNonEmptyStacks().stream()
                .filter(s -> s.get(DataComponentTypes.FOOD) != null)
                .max(Comparator.comparingInt(s -> {
                    FoodComponent fc = s.get(DataComponentTypes.FOOD);
                    return fc == null ? 0 : fc.nutrition();
                }))
                .orElse(null);
    }
}

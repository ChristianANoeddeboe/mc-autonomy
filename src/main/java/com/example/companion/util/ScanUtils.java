package com.example.companion.util;

import com.example.companion.entity.CompanionEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Predicate;

/**
 * Shared scanning and inventory utilities used by goal implementations.
 */
public final class ScanUtils {

    private ScanUtils() {}

    // ------------------------------------------------------------------
    // Block scanning
    // ------------------------------------------------------------------

    /**
     * Find the nearest block matching {@code predicate} within a cube of
     * {@code radius} blocks centred on {@code entity}.
     *
     * @return the nearest matching {@link BlockPos}, or {@code null} if none found
     */
    public static BlockPos findNearestBlock(CompanionEntity entity, int radius,
                                             Predicate<BlockState> predicate) {
        World world = entity.getWorld();
        BlockPos origin = entity.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = world.getBlockState(mutable);
                    if (predicate.test(state)) {
                        double dist = origin.getSquaredDistance(mutable);
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = mutable.toImmutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    /** True if the entity is within {@code reach} blocks of {@code pos}. */
    public static boolean isReachable(CompanionEntity entity, BlockPos pos, double reach) {
        return entity.getBlockPos().isWithinDistance(pos, reach);
    }

    // ------------------------------------------------------------------
    // Inventory queries
    // ------------------------------------------------------------------

    /**
     * Count how many items in the companion's inventory satisfy {@code predicate}.
     */
    public static int countInInventory(CompanionEntity entity, Predicate<ItemStack> predicate) {
        int count = 0;
        for (ItemStack stack : entity.getNonEmptyStacks()) {
            if (predicate.test(stack)) count += stack.getCount();
        }
        return count;
    }

    /**
     * Return the first non-empty {@link ItemStack} matching {@code predicate},
     * or {@link ItemStack#EMPTY} if none.
     */
    public static ItemStack findInInventory(CompanionEntity entity, Predicate<ItemStack> predicate) {
        for (ItemStack stack : entity.getNonEmptyStacks()) {
            if (predicate.test(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Remove {@code amount} items matching {@code predicate} from the inventory.
     *
     * @return true if the full amount was available and removed
     */
    public static boolean consumeFromInventory(CompanionEntity entity,
                                               Predicate<ItemStack> predicate, int amount) {
        int remaining = amount;
        for (int i = 0; i < entity.getCarriedInventory().size() && remaining > 0; i++) {
            ItemStack stack = entity.getCarriedInventory().getStack(i);
            if (stack.isEmpty() || !predicate.test(stack)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.decrement(take);
            remaining -= take;
        }
        return remaining == 0;
    }

    /**
     * Add {@code stack} to the first available slot in the companion's inventory.
     *
     * @return true if the item was fully added
     */
    public static boolean addToInventory(CompanionEntity entity, ItemStack stack) {
        for (int i = 0; i < entity.getCarriedInventory().size(); i++) {
            ItemStack slot = entity.getCarriedInventory().getStack(i);
            if (slot.isEmpty()) {
                entity.getCarriedInventory().setStack(i, stack.copy());
                return true;
            }
            if (ItemStack.areItemsAndComponentsEqual(slot, stack)) {
                int space = slot.getMaxCount() - slot.getCount();
                if (space > 0) {
                    int take = Math.min(space, stack.getCount());
                    slot.increment(take);
                    stack.decrement(take);
                    if (stack.isEmpty()) return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Item entity collection
    // ------------------------------------------------------------------

    /**
     * Pick up nearby {@link ItemEntity} objects within {@code radius} blocks,
     * adding them to the companion's inventory.
     *
     * @return number of stacks collected
     */
    public static int collectNearbyItems(CompanionEntity entity, double radius) {
        if (!(entity.getWorld() instanceof ServerWorld)) return 0;
        Box box = entity.getBoundingBox().expand(radius);
        List<ItemEntity> items = entity.getWorld().getEntitiesByClass(
                ItemEntity.class, box, ie -> !ie.isRemoved());
        int collected = 0;
        for (ItemEntity ie : items) {
            if (addToInventory(entity, ie.getStack())) {
                ie.discard();
                collected++;
            }
        }
        return collected;
    }

    // ------------------------------------------------------------------
    // Block name helper
    // ------------------------------------------------------------------

    public static String blockName(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).getPath();
    }

    public static String itemName(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).getPath();
    }
}

package com.example.companion.util;

import com.example.companion.entity.CompanionEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;

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
        Level world = entity.level();
        BlockPos origin = entity.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = world.getBlockState(mutable);
                    if (predicate.test(state)) {
                        double dist = origin.distSqr(mutable);
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = mutable.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    /** True if the entity is within {@code reach} blocks of {@code pos}. */
    public static boolean isReachable(CompanionEntity entity, BlockPos pos, double reach) {
        return entity.blockPosition().closerThan(pos, reach);
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
        for (int i = 0; i < entity.getCarriedInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = entity.getCarriedInventory().getItem(i);
            if (stack.isEmpty() || !predicate.test(stack)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
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
        for (int i = 0; i < entity.getCarriedInventory().getContainerSize(); i++) {
            ItemStack slot = entity.getCarriedInventory().getItem(i);
            if (slot.isEmpty()) {
                entity.getCarriedInventory().setItem(i, stack.copy());
                return true;
            }
            if (ItemStack.isSameItemSameComponents(slot, stack)) {
                int space = slot.getMaxStackSize() - slot.getCount();
                if (space > 0) {
                    int take = Math.min(space, stack.getCount());
                    slot.grow(take);
                    stack.shrink(take);
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
        if (!(entity.level() instanceof ServerLevel)) return 0;
        AABB box = entity.getBoundingBox().inflate(radius);
        List<ItemEntity> items = entity.level().getEntitiesOfClass(
                ItemEntity.class, box, ie -> !ie.isRemoved());
        int collected = 0;
        for (ItemEntity ie : items) {
            if (addToInventory(entity, ie.getItem())) {
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
        String desc = state.getBlock().getDescriptionId();
        int dot = desc.lastIndexOf('.');
        return dot >= 0 ? desc.substring(dot + 1) : desc;
    }

    public static String itemName(ItemStack stack) {
        String desc = stack.getItem().getDescriptionId();
        int dot = desc.lastIndexOf('.');
        return dot >= 0 ? desc.substring(dot + 1) : desc;
    }
}

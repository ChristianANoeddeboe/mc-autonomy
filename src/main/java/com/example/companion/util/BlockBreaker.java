package com.example.companion.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Multi-tick block breaking with crack animations and correct tool drops.
 *
 * <p>Each tick call {@link #tick} while targeting a block. When progress
 * reaches 1.0 the block is removed and {@link Block#dropResources} is called
 * with the supplied tool so drops match what a player holding that item would
 * receive (e.g. iron ore needs a pickaxe to drop raw iron).
 *
 * <p>Call {@link #clear} when the entity stops targeting the block so the
 * crack overlay is removed.
 */
public final class BlockBreaker {

    private BlockPos breaking = null;
    private float   progress  = 0.0f;

    /**
     * Advance breaking by one tick.
     *
     * @param breakerId entity ID for the crack animation packet
     * @param sw        server level
     * @param pos       target block
     * @param tool      item currently held (affects speed and drops)
     * @param breaker   entity that broke the block (used for looting / XP)
     * @return {@code true} if the block was destroyed this tick
     */
    public boolean tick(int breakerId, ServerLevel sw, BlockPos pos,
                        ItemStack tool, Entity breaker) {
        BlockState state = sw.getBlockState(pos);
        if (state.isAir()) {
            clear(breakerId, sw);
            return true; // already gone
        }

        float hardness = state.getDestroySpeed(sw, pos);
        if (hardness < 0) return false; // unbreakable (bedrock, barriers, …)

        if (!pos.equals(breaking)) {
            clear(breakerId, sw);
            breaking = pos;
            progress  = 0.0f;
        }

        // Vanilla formula:  increment = toolSpeed / (hardness × 30)  if correct tool
        //                             = toolSpeed / (hardness × 100) otherwise
        float toolSpeed  = tool.isEmpty() ? 1.0f : tool.getDestroySpeed(state);
        boolean canHarvest = !state.requiresCorrectToolForDrops()
                || tool.isCorrectToolForDrops(state);
        progress += toolSpeed / (hardness * (canHarvest ? 30f : 100f));

        sw.destroyBlockProgress(breakerId, pos, Math.min(9, (int)(progress * 10)));

        if (progress >= 1.0f) {
            clear(breakerId, sw);
            sw.removeBlock(pos, false);
            Block.dropResources(state, sw, pos, null, breaker, tool);
            return true;
        }
        return false;
    }

    /** Clear the crack overlay and reset progress (call on goal reset / target change). */
    public void clear(int breakerId, ServerLevel sw) {
        if (breaking != null) {
            sw.destroyBlockProgress(breakerId, breaking, -1);
            breaking = null;
        }
        progress = 0.0f;
    }

    public boolean isBreaking() { return breaking != null; }

    // -----------------------------------------------------------------------
    // Static helpers
    // -----------------------------------------------------------------------

    /**
     * Find the fastest tool in the companion's inventory for breaking {@code state}.
     * Returns {@link ItemStack#EMPTY} if bare-hands is the best option.
     */
    public static ItemStack bestToolFor(Iterable<ItemStack> stacks, BlockState state) {
        ItemStack best      = ItemStack.EMPTY;
        float     bestSpeed = 1.0f;
        for (ItemStack stack : stacks) {
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best      = stack;
            }
        }
        return best;
    }
}

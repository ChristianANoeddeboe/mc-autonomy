package com.example.companion.entity;

import com.example.companion.CompanionMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Utility class that executes server-side physical actions for the companion entity.
 *
 * <p>All methods are no-ops until Phase 2/4 implementation; they log the attempted action.
 */
public final class ActionExecutor {

    private final CompanionEntity entity;

    public ActionExecutor(CompanionEntity entity) {
        this.entity = entity;
    }

    // ------------------------------------------------------------------
    // Movement
    // ------------------------------------------------------------------

    public void moveTo(double x, double y, double z, double speed) {
        // TODO (Phase 2): entity.getNavigation().startMovingTo(x, y, z, speed)
        CompanionMod.LOGGER.debug("ActionExecutor.moveTo({}, {}, {})", x, y, z);
    }

    public void stopMoving() {
        entity.getNavigation().stop();
    }

    // ------------------------------------------------------------------
    // Block interaction
    // ------------------------------------------------------------------

    public boolean breakBlock(BlockPos pos) {
        if (!(entity.getWorld() instanceof ServerWorld sw)) return false;
        CompanionMod.LOGGER.debug("ActionExecutor.breakBlock({})", pos);
        // TODO (Phase 4): simulate mining with correct tool, drop items
        return sw.breakBlock(pos, true, entity);
    }

    public boolean placeBlock(BlockPos pos, BlockState state) {
        if (!(entity.getWorld() instanceof ServerWorld sw)) return false;
        CompanionMod.LOGGER.debug("ActionExecutor.placeBlock({}, {})", pos, state);
        // TODO (Phase 4): check inventory for matching block item
        sw.setBlockState(pos, state);
        return true;
    }

    // ------------------------------------------------------------------
    // Item use
    // ------------------------------------------------------------------

    public void useItem(ItemStack stack) {
        // TODO (Phase 4): trigger item use action
        CompanionMod.LOGGER.debug("ActionExecutor.useItem({})", stack);
    }

    public void eatFood(ItemStack food) {
        // TODO (Phase 2): consume food and restore hunger
        CompanionMod.LOGGER.debug("ActionExecutor.eatFood({})", food);
    }
}

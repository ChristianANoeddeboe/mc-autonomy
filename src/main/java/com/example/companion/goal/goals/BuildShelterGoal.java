package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;
import com.example.companion.util.ScanUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Set;

/**
 * BUILD_SHELTER — construct a minimal enclosed shelter from blocks in inventory.
 *
 * <p>The shelter is a small hollow box: 4-wide × 3-tall × 4-deep exterior
 * (3×2×3 interior), with an open entrance at the front. The companion
 * navigates inside once complete.
 *
 * <pre>
 *   Top-down view (y = wall level):
 *     # # # #
 *     #     #
 *     #     #  ← entrance (x=1,2 at z=0 are open)
 *     # # # #
 * </pre>
 *
 * <p>Complete when: all blocks placed and entity is inside.
 * <p>Fails when: insufficient building materials or placement errors.
 */
public class BuildShelterGoal implements CompanionGoal {

    private static final int REQUIRED_BLOCKS = 26; // floor(6)+ceiling(6)+walls(14)
    private static final double PLACE_REACH  = 4.5;
    private static final double WALK_SPEED   = 1.0;
    private static final int    NAV_TIMEOUT  = 20 * 20;

    /** Acceptable building materials — ordered by preference. */
    private static final Set<String> BUILDING_MATERIALS = Set.of(
            "dirt", "cobblestone", "stone", "granite", "diorite", "andesite",
            "oak_planks", "spruce_planks", "birch_planks", "dark_oak_planks",
            "acacia_planks", "jungle_planks", "gravel", "sand");

    /** Shelter layout relative to the chosen site origin (feet level). */
    private static final int[][] SHELTER_OFFSETS = {
            // --- Floor (y=0) ---
            {0,0,0},{1,0,0},{2,0,0},{3,0,0},
            {0,0,1},{3,0,1},
            {0,0,2},{3,0,2},
            {0,0,3},{1,0,3},{2,0,3},{3,0,3},
            // --- Ceiling (y=2) ---
            {0,2,0},{1,2,0},{2,2,0},{3,2,0},
            {0,2,1},{3,2,1},
            {0,2,2},{3,2,2},
            {0,2,3},{1,2,3},{2,2,3},{3,2,3},
            // --- Walls (y=1) ---
            {0,1,0},{3,1,0},           // entrance pillars
            {0,1,1},{3,1,1},
            {0,1,2},{3,1,2},
            {0,1,3},{1,1,3},{2,1,3},{3,1,3}, // back wall
    };
    // Interior centre: x=1.5, y=1, z=1.5 relative to origin

    private enum State { CHECK_MATERIALS, NAVIGATE_TO_SITE, BUILDING, ENTER, DONE }

    private final CompanionEntity entity;
    private boolean failed = false;
    private boolean complete = false;

    private State state = State.CHECK_MATERIALS;
    private BlockPos siteOrigin = null;
    private int placementIndex = 0;
    private int navTicks = 0;

    public BuildShelterGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false; complete = false; state = State.CHECK_MATERIALS;
        siteOrigin = null; placementIndex = 0; navTicks = 0;
        CompanionMod.LOGGER.info("BuildShelterGoal started");
    }

    @Override
    public void tick() {
        if (!(entity.getWorld() instanceof ServerWorld sw)) return;

        switch (state) {
            case CHECK_MATERIALS -> {
                int available = ScanUtils.countInInventory(entity, this::isBuildingMaterial);
                if (available < REQUIRED_BLOCKS) {
                    CompanionMod.LOGGER.warn("BuildShelterGoal: only {} building blocks (need {})",
                            available, REQUIRED_BLOCKS);
                    failed = true;
                    return;
                }
                // Choose site 3 blocks ahead of current facing
                siteOrigin = entity.getBlockPos().offset(entity.getHorizontalFacing(), 2);
                state = State.NAVIGATE_TO_SITE;
                entity.getNavigation().startMovingTo(
                        siteOrigin.getX(), siteOrigin.getY(), siteOrigin.getZ(), WALK_SPEED);
            }
            case NAVIGATE_TO_SITE -> {
                navTicks++;
                if (navTicks > NAV_TIMEOUT) { failed = true; return; }
                if (ScanUtils.isReachable(entity, siteOrigin, PLACE_REACH)) {
                    entity.getNavigation().stop();
                    placementIndex = 0;
                    state = State.BUILDING;
                }
            }
            case BUILDING -> {
                if (placementIndex >= SHELTER_OFFSETS.length) {
                    CompanionMod.LOGGER.info("BuildShelterGoal: shelter complete, entering");
                    // Navigate inside (centre of interior)
                    BlockPos inside = siteOrigin.add(1, 1, 1);
                    entity.getNavigation().startMovingTo(
                            inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, WALK_SPEED);
                    navTicks = 0;
                    state = State.ENTER;
                    return;
                }
                // Place one block per tick
                int[] off = SHELTER_OFFSETS[placementIndex];
                BlockPos placePos = siteOrigin.add(off[0], off[1], off[2]);

                if (sw.getBlockState(placePos).isAir()) {
                    ItemStack material = findAndConsumeMaterial();
                    if (material == null) { failed = true; return; }
                    Block block = Block.getBlockFromItem(material.getItem());
                    if (block == Blocks.AIR) block = Blocks.DIRT; // fallback
                    sw.setBlockState(placePos, block.getDefaultState());
                }
                placementIndex++;
            }
            case ENTER -> {
                navTicks++;
                if (navTicks > NAV_TIMEOUT) { complete = true; return; } // good enough
                BlockPos inside = siteOrigin.add(1, 1, 1);
                if (entity.getBlockPos().isWithinDistance(inside, 2.0)) {
                    entity.getNavigation().stop();
                    complete = true;
                }
            }
            case DONE -> complete = true;
        }
    }

    private ItemStack findAndConsumeMaterial() {
        for (int i = 0; i < entity.getCarriedInventory().size(); i++) {
            ItemStack s = entity.getCarriedInventory().getStack(i);
            if (!s.isEmpty() && isBuildingMaterial(s)) {
                ItemStack result = s.copy();
                result.setCount(1);
                s.decrement(1);
                return result;
            }
        }
        return null;
    }

    private boolean isBuildingMaterial(ItemStack stack) {
        return BUILDING_MATERIALS.contains(ScanUtils.itemName(stack));
    }

    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "Insufficient building materials or couldn't navigate"; }

    @Override
    public void reset() {
        failed = false; complete = false; state = State.CHECK_MATERIALS;
        siteOrigin = null; placementIndex = 0; navTicks = 0;
        entity.getNavigation().stop();
    }
}

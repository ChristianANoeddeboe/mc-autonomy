package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;
import com.example.companion.util.ScanUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FIND_FOOD — obtain food by harvesting crops or hunting passive animals.
 *
 * <p>Priority: crops first (no combat), animals as fallback.
 * <p>Complete when: a food item appears in inventory.
 * <p>Fails when: no food source found after {@value SEARCH_TIMEOUT_TICKS} ticks.
 */
public class FindFoodGoal implements CompanionGoal {

    private static final int    SCAN_RADIUS          = 24;
    private static final double HARVEST_REACH        = 2.5;
    private static final double ANIMAL_REACH         = 2.5;
    private static final double WALK_SPEED           = 1.0;
    private static final int    SEARCH_TIMEOUT_TICKS = 20 * 60;
    private static final int    NAV_TIMEOUT_TICKS    = 20 * 30;

    /** Fully-grown crop blocks the companion can harvest. */
    private static final Set<String> HARVESTABLE_CROPS = Set.of(
            "wheat", "carrots", "potatoes", "beetroots", "sweet_berry_bush");

    private enum State { SCANNING, NAVIGATING_CROP, HARVESTING_CROP,
                          NAVIGATING_ANIMAL, HUNTING_ANIMAL, COLLECTING }

    private final CompanionEntity entity;
    private boolean failed = false;
    private boolean complete = false;

    private State state = State.SCANNING;
    private BlockPos targetCrop = null;
    private LivingEntity targetAnimal = null;
    private int ticksElapsed = 0;
    private int navTicks = 0;

    public FindFoodGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false; complete = false; state = State.SCANNING;
        targetCrop = null; targetAnimal = null;
        ticksElapsed = 0; navTicks = 0;
        CompanionMod.LOGGER.info("FindFoodGoal started");
    }

    @Override
    public void tick() {
        if (!(entity.getWorld() instanceof ServerWorld sw)) return;

        ticksElapsed++;
        if (ticksElapsed >= SEARCH_TIMEOUT_TICKS) {
            failed = true;
            return;
        }

        // Completion check: any food item in inventory?
        if (ScanUtils.countInInventory(entity,
                s -> s.get(DataComponentTypes.FOOD) != null) > 0) {
            complete = true;
            entity.getNavigation().stop();
            return;
        }

        ScanUtils.collectNearbyItems(entity, 2.0);

        switch (state) {
            case SCANNING -> scan(sw);
            case NAVIGATING_CROP -> navigateToCrop();
            case HARVESTING_CROP -> harvestCrop(sw);
            case NAVIGATING_ANIMAL -> navigateToAnimal();
            case HUNTING_ANIMAL -> huntAnimal(sw);
            case COLLECTING -> {
                ScanUtils.collectNearbyItems(entity, 3.0);
                state = State.SCANNING;
            }
        }
    }

    private void scan(ServerWorld sw) {
        navTicks = 0;
        // 1. Try crops first
        targetCrop = ScanUtils.findNearestBlock(entity, SCAN_RADIUS,
                blockState -> HARVESTABLE_CROPS.contains(ScanUtils.blockName(blockState)));
        if (targetCrop != null) {
            state = State.NAVIGATING_CROP;
            entity.getNavigation().startMovingTo(
                    targetCrop.getX(), targetCrop.getY(), targetCrop.getZ(), WALK_SPEED);
            return;
        }
        // 2. Try passive animals
        Box box = entity.getBoundingBox().expand(SCAN_RADIUS);
        List<AnimalEntity> animals = sw.getEntitiesByClass(AnimalEntity.class, box,
                a -> a.isAlive() && !a.isBaby());
        if (!animals.isEmpty()) {
            targetAnimal = animals.stream()
                    .min((a, b) -> Double.compare(entity.distanceTo(a), entity.distanceTo(b)))
                    .orElse(null);
            if (targetAnimal != null) {
                state = State.NAVIGATING_ANIMAL;
                entity.getNavigation().startMovingTo(targetAnimal, WALK_SPEED);
            }
        }
        // If neither found, keep scanning until timeout
    }

    private void navigateToCrop() {
        navTicks++;
        if (navTicks > NAV_TIMEOUT_TICKS || targetCrop == null) {
            state = State.SCANNING; return;
        }
        if (!HARVESTABLE_CROPS.contains(ScanUtils.blockName(entity.getWorld().getBlockState(targetCrop)))) {
            state = State.SCANNING; return; // someone else harvested it
        }
        if (ScanUtils.isReachable(entity, targetCrop, HARVEST_REACH)) {
            entity.getNavigation().stop();
            state = State.HARVESTING_CROP;
        }
    }

    private void harvestCrop(ServerWorld sw) {
        if (targetCrop == null) { state = State.SCANNING; return; }
        sw.breakBlock(targetCrop, true, entity);
        ScanUtils.collectNearbyItems(entity, 3.0);
        state = State.COLLECTING;
    }

    private void navigateToAnimal() {
        navTicks++;
        if (navTicks > NAV_TIMEOUT_TICKS || targetAnimal == null || !targetAnimal.isAlive()) {
            state = State.SCANNING; return;
        }
        entity.getNavigation().startMovingTo(targetAnimal, WALK_SPEED);
        if (entity.distanceTo(targetAnimal) <= ANIMAL_REACH) {
            entity.getNavigation().stop();
            state = State.HUNTING_ANIMAL;
        }
    }

    private void huntAnimal(ServerWorld sw) {
        if (targetAnimal == null || !targetAnimal.isAlive()) {
            state = State.COLLECTING; return;
        }
        entity.tryAttack(sw, targetAnimal);
        if (!targetAnimal.isAlive()) {
            ScanUtils.collectNearbyItems(entity, 4.0);
            state = State.COLLECTING;
        } else {
            state = State.NAVIGATING_ANIMAL; // keep chasing
        }
    }

    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "No food source found within range"; }

    @Override
    public void reset() {
        failed = false; complete = false; state = State.SCANNING;
        targetCrop = null; targetAnimal = null;
        ticksElapsed = 0; navTicks = 0;
        entity.getNavigation().stop();
    }
}

package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;
import com.example.companion.util.ScanUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
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

    /**
     * Entity types whose death loot table contains at least one food item.
     * Built once on first use by rolling each CREATURE-category entity's loot table.
     */
    private static Set<EntityType<?>> foodAnimalTypes = null;

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
        if (!(entity.level() instanceof ServerLevel sw)) return;

        ticksElapsed++;
        if (ticksElapsed >= SEARCH_TIMEOUT_TICKS) {
            failed = true;
            return;
        }

        // Completion check: any food item in inventory?
        if (ScanUtils.countInInventory(entity,
                s -> s.get(DataComponents.FOOD) != null) > 0) {
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

    private void scan(ServerLevel sw) {
        navTicks = 0;
        // 1. Try crops first
        targetCrop = ScanUtils.findNearestBlock(entity, SCAN_RADIUS,
                blockState -> HARVESTABLE_CROPS.contains(ScanUtils.blockName(blockState)));
        if (targetCrop != null) {
            state = State.NAVIGATING_CROP;
            entity.getNavigation().moveTo(
                    targetCrop.getX(), targetCrop.getY(), targetCrop.getZ(), WALK_SPEED);
            return;
        }
        // 2. Try passive animals
        AABB box = entity.getBoundingBox().inflate(SCAN_RADIUS);
        Set<EntityType<?>> foodTypes = getFoodAnimalTypes(sw);
        List<Animal> animals = sw.getEntitiesOfClass(Animal.class, box,
                a -> a.isAlive() && !a.isBaby() && foodTypes.contains(a.getType()));
        if (!animals.isEmpty()) {
            targetAnimal = animals.stream()
                    .min((a, b) -> Double.compare(entity.distanceTo(a), entity.distanceTo(b)))
                    .orElse(null);
            if (targetAnimal != null) {
                state = State.NAVIGATING_ANIMAL;
                entity.getNavigation().moveTo(targetAnimal, WALK_SPEED);
            }
        }
        // If neither found, keep scanning until timeout
    }

    private void navigateToCrop() {
        navTicks++;
        if (navTicks > NAV_TIMEOUT_TICKS || targetCrop == null) {
            state = State.SCANNING; return;
        }
        if (!HARVESTABLE_CROPS.contains(ScanUtils.blockName(entity.level().getBlockState(targetCrop)))) {
            state = State.SCANNING; return; // someone else harvested it
        }
        if (ScanUtils.isReachable(entity, targetCrop, HARVEST_REACH)) {
            entity.getNavigation().stop();
            state = State.HARVESTING_CROP;
        }
    }

    private void harvestCrop(ServerLevel sw) {
        if (targetCrop == null) { state = State.SCANNING; return; }
        sw.destroyBlock(targetCrop, true, entity);
        ScanUtils.collectNearbyItems(entity, 3.0);
        state = State.COLLECTING;
    }

    private void navigateToAnimal() {
        navTicks++;
        if (navTicks > NAV_TIMEOUT_TICKS || targetAnimal == null || !targetAnimal.isAlive()) {
            state = State.SCANNING; return;
        }
        entity.getNavigation().moveTo(targetAnimal, WALK_SPEED);
        if (entity.distanceTo(targetAnimal) <= ANIMAL_REACH) {
            entity.getNavigation().stop();
            state = State.HUNTING_ANIMAL;
        }
    }

    private void huntAnimal(ServerLevel sw) {
        if (targetAnimal == null || !targetAnimal.isAlive()) {
            state = State.COLLECTING; return;
        }
        entity.doHurtTarget(sw, targetAnimal);
        if (!targetAnimal.isAlive()) {
            ScanUtils.collectNearbyItems(entity, 4.0);
            state = State.COLLECTING;
        } else {
            state = State.NAVIGATING_ANIMAL; // keep chasing
        }
    }

    /**
     * Returns the cached set of entity types whose death loot can include food.
     * Built on first call by rolling each CREATURE-category entity's loot table
     * with the companion as the THIS_ENTITY proxy.
     */
    private Set<EntityType<?>> getFoodAnimalTypes(ServerLevel level) {
        if (foodAnimalTypes != null) return foodAnimalTypes;

        Set<EntityType<?>> result = new HashSet<>();

        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type.getCategory() != MobCategory.CREATURE) continue;

            ResourceKey<LootTable> lootKey = type.getDefaultLootTable().get();
            LootTable table = level.getServer().reloadableRegistries().getLootTable(lootKey);
            if (table == LootTable.EMPTY) continue;

            List<ItemStack> drops = table.getRandomItems(params);
            if (drops.stream().anyMatch(s -> s.has(DataComponents.FOOD))) {
                result.add(type);
                CompanionMod.LOGGER.debug("FindFoodGoal: {} yields food", BuiltInRegistries.ENTITY_TYPE.getKey(type));
            }
        }

        CompanionMod.LOGGER.info("FindFoodGoal: {} food-yielding animal type(s) found", result.size());
        foodAnimalTypes = result;
        return result;
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

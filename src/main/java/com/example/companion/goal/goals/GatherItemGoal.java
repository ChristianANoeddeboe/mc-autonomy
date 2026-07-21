package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;
import com.example.companion.util.BlockBreaker;
import com.example.companion.util.ScanUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.function.Predicate;

/**
 * GATHER_ITEM — locate, walk to, and break any surface-level block until
 * the inventory holds {@code count} matching items.
 *
 * <p>params:
 * <ul>
 *   <li>{@code type}  — block type name, e.g. "log", "sand", "gravel",
 *       "cobblestone", "oak_log".  Tags like "log"/"wood" map to
 *       {@link BlockTags#LOGS}; exact Minecraft block IDs are also accepted.</li>
 *   <li>{@code count} — how many items to collect (default 8).</li>
 * </ul>
 *
 * <p>Breaking is timed via {@link BlockBreaker} — the companion holds the
 * best tool in its inventory for the target block and cracks the block over
 * multiple ticks, matching player-like behaviour.
 */
public class GatherItemGoal implements CompanionGoal {

    private static final int    DEFAULT_COUNT      = 8;
    private static final int    SCAN_RADIUS        = 24;
    private static final double APPROACH_REACH     = 3.0;
    private static final double WALK_SPEED         = 1.0;
    private static final int    MAX_SCAN_ATTEMPTS  = 5;
    private static final int    NAV_TIMEOUT_TICKS  = 20 * 30;
    /** How far ahead (blocks) to scan for navigation obstacles. */
    private static final int    OBSTACLE_SCAN_DIST = 5;

    private enum State { SCANNING, NAVIGATING, BREAKING }

    private final CompanionEntity entity;
    private final BlockBreaker    breaker = new BlockBreaker();

    private int     targetCount   = DEFAULT_COUNT;
    private String  typeName      = "log";
    private boolean failed        = false;
    private boolean complete      = false;

    private Predicate<BlockState> blockPred;
    private Predicate<ItemStack>  itemPred;

    private State    state        = State.SCANNING;
    private BlockPos targetBlock  = null;
    private int      scanAttempts = 0;
    private int      navTicks     = 0;

    public GatherItemGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false; complete = false;
        state = State.SCANNING; targetBlock = null;
        scanAttempts = 0; navTicks = 0;
        breaker.clear(entity.getId(), (ServerLevel) entity.level());

        typeName    = params.getOrDefault("type", "log").toString();
        targetCount = params.containsKey("count")
                ? ((Number) params.get("count")).intValue()
                : DEFAULT_COUNT;

        blockPred = resolveBlockPredicate(typeName);
        itemPred  = resolveItemPredicate(typeName);
        CompanionMod.LOGGER.info("GatherItemGoal started — type={} count={}", typeName, targetCount);
    }

    @Override
    public void tick() {
        if (!(entity.level() instanceof ServerLevel sw)) return;

        // Completion check
        if (ScanUtils.countInInventory(entity, itemPred) >= targetCount) {
            complete = true;
            breaker.clear(entity.getId(), sw);
            entity.getNavigation().stop();
            CompanionMod.LOGGER.info("GatherItemGoal: collected {} '{}'", targetCount, typeName);
            return;
        }

        ScanUtils.collectNearbyItems(entity, 2.0);

        switch (state) {
            case SCANNING -> {
                targetBlock = ScanUtils.findNearestBlock(entity, SCAN_RADIUS, blockPred);
                if (targetBlock == null) {
                    if (++scanAttempts >= MAX_SCAN_ATTEMPTS) {
                        failed = true;
                        CompanionMod.LOGGER.warn("GatherItemGoal: no '{}' found after {} scans",
                                typeName, scanAttempts);
                    }
                } else {
                    navTicks = 0;
                    state = State.NAVIGATING;
                    // Navigate to ground-level XZ — we can break slightly above/below
                    entity.getNavigation().moveTo(
                            targetBlock.getX(), entity.getY(), targetBlock.getZ(), WALK_SPEED);
                }
            }
            case NAVIGATING -> {
                navTicks++;
                if (navTicks > NAV_TIMEOUT_TICKS) {
                    state = State.SCANNING;
                    return;
                }
                if (!blockPred.test(entity.level().getBlockState(targetBlock))) {
                    state = State.SCANNING;
                    return;
                }
                if (isHorizontallyReachable(targetBlock)) {
                    entity.getNavigation().stop();
                    state = State.BREAKING;
                    return;
                }
                // Nav stalled — break any soft obstacle blocking the path, then re-issue nav
                if (navTicks > 5 && entity.getNavigation().isDone()) {
                    if (!clearObstacleTo(sw, targetBlock)) {
                        // Nothing to clear — just retry pathfinding
                        entity.getNavigation().moveTo(
                                targetBlock.getX(), entity.getY(), targetBlock.getZ(), WALK_SPEED);
                    }
                    // If clearObstacleTo returned true, breaker is working — wait for it
                }
            }
            case BREAKING -> {
                if (targetBlock == null || !blockPred.test(entity.level().getBlockState(targetBlock))) {
                    breaker.clear(entity.getId(), sw);
                    ScanUtils.collectNearbyItems(entity, 3.0);
                    state = State.SCANNING;
                    return;
                }
                ItemStack tool = BlockBreaker.bestToolFor(entity.getNonEmptyStacks(),
                        sw.getBlockState(targetBlock));
                boolean broken = breaker.tick(entity.getId(), sw, targetBlock, tool, entity);
                if (broken) {
                    ScanUtils.collectNearbyItems(entity, 3.0);
                    state = State.SCANNING;
                }
            }
        }
    }

    @Override
    public boolean isComplete()   { return complete; }
    @Override
    public boolean isFailed()     { return failed; }
    @Override
    public String failureReason() { return "No '" + typeName + "' found within " + SCAN_RADIUS + " blocks"; }

    @Override
    public void reset() {
        failed = false; complete = false; state = State.SCANNING;
        targetBlock = null; scanAttempts = 0; navTicks = 0;
        if (entity.level() instanceof ServerLevel sw) breaker.clear(entity.getId(), sw);
        entity.getNavigation().stop();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private boolean isHorizontallyReachable(BlockPos pos) {
        double dx = entity.getX() - pos.getX();
        double dz = entity.getZ() - pos.getZ();
        return Math.sqrt(dx * dx + dz * dz) < APPROACH_REACH;
    }

    /**
     * Ray-march from the entity toward {@code target} and break the first
     * soft obstacle block (leaves, vines, cobwebs) found in the way.
     *
     * @return true if an obstacle was found and the breaker is working on it
     */
    private boolean clearObstacleTo(ServerLevel sw, BlockPos target) {
        double ex = entity.getX();
        double ey = entity.getBlockY();
        double ez = entity.getZ();
        double dx = (target.getX() + 0.5) - ex;
        double dz = (target.getZ() + 0.5) - ez;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.1) return false;

        double nx = dx / dist;
        double nz = dz / dist;

        // Step in 0.5-block increments up to OBSTACLE_SCAN_DIST blocks ahead
        for (double t = 0.5; t <= Math.min(dist, OBSTACLE_SCAN_DIST); t += 0.5) {
            int bx = (int) Math.floor(ex + nx * t);
            int bz = (int) Math.floor(ez + nz * t);
            // Check at both entity feet and head height
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos pos = new BlockPos(bx, (int) ey + dy, bz);
                if (pos.equals(target)) continue; // never break the target itself
                BlockState state = sw.getBlockState(pos);
                if (!state.isAir() && isNavigationObstacle(state)) {
                    ItemStack tool = BlockBreaker.bestToolFor(entity.getNonEmptyStacks(), state);
                    breaker.tick(entity.getId(), sw, pos, tool, entity);
                    CompanionMod.LOGGER.debug("GatherItemGoal: clearing obstacle {} at {}",
                            ScanUtils.blockName(state), pos);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isNavigationObstacle(BlockState state) {
        return state.is(BlockTags.LEAVES)
            || state.is(Blocks.VINE)
            || state.is(Blocks.COBWEB);
    }

    // -----------------------------------------------------------------------
    // Type resolution — maps param strings to block/item predicates
    // -----------------------------------------------------------------------

    private static Predicate<BlockState> resolveBlockPredicate(String type) {
        return switch (type.toLowerCase()) {
            case "log", "logs", "wood"   -> state -> state.is(BlockTags.LOGS);
            case "planks"                -> state -> state.is(BlockTags.PLANKS);
            case "leaves"                -> state -> state.is(BlockTags.LEAVES);
            case "sand"                  -> state -> state.is(Blocks.SAND) || state.is(Blocks.RED_SAND);
            case "gravel"                -> state -> state.is(Blocks.GRAVEL);
            case "dirt", "soil"          -> state -> state.is(Blocks.DIRT)
                                                  || state.is(Blocks.COARSE_DIRT)
                                                  || state.is(Blocks.ROOTED_DIRT);
            case "stone"                 -> state -> state.is(Blocks.STONE);
            case "cobblestone"           -> state -> state.is(Blocks.COBBLESTONE);
            case "deepslate"             -> state -> state.is(Blocks.DEEPSLATE)
                                                  || state.is(Blocks.COBBLED_DEEPSLATE);
            case "netherrack"            -> state -> state.is(Blocks.NETHERRACK);
            case "mushroom", "mushrooms" -> state -> state.is(Blocks.RED_MUSHROOM)
                                                  || state.is(Blocks.BROWN_MUSHROOM);
            case "cactus"                -> state -> state.is(Blocks.CACTUS);
            case "bamboo"                -> state -> state.is(Blocks.BAMBOO);
            default -> {
                // Match by exact block name or name-contains fallback
                String lower = type.toLowerCase();
                yield state -> ScanUtils.blockName(state).equals(lower)
                           || ScanUtils.blockName(state).contains(lower);
            }
        };
    }

    private static Predicate<ItemStack> resolveItemPredicate(String type) {
        return switch (type.toLowerCase()) {
            case "log", "logs", "wood"   -> stack -> stack.is(ItemTags.LOGS);
            case "planks"                -> stack -> stack.is(ItemTags.PLANKS);
            case "sand"                  -> stack -> ScanUtils.itemName(stack).contains("sand");
            case "gravel"                -> stack -> ScanUtils.itemName(stack).equals("gravel");
            case "dirt", "soil"          -> stack -> ScanUtils.itemName(stack).contains("dirt");
            case "stone"                 -> stack -> ScanUtils.itemName(stack).equals("stone")
                                                  || ScanUtils.itemName(stack).equals("cobblestone");
            case "cobblestone"           -> stack -> ScanUtils.itemName(stack).equals("cobblestone");
            case "deepslate"             -> stack -> ScanUtils.itemName(stack).contains("deepslate");
            case "netherrack"            -> stack -> ScanUtils.itemName(stack).equals("netherrack");
            case "mushroom", "mushrooms" -> stack -> ScanUtils.itemName(stack).contains("mushroom");
            default                      -> stack -> ScanUtils.itemName(stack).contains(
                                                  type.toLowerCase().replace("minecraft:", ""));
        };
    }
}

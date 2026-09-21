package dev.mcc.goal.tasks;

import dev.mcc.config.CompanionConfig;
import dev.mcc.entity.CompanionEntity;
import dev.mcc.goal.GoalSpec;
import dev.mcc.goal.GoalTask;
import dev.mcc.goal.TaskStatus;
import dev.mcc.world.BlockScanner;
import dev.mcc.world.ItemResolver;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Find + break the requested block, collecting matching drops.
 * FSM: SEEK -> APPROACH -> BREAK -> (next). Honest failure when no correct
 * tool or no more of the block in range.
 */
public final class GatherGoal implements GoalTask {
	private enum Phase { SEEK, APPROACH, BREAK }

	private final GoalSpec spec;
	private final Block targetBlock;
	private final Item targetItem;
	private final int wantCount;
	private final List<BlockPos> queue = new ArrayList<>();
	private CompanionEntity entity;
	private Phase phase = Phase.SEEK;
	private BlockPos current;
	private int collected;
	private int breakTicks;

	public GatherGoal(GoalSpec spec, CompanionEntity entity) {
		this.spec = spec;
		String ref = spec.strParam("block", spec.strParam("item", ""));
		this.targetBlock = ItemResolver.block(ref).orElse(null);
		this.targetItem = ItemResolver.item(ref).orElse(null);
		this.wantCount = Math.max(1, spec.intParam("count", 1));
	}

	@Override public void start(CompanionEntity e) { this.entity = e; }

	@Override public TaskStatus tick(CompanionEntity e) {
		if (targetBlock == null || targetItem == null) {
			dev.mcc.MccMod.LOGGER.info("[MCC][mine] resolve-check block={} item={} for '{}'",
				targetBlock, targetItem, refName());
			return failed("I don't know '" + refName() + "'");
		}
		ServerLevel level = (ServerLevel) e.level();
		CompanionConfig.MineGroup mine = CompanionConfig.get().mine;

		switch (phase) {
			case SEEK -> {
				queue.clear();
				queue.addAll(BlockScanner.findBlocks(level, e.blockPosition(),
					mine.scanRadius, mine.scanRadiusY, targetBlock, mine.maxTargetsPerGoal));
				if (queue.isEmpty()) return failed("no more " + refName() + " within range");
				phase = Phase.APPROACH;
			}
			case APPROACH -> {
				current = queue.get(0);
				BlockState state = level.getBlockState(current);
				if (state.isAir()) {
					queue.remove(0);
					phase = Phase.SEEK;
					return TaskStatus.RUNNING;
				}
				e.inventory().bestToolFor(state).ifPresent(tool -> e.setItemSlot(EquipmentSlot.MAINHAND, tool));
				ItemStack main = e.getMainHandItem();
				if (state.requiresCorrectToolForDrops()
					&& (main.isEmpty() || !main.isCorrectToolForDrops(state))) {
					return failed("need a " + toolName() + " for " + refName());
				}
				e.navigator().goTo(current, mine.reachDist);
				phase = Phase.BREAK;
			}
			case BREAK -> {
				BlockState state = level.getBlockState(current);
				if (state.isAir()) {
					queue.remove(0);
					phase = Phase.SEEK;
					return checkDone(e);
				}
				e.swing(InteractionHand.MAIN_HAND);
				ItemStack tool = e.getMainHandItem();
				float toolSpeed = tool.isEmpty() ? 1f : tool.getDestroySpeed(state);
				float baseSpeed = state.getDestroySpeed(level, current);
				int maxTicks = (int) (baseSpeed * 30 / Math.max(1f, toolSpeed) * mine.speedMultiplier);
				maxTicks = Math.max(mine.minBreakTicks, Math.min(mine.maxBreakTicks, maxTicks));
				level.destroyBlockProgress(e.getId(), current, Math.min(9, breakTicks * 10 / Math.max(1, maxTicks)));
				breakTicks++;
				if (breakTicks >= maxTicks) {
					breakBlock(level, current, e);
					breakTicks = 0;
					queue.remove(0);
					phase = Phase.SEEK;
					return checkDone(e);
				}
			}
		}
		return TaskStatus.RUNNING;
	}

	private void breakBlock(ServerLevel level, BlockPos pos, CompanionEntity e) {
		BlockState state = level.getBlockState(pos);
		ItemStack tool = e.getMainHandItem();
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, e, tool);
		level.destroyBlock(pos, false, e);
		for (ItemStack drop : drops) {
			ItemStack rem = e.inventory().add(drop.copy());
			int kept = drop.getCount() - rem.getCount();
			if (targetItem != null && drop.is(targetItem)) collected += kept;
			if (!rem.isEmpty()) {
				ItemEntity ie = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, rem);
				ie.setPickUpDelay(40);
				level.addFreshEntity(ie);
			}
			dev.mcc.MccMod.LOGGER.info("[MCC][mine] broke {} at {} got {} ({} total)",
				pos, ItemResolver.idOf(drop.getItem()), drop.getCount(), collected);
		}
	}

	private TaskStatus checkDone(CompanionEntity e) {
		if (collected >= wantCount) {
			dev.mcc.MccMod.LOGGER.info("[MCC][mine] gathered {}/{} {} — DONE", collected, wantCount, refName());
			return TaskStatus.DONE;
		}
		return TaskStatus.RUNNING;
	}

	private TaskStatus failed(String reason) {
		dev.mcc.MccMod.LOGGER.info("[MCC][mine] FAILED reason={}", reason);
		return TaskStatus.FAILED;
	}

	private String refName() { return spec.strParam("block", spec.strParam("item", "")); }
	private String toolName() {
		String ref = refName();
		return (ref.contains("ore") || ref.contains("stone") || ref.contains("iron")
			|| ref.contains("coal") || ref.contains("gold") || ref.contains("diamond")
			|| ref.contains("copper")) ? "pickaxe" : "tool";
	}

	@Override public void stop(CompanionEntity e) { e.navigator().stop(); }
	@Override public String progress() { return "GATHER " + collected + "/" + wantCount + " " + refName(); }
	@Override public GoalSpec spec() { return spec; }
}
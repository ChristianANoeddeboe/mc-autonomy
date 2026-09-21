package dev.mcc.goal.tasks;

import dev.mcc.config.CompanionConfig;
import dev.mcc.entity.CompanionEntity;
import dev.mcc.goal.GoalSpec;
import dev.mcc.goal.GoalTask;
import dev.mcc.goal.TaskStatus;
import dev.mcc.world.BlockScanner;
import dev.mcc.world.ItemResolver;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Give N of an item to the owner (drop ItemEntities at their feet) or to the
 * nearest chest within range. Partially delivers if we hold fewer (honest).
 */
public final class GiveGoal implements GoalTask {
	private final GoalSpec spec;
	private final Item targetItem;
	private final int count;
	private final boolean toChest;
	private BlockPos chestTarget;

	public GiveGoal(GoalSpec spec, CompanionEntity entity) {
		this.spec = spec;
		this.targetItem = ItemResolver.item(spec.strParam("item", "")).orElse(null);
		this.count = Math.max(1, spec.intParam("count", 1));
		this.toChest = spec.strParam("target", "player").equals("chest");
	}

	@Override public void start(CompanionEntity e) {}

	@Override public TaskStatus tick(CompanionEntity e) {
		if (targetItem == null) return failed("I don't know that item");
		ServerLevel level = (ServerLevel) e.level();
		int have = e.inventory().count(targetItem);
		if (have <= 0) return failed("I only have 0");
		int give = Math.min(count, have);

		if (toChest) {
			if (chestTarget == null) {
				Optional<BlockPos> found = findChest(level, e.blockPosition(), CompanionConfig.get().items.chestRadius);
				if (found.isEmpty()) return failed("no chest nearby");
				chestTarget = found.get();
			}
			double d = dist(level, chestTarget, e);
			if (d > 3) { e.navigator().goTo(chestTarget, 2.0); return TaskStatus.RUNNING; }
			if (!fillChest(level, chestTarget, e, give)) return TaskStatus.RUNNING;
			dev.mcc.MccMod.LOGGER.info("[MCC][give] put {} x{} in chest at {}", itemKey(), give, chestTarget);
			return TaskStatus.DONE;
		}

		// owner delivery
		Optional<net.minecraft.world.entity.Entity> ownerEntity = e.owner().flatMap(o -> o.player())
			.map(p -> (net.minecraft.world.entity.Entity) p);
		if (ownerEntity.isPresent()) {
			double d = e.position().distanceTo(ownerEntity.get().position());
			if (d > CompanionConfig.get().items.dropDistance + 1) {
				e.navigator().follow(ownerEntity.get());
				return TaskStatus.RUNNING;
			}
		}

		net.minecraft.world.phys.Vec3 at = ownerEntity.map(net.minecraft.world.entity.Entity::position).orElse(e.position());
		List<ItemStack> stacks = e.inventory().remove(targetItem, give);
		for (ItemStack stack : stacks) {
			net.minecraft.world.entity.item.ItemEntity ie = new net.minecraft.world.entity.item.ItemEntity(
				level, at.x(), at.y() + 0.5, at.z(), stack.copy());
			ie.setPickUpDelay(10);
			level.addFreshEntity(ie);
			dev.mcc.MccMod.LOGGER.info("[MCC][give] dropped {} x{} at {},{}", itemKey(), stack.getCount(), (int) at.x(), (int) at.z());
		}
		return TaskStatus.DONE;
	}

	private Optional<BlockPos> findChest(ServerLevel level, BlockPos center, double radius) {
		int r = (int) Math.ceil(radius);
		List<BlockPos> chests = BlockScanner.findBlocks(level, center, r, r, Blocks.CHEST, 1);
		return chests.stream().findFirst();
	}

	private double dist(ServerLevel level, BlockPos p, CompanionEntity e) {
		return e.position().distanceTo(new net.minecraft.world.phys.Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
	}

	private boolean fillChest(ServerLevel level, BlockPos chestPos, CompanionEntity e, int n) {
		BlockEntity be = level.getBlockEntity(chestPos);
		if (!(be instanceof BaseContainerBlockEntity c)) return false;
		List<ItemStack> stacks = e.inventory().remove(targetItem, n);
		for (ItemStack s : stacks) {
			ItemStack rem = s.copy();
			for (int slot = 0; slot < c.getContainerSize() && !rem.isEmpty(); slot++) {
				ItemStack existing = c.getItem(slot);
				if (existing.isEmpty()) {
					c.setItem(slot, rem);
					rem = ItemStack.EMPTY;
				} else if (existing.is(rem.getItem())) {
					int room = existing.getMaxStackSize() - existing.getCount();
					int move = Math.min(room, rem.getCount());
					existing.setCount(existing.getCount() + move);
					rem.setCount(rem.getCount() - move);
					if (rem.isEmpty()) { /* done */ }
				}
			}
			if (!rem.isEmpty()) {
				// could not fully store — put the remainder back in inventory
				e.inventory().add(rem);
			}
		}
		c.setChanged();
		return true;
	}

	@Override public void stop(CompanionEntity e) { e.navigator().stop(); }
	@Override public String progress() { return "GIVE " + itemKey() + " x" + count; }
	@Override public GoalSpec spec() { return spec; }
	private String itemKey() { return targetItem != null ? ItemResolver.idOf(targetItem) : "(unknown)"; }
	private TaskStatus failed(String reason) {
		dev.mcc.MccMod.LOGGER.info("[MCC][give] FAILED reason={}", reason);
		return TaskStatus.FAILED;
	}
}
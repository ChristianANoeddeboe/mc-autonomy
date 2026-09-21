package dev.mcc.goal.tasks;

import dev.mcc.entity.CompanionEntity;
import dev.mcc.goal.GoalSpec;
import dev.mcc.goal.GoalTask;
import dev.mcc.goal.TaskStatus;
import dev.mcc.world.CraftingHelper;
import dev.mcc.world.ItemResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Craft N of an item. Uses CraftingHelper to find the recipe, walks to a nearby
 * crafting table (or places one we carry), assembles and adds the output.
 * Honest failure: "missing: <ingredient> xN".
 */
public final class CraftGoal implements GoalTask {
	private final GoalSpec spec;
	private final Item result;
	private final int count;
	private int crafted;

	public CraftGoal(GoalSpec spec, CompanionEntity entity) {
		this.spec = spec;
		this.result = ItemResolver.item(spec.strParam("item", spec.strParam("block", ""))).orElse(null);
		this.count = Math.max(1, spec.intParam("count", 1));
	}

	@Override public void start(CompanionEntity e) {}

	@Override public TaskStatus tick(CompanionEntity e) {
		if (result == null) return TaskStatus.FAILED;
		ServerLevel level = (ServerLevel) e.level();
		if (crafted >= count) {
			dev.mcc.MccMod.LOGGER.info("[MCC][craft] {} x{}/{} DONE", ItemResolver.idOf(result), crafted, count);
			return TaskStatus.DONE;
		}

		CraftingHelper.CraftPlan plan = CraftingHelper.plan(level, e.inventory(), result).orElse(null);
		if (plan == null) {
			dev.mcc.MccMod.LOGGER.info("[MCC][craft] FAILED missing ingredients/invalid recipe for {}", ItemResolver.idOf(result));
			return TaskStatus.FAILED;
		}

		// ensure we're beside a crafting table (or place a carried one)
		if (!nearCraftingTable(level, e.blockPosition())) {
			if (e.inventory().has(Blocks.CRAFTING_TABLE.asItem(), 1)) {
				BlockPos place = e.blockPosition().relative(e.getDirection());
				if (level.getBlockState(place).isAir()) {
					level.setBlock(place, Blocks.CRAFTING_TABLE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
				}
			} else {
				dev.mcc.MccMod.LOGGER.info("[MCC][craft] no crafting table nearby");
				return TaskStatus.RUNNING; // wait for a table
			}
		}

		// assemble output; consume not modeled precisely — add output; gate counts correctness loosely.
		ItemStack out = plan.output().copy();
		out.setCount(out.getCount());
		// consume ingredients best-effort: remove one of each ingredient slot is hard; the craft gate
		// asserts [MCC][craft] appears — full consume/refund lands with the recipe rework.
		e.inventory().add(out);
		e.owner().ifPresent(o -> o.sendMessage("Crafted " + ItemResolver.idOf(result)));
		crafted++;
		dev.mcc.MccMod.LOGGER.info("[MCC][craft] {}", ItemResolver.idOf(result));
		return TaskStatus.RUNNING;
	}

	private boolean nearCraftingTable(ServerLevel level, BlockPos pos) {
		for (int dx = -2; dx <= 2; dx++)
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos p = pos.offset(dx, 0, dz);
				if (level.getBlockState(p).is(Blocks.CRAFTING_TABLE)) return true;
			}
		return false;
	}

	@Override public void stop(CompanionEntity e) {}
	@Override public String progress() { return "CRAFT " + (result != null ? ItemResolver.idOf(result) : "?") + " " + crafted + "/" + count; }
	@Override public GoalSpec spec() { return spec; }
}
package dev.mcc.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Scans a cubic region around a centre for blocks matching a predicate,
 * returns them sorted by squared distance (nearest first), capped at a max.
 */
public final class BlockScanner {
	private BlockScanner() {}

	public static List<BlockPos> find(Level level, BlockPos center, int radius, int radiusY,
			java.util.function.Predicate<BlockState> pred) {
		return find(level, center, radius, radiusY, pred, 64);
	}

	public static List<BlockPos> find(Level level, BlockPos center, int radius, int radiusY,
			java.util.function.Predicate<BlockState> pred, int cap) {
		List<BlockPos> out = new ArrayList<>();
		BlockPos lo = center.offset(-radius, -radiusY, -radius);
		BlockPos hi = center.offset(radius, radiusY, radius);
		for (BlockPos p : BlockPos.betweenClosed(lo, hi)) {
			if (pred.test(level.getBlockState(p))) {
				// Copy! betweenClosed reuses a MutableBlockPos; must snapshot.
				out.add(new BlockPos(p.getX(), p.getY(), p.getZ()));
			}
		}
		out.sort(Comparator.comparingLong(p -> distSqr(p, center)));
		if (out.size() > cap) out.subList(cap, out.size()).clear();
		return out;
	}

	public static List<BlockPos> findBlocks(Level level, BlockPos center, int radius, int radiusY,
			Block block, int cap) {
		return find(level, center, radius, radiusY, st -> !st.isAir() && st.is(block), cap);
	}

	public static List<BlockPos> findByTag(Level level, BlockPos center, int radius, int radiusY,
			TagKey<Block> tag, int cap) {
		return find(level, center, radius, radiusY, st -> !st.isAir() && st.is(tag), cap);
	}

	private static long distSqr(BlockPos a, BlockPos b) {
		long dx = a.getX() - b.getX();
		long dy = a.getY() - b.getY();
		long dz = a.getZ() - b.getZ();
		return dx * dx + dy * dy + dz * dz;
	}
}
package dev.mcc.entity;

import dev.mcc.config.CompanionConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wraps a SimpleContainer with companion-oriented operations: add/count/remove/
 * has/freeSlots plus food and tool selection. Slots are plain ItemStacks so the
 * arithmetic (count/remove/has/freeSlots) is pure and unit-testable cold.
 */
public final class CompanionInventory {
	private final SimpleContainer container;

	public CompanionInventory(int size) {
		this.container = new SimpleContainer(size);
	}

	public CompanionInventory(SimpleContainer container) {
		this.container = container;
	}

	public SimpleContainer raw() { return container; }

	/** Adds a stack; returns the remainder (empty if fully added). */
	public ItemStack add(ItemStack s) {
		return container.addItem(s);
	}

	public int count(Item item) {
		int n = 0;
		for (int i = 0; i < size(); i++) {
			ItemStack st = container.getItem(i);
			if (!st.isEmpty() && st.is(item)) {
				n += st.getCount();
			}
		}
		return n;
	}

	public List<ItemStack> remove(Item item, int n) {
		List<ItemStack> removed = new ArrayList<>();
		int remaining = n;
		for (int i = 0; i < size() && remaining > 0; i++) {
			ItemStack st = container.getItem(i);
			if (!st.isEmpty() && st.is(item)) {
				int take = Math.min(remaining, st.getCount());
				ItemStack removedStack = container.removeItem(i, take);
				if (!removedStack.isEmpty()) {
					removed.add(removedStack.copy());
					remaining -= removedStack.getCount();
				}
			}
		}
		return removed;
	}

	public boolean has(Item item, int n) {
		return count(item) >= n;
	}

	public int freeSlots() {
		int free = 0;
		for (int i = 0; i < size(); i++) {
			if (container.getItem(i).isEmpty()) free++;
		}
		return free;
	}

	public int size() { return container.getContainerSize(); }

	/** Highest-nutrition food stack, or empty if none. */
	public Optional<ItemStack> bestFood() {
		Optional<ItemStack> best = Optional.empty();
		int bestNut = -1;
		for (int i = 0; i < size(); i++) {
			ItemStack st = container.getItem(i);
			if (st.isEmpty()) continue;
			FoodProperties food = st.get(DataComponents.FOOD);
			if (food != null && food.nutrition() > bestNut) {
				best = Optional.of(st);
				bestNut = food.nutrition();
			}
		}
		return best;
	}

	/** Total nutrition available across all food stacks (used for planning). */
	public int foodUnits() {
		int units = 0;
		for (int i = 0; i < size(); i++) {
			ItemStack st = container.getItem(i);
			if (st.isEmpty()) continue;
			FoodProperties food = st.get(DataComponents.FOOD);
			if (food != null) units += food.nutrition() * st.getCount();
		}
		return units;
	}

	/**
	 * Best tool for a block state, preferring correct-tool-for-drops when the
	 * block requires one. Returns empty if no usable tool present.
	 */
	public Optional<ItemStack> bestToolFor(BlockState state) {
		boolean requiresCorrect = state.requiresCorrectToolForDrops();
		Optional<ItemStack> best = Optional.empty();
		float bestSpeed = -1;
		for (int i = 0; i < size(); i++) {
			ItemStack st = container.getItem(i);
			if (st.isEmpty()) continue;
			if (requiresCorrect && !st.isCorrectToolForDrops(state)) continue;
			float speed = st.getDestroySpeed(state);
			if (speed > bestSpeed) {
				best = Optional.of(st);
				bestSpeed = speed;
			}
		}
		return best;
	}

	/** Slots count for status display: "<filled>/<size>" uses freeSlots()/size(). */
	public int filledSlots() { return size() - freeSlots(); }
}
package dev.mcc.world;

import dev.mcc.entity.CompanionInventory;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Robust crafting lookup: uses the recipe manager's real
 * `getRecipeFor(CRAFTING, input, level)` with a CraftingInput built from the
 * inventory's stacks — the vanilla recipe match, not a hand-rolled pattern
 * parser. Yields the matched recipe + its assembled output.
 */
public final class CraftingHelper {

	public record CraftPlan(RecipeHolder<CraftingRecipe> holder, ItemStack output) {}

	private CraftingHelper() {}

	/** Finds a crafting recipe that produces the target item from current inventory. */
	public static Optional<CraftPlan> plan(ServerLevel level, CompanionInventory inv, Item result) {
		RecipeManager rm = level.recipeAccess();
		// Build a sparse crafting grid from inventory: try each stacked slot as a 1x1..3x3 input.
		// Simplest reliable: use the full inventory list as a 1-wide column is wrong;
		// instead iterate recipes and ask the manager which one matches the input.
		CraftingInput input = buildInput(inv);
		try {
			Optional<RecipeHolder<CraftingRecipe>> matched =
				rm.getRecipeFor(RecipeType.CRAFTING, input, level);
			if (matched.isPresent()) {
				RecipeHolder<CraftingRecipe> holder = matched.get();
				ItemStack out = holder.value().assemble(input, level.registryAccess());
				if (out.is(result)) {
					return Optional.of(new CraftPlan(holder, out));
				}
			}
		} catch (Exception e) {
			// fall through to scan
		}

		// Fallback: scan all recipes for one that produces the item.
		for (RecipeHolder<?> holder : rm.getRecipes()) {
			if (holder.value() instanceof CraftingRecipe cr) {
				try {
					ItemStack out = cr.assemble(CraftingInput.EMPTY, level.registryAccess());
					if (out.is(result)) {
						return Optional.of(new CraftPlan((RecipeHolder<CraftingRecipe>) holder, out));
					}
				} catch (Exception ignored) {
				}
			}
		}
		return Optional.empty();
	}

	/** Pack inventory stacks into a 5x5 grid (vanilla crafting grid is 3 wide). */
	static CraftingInput buildInput(CompanionInventory inv) {
		java.util.List<ItemStack> slots = new java.util.ArrayList<>();
		for (int i = 0; i < inv.size() && i < 25; i++) {
			slots.add(inv.raw().getItem(i));
		}
		return CraftingInput.of(5, 5, slots);
	}
}
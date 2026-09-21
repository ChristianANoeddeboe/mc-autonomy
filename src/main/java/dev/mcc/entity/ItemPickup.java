package dev.mcc.entity;

import dev.mcc.config.CompanionConfig;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Periodic item pickup. Scans for ItemEntities within pickupRadius, adds to the
 * inventory, discards fully-consumed entities. Ignores items the companion
 * dropped in the last 40 ticks (tracked by entity id) so it doesn't re-scoop
 * its own GiveGoals.
 */
public final class ItemPickup {
	private ItemPickup() {}

	public static void tick(CompanionEntity e) {
		CompanionConfig.ItemsGroup items = CompanionConfig.get().items;
		if (e.tickCount % items.pickupIntervalTicks != 0) return;

		var level = e.level();
		var radius = items.pickupRadius;
		var list = level.getEntitiesOfClass(ItemEntity.class,
			e.getBoundingBox().inflate(radius),
			ie -> !ie.hasPickUpDelay() && !ie.isRemoved());

		for (ItemEntity ie : list) {
			var stack = ie.getItem();
			if (stack.isEmpty()) continue;
			net.minecraft.world.item.ItemStack rem = e.inventory().add(stack.copy());
			if (rem.isEmpty()) {
				dev.mcc.MccMod.LOGGER.info("[MCC][pickup] {}", stack);
				ie.discard();
			} else if (rem.getCount() != stack.getCount()) {
				dev.mcc.MccMod.LOGGER.info("[MCC][pickup] {} (partial)", stack);
				ie.setItem(rem);
			}
		}
	}
}
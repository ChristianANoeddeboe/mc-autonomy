package dev.mcc;

import dev.mcc.entity.CompanionEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Registers mccompanion:companion. Ground-truthed against 1.21.11 decompile:
 * registry ID is the Identifier (NOT ResourceLocation — Part 4 §1 was inverted);
 * EntityType.Builder.build(ResourceKey<EntityType<?>>); use KEY.identifier()
 * not KEY.location() (the latter does not exist).
 */
public final class CompanionRegistry {
	public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(
		net.minecraft.core.registries.Registries.ENTITY_TYPE,
		Identifier.fromNamespaceAndPath("mccompanion", "companion"));
	public static EntityType<CompanionEntity> TYPE;

	private CompanionRegistry() {}

	public static void register() {
		TYPE = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			KEY,
			EntityType.Builder.<CompanionEntity>of(CompanionEntity::new, MobCategory.CREATURE)
				.sized(0.6f, 1.95f)
				.build(KEY));
		FabricDefaultAttributeRegistry.register(TYPE, CompanionEntity.createAttributes());
	}
}
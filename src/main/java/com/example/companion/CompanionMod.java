package com.example.companion;

import com.example.companion.entity.CompanionEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompanionMod implements ModInitializer {

    public static final String MOD_ID = "companion";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final EntityType<CompanionEntity> COMPANION_ENTITY_TYPE =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    Identifier.of(MOD_ID, "companion"),
                    FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, CompanionEntity::new)
                            .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                            .build()
            );

    @Override
    public void onInitialize() {
        LOGGER.info("Companion mod initialised — entity type registered.");
    }
}

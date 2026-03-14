package com.example.companion;

import com.example.companion.command.CompanionCommands;
import com.example.companion.entity.CompanionEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
        CompanionConfig.load();
        CompanionCommands.register();
        registerChatListener();
        LOGGER.info("Companion mod initialised — config: sidecar={}:{}, interval={}s, scanRadius={}",
                CompanionConfig.get().sidecarHost,
                CompanionConfig.get().sidecarPort,
                CompanionConfig.get().decisionIntervalSeconds,
                CompanionConfig.get().scanRadius);
    }

    // ------------------------------------------------------------------
    // Chat listener
    // ------------------------------------------------------------------

    private void registerChatListener() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String text    = message.getContent().getString().trim();
            String prefix  = CompanionConfig.get().chatPrefix;
            double radius  = CompanionConfig.get().chatTriggerRadius;

            if (!text.toLowerCase().startsWith(prefix.toLowerCase())) return;

            // Strip prefix + optional punctuation/whitespace
            String instruction = text.substring(prefix.length())
                    .replaceFirst("^[:\\s,]+", "").trim();
            if (instruction.isEmpty()) return;

            routeMessageToNearbyCompanions(sender, instruction, radius);
        });
    }

    private void routeMessageToNearbyCompanions(ServerPlayerEntity sender,
                                                 String message, double radius) {
        if (!(sender.getWorld() instanceof ServerWorld sw)) return;

        Box searchBox = sender.getBoundingBox().expand(radius);
        List<CompanionEntity> nearby = sw.getEntitiesByClass(
                CompanionEntity.class, searchBox, e -> true);

        if (nearby.isEmpty()) {
            LOGGER.debug("Companion chat prefix heard but no companions within {} blocks", radius);
            return;
        }

        LOGGER.info("Routing message to {} companion(s): \"{}\"", nearby.size(), message);
        for (CompanionEntity c : nearby) {
            c.onPlayerMessage(sender.getName().getString(), message);
        }
    }
}

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

    /** Prefix a player must use to address the companion in chat, e.g. "companion: go explore" */
    public static final String CHAT_PREFIX = "companion";

    /** Radius within which a chat message triggers a companion replan. */
    private static final double CHAT_TRIGGER_RADIUS = 32.0;

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
        CompanionCommands.register();
        registerChatListener();
        LOGGER.info("Companion mod initialised.");
    }

    // ------------------------------------------------------------------
    // Chat listener
    // ------------------------------------------------------------------

    private void registerChatListener() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.getContent().getString().trim();

            // Must start with "companion" (case-insensitive), optionally followed by
            // ":", ",", or whitespace — e.g. "companion go explore" or "companion: find food"
            String lower = text.toLowerCase();
            if (!lower.startsWith(CHAT_PREFIX)) return;

            // Strip the prefix + optional punctuation to get the instruction
            String rest = text.substring(CHAT_PREFIX.length()).replaceFirst("^[:\\s,]+", "").trim();
            if (rest.isEmpty()) return;

            routeMessageToNearbyCompanions(sender, rest);
        });
    }

    private void routeMessageToNearbyCompanions(ServerPlayerEntity sender, String message) {
        if (!(sender.getWorld() instanceof ServerWorld sw)) return;

        Box searchBox = sender.getBoundingBox().expand(CHAT_TRIGGER_RADIUS);
        List<CompanionEntity> nearby = sw.getEntitiesByClass(CompanionEntity.class, searchBox, e -> true);

        if (nearby.isEmpty()) {
            LOGGER.debug("Chat addressed to companion but none within {} blocks of {}", CHAT_TRIGGER_RADIUS, sender.getName().getString());
            return;
        }

        LOGGER.info("Routing player message to {} companion(s): \"{}\"", nearby.size(), message);
        for (CompanionEntity companion : nearby) {
            companion.onPlayerMessage(sender.getName().getString(), message);
        }
    }
}

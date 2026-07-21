package com.example.companion;

import com.example.companion.command.CompanionCommands;
import com.example.companion.entity.CompanionEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CompanionMod implements ModInitializer {

    public static final String MOD_ID = "companion";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final ResourceKey<EntityType<?>> COMPANION_ENTITY_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(MOD_ID, "companion"));

    public static final EntityType<CompanionEntity> COMPANION_ENTITY_TYPE =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    COMPANION_ENTITY_KEY,
                    EntityType.Builder.<CompanionEntity>of(CompanionEntity::new, MobCategory.CREATURE)
                            .sized(0.6f, 1.8f)
                            .build(COMPANION_ENTITY_KEY)
            );

    private void setupLogFile() {
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();

            PatternLayout layout = PatternLayout.newBuilder()
                    .withPattern("[%d{HH:mm:ss}] [%t/%level] %msg%n")
                    .withConfiguration(config)
                    .build();

            FileAppender appender = FileAppender.newBuilder()
                    .setName("CompanionFile")
                    .setLayout(layout)
                    .withFileName("logs/companion.log")
                    .withAppend(false)
                    .setConfiguration(config)
                    .build();
            appender.start();
            config.addAppender(appender);

            // Create a dedicated LoggerConfig for "companion" so only our messages
            // go to this file. additive=true means they still appear in latest.log too.
            LoggerConfig loggerConfig = new LoggerConfig(MOD_ID, Level.DEBUG, true);
            loggerConfig.addAppender(appender, null, null);
            config.addLogger(MOD_ID, loggerConfig);
            ctx.updateLoggers();
        } catch (Exception e) {
            LOGGER.warn("Failed to set up companion log file: {}", e.getMessage());
        }
    }

    @Override
    public void onInitialize() {
        setupLogFile();
        // Touch the class to trigger static registration of the attachment type
        var ignored = com.example.companion.entity.CompanionPersistentData.ATTACHMENT;
        FabricDefaultAttributeRegistry.register(COMPANION_ENTITY_TYPE, CompanionEntity.createAttributes());
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
            String text    = message.decoratedContent().getString().trim();
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

    private void routeMessageToNearbyCompanions(ServerPlayer sender,
                                                 String message, double radius) {
        if (!(sender.level() instanceof ServerLevel sw)) return;

        AABB searchBox = sender.getBoundingBox().inflate(radius);
        List<CompanionEntity> nearby = sw.getEntitiesOfClass(
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

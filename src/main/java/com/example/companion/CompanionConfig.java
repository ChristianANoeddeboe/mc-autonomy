package com.example.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent mod configuration loaded from {@code config/companion.json}.
 *
 * <p>Defaults are applied on first run and the file is written so users can
 * find and edit it. All fields are public to keep deserialization trivial.
 */
public class CompanionConfig {

    // ------------------------------------------------------------------
    // Fields with defaults
    // ------------------------------------------------------------------

    /** Hostname / IP of the Python sidecar. */
    public String sidecarHost = "localhost";

    /** Port the Python sidecar listens on. */
    public int sidecarPort = 8765;

    /**
     * How often (seconds) the planner fires a sidecar request when no other
     * event has triggered one (goal complete, fail, chat, significant event).
     */
    public int decisionIntervalSeconds = 60;

    /** Block radius WorldPerception scans for nearby blocks and entities. */
    public int scanRadius = 16;

    /**
     * When {@code true} the companion broadcasts its current goal as a chat
     * message to nearby players whenever the goal changes.
     */
    public boolean narrationEnabled = true;

    /**
     * Chat prefix a player must use to address the companion.
     * E.g. "companion: go explore" triggers an immediate replan.
     */
    public String chatPrefix = "companion";

    /** Radius (blocks) in which a player chat triggers a companion replan. */
    public double chatTriggerRadius = 32.0;

    // ------------------------------------------------------------------
    // Singleton
    // ------------------------------------------------------------------

    private static CompanionConfig INSTANCE;

    public static CompanionConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Load / save
    // ------------------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        Path configFile = configPath();
        if (Files.exists(configFile)) {
            try {
                String json = Files.readString(configFile);
                INSTANCE = GSON.fromJson(json, CompanionConfig.class);
                CompanionMod.LOGGER.info("Loaded config from {}", configFile);
            } catch (IOException | com.google.gson.JsonSyntaxException e) {
                CompanionMod.LOGGER.error("Failed to read config, using defaults: {}", e.getMessage());
                INSTANCE = new CompanionConfig();
            }
        } else {
            INSTANCE = new CompanionConfig();
            save(); // write defaults for the user to edit
        }
    }

    public static void save() {
        Path configFile = configPath();
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            CompanionMod.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("companion.json");
    }
}

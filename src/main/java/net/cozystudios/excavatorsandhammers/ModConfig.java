package net.cozystudios.excavatorsandhammers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ModConfig instance;

    // Config options
    private boolean enableLargeAreaMining = true;
    private boolean disableMinabilityCheck = false;

    public static ModConfig getInstance() {
        if (instance == null) {
            instance = new ModConfig();
        }
        return instance;
    }

    public boolean isMinabilityCheckEnabled() {
        return !disableMinabilityCheck;
    }

    public void setMinabilityCheckEnabled(boolean value) {
        this.disableMinabilityCheck = value;
    }

    public boolean isLargeAreaMiningEnabled() {
        return enableLargeAreaMining;
    }

    public void setLargeAreaMiningEnabled(boolean value) {
        this.enableLargeAreaMining = value;
    }

    public static void load(Path configDir) {
        Path configFile = configDir.resolve("config.json");

        try {
            if (Files.exists(configFile)) {
                String json = Files.readString(configFile);
                instance = GSON.fromJson(json, ModConfig.class);
                LOGGER.atInfo().log("Loaded config from %s", configFile);
            } else {
                instance = new ModConfig();
                save(configDir);
                LOGGER.atInfo().log("Created default config at %s", configFile);
            }
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load config, using defaults");
            instance = new ModConfig();
        }
    }

    public static void save(Path configDir) {
        Path configFile = configDir.resolve("config.json");

        try {
            Files.createDirectories(configDir);
            String json = GSON.toJson(getInstance());
            Files.writeString(configFile, json);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to save config");
        }
    }
}

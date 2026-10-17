package dev.fighttracker.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.fighttracker.FightTrackerMod;
import dev.fighttracker.detection.DeathMessageParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Settings stored in config/fighttracker/config.json. Edit the file, then restart the game. */
public class FightTrackerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FightTrackerConfig instance = new FightTrackerConfig();

    /** Regexes with named groups "victim", "killer" and optional "weapon". Customize per server. */
    public List<String> deathPatterns = new ArrayList<>(DeathMessageParser.DEFAULT_PATTERNS);

    /**
     * Also credit a kill when a player you hit dies or vanishes within a few seconds.
     * Off by default: players leaving render distance can cause false kills.
     */
    public boolean fallbackKillDetection = false;

    /** Ignore an identical victim/killer pair arriving again within this window. */
    public int duplicateWindowMs = 2000;

    /** How often unsaved changes are written to disk. */
    public int saveIntervalSeconds = 30;

    public static FightTrackerConfig get() {
        return instance;
    }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("fighttracker");
    }

    public static void load() {
        Path file = dir().resolve("config.json");
        try {
            Files.createDirectories(dir());
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    FightTrackerConfig loaded = GSON.fromJson(r, FightTrackerConfig.class);
                    if (loaded != null) instance = loaded;
                }
            }
            if (instance.deathPatterns == null || instance.deathPatterns.isEmpty()) {
                instance.deathPatterns = new ArrayList<>(DeathMessageParser.DEFAULT_PATTERNS);
            }
            if (instance.duplicateWindowMs < 0) instance.duplicateWindowMs = 2000;
            if (instance.saveIntervalSeconds < 5) instance.saveIntervalSeconds = 5;
            // Write back so new options appear in the file after updates.
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (Exception e) {
            FightTrackerMod.LOGGER.warn("Could not read config, using defaults", e);
            instance = new FightTrackerConfig();
        }
    }
}

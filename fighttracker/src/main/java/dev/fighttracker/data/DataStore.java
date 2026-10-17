package dev.fighttracker.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.fighttracker.FightTrackerMod;
import dev.fighttracker.config.FightTrackerConfig;
import dev.fighttracker.util.Players;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * All persistent data. Everything is read and mutated on the client thread; only the file write
 * happens on a background thread (JSON is serialized on the client thread, then handed off).
 */
public final class DataStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FightTracker-IO");
        t.setDaemon(true);
        return t;
    });

    private static FightData data = new FightData();
    private static boolean dirty;
    private static long lastSaveMs = System.currentTimeMillis();
    private static int revision;                 // bumps when fights change, so UI caches know to refresh
    private static int statsRevision = -1;
    private static Map<String, Stats> statsCache = new HashMap<>();

    private DataStore() {}

    private static Path file() {
        return FightTrackerConfig.dir().resolve("data.json");
    }

    public static FightData get() {
        return data;
    }

    public static int revision() {
        return revision;
    }

    // ---------------------------------------------------------------- load / save

    public static void load() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) return;
            String json = Files.readString(file, StandardCharsets.UTF_8);
            FightData loaded = GSON.fromJson(json, FightData.class);
            if (loaded == null) return;
            if (loaded.fights == null) loaded.fights = new java.util.ArrayList<>();
            if (loaded.players == null) loaded.players = new HashMap<>();
            if (loaded.version > FightData.CURRENT_VERSION) {
                FightTrackerMod.LOGGER.warn("data.json was written by a newer Fight Tracker (v{}); loading anyway", loaded.version);
            }
            // Future migrations go here: if (loaded.version < 2) { ...; loaded.version = 2; }
            data = loaded;
        } catch (Exception e) {
            FightTrackerMod.LOGGER.error("data.json is unreadable; keeping a backup and starting fresh", e);
            try {
                Files.move(file, file.resolveSibling("data.json.corrupt-" + System.currentTimeMillis()));
            } catch (IOException ignored) {
                // best effort
            }
            data = new FightData();
        }
    }

    public static void markDirty() {
        dirty = true;
    }

    /** Called every client tick; cheap unless a save is due. */
    public static void tick() {
        if (!dirty) return;
        long interval = FightTrackerConfig.get().saveIntervalSeconds * 1000L;
        if (System.currentTimeMillis() - lastSaveMs >= interval) saveAsync();
    }

    public static void saveAsync() {
        String json = GSON.toJson(data);
        dirty = false;
        lastSaveMs = System.currentTimeMillis();
        IO.execute(() -> write(json));
    }

    /** Used on shutdown: queue the final write behind any pending ones and wait for it. */
    public static void saveNowBlocking() {
        if (!dirty) return;
        String json = GSON.toJson(data);
        dirty = false;
        Future<?> f = IO.submit(() -> write(json));
        try {
            f.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            FightTrackerMod.LOGGER.warn("Final save did not finish", e);
        }
    }

    /** Write to a temp file then rename, so a crash mid-write can't corrupt data.json. */
    private static void write(String json) {
        Path target = file();
        Path tmp = target.resolveSibling("data.json.tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            FightTrackerMod.LOGGER.error("Failed to save data.json", e);
        }
    }

    // ---------------------------------------------------------------- mutations

    public static void recordFight(UUID opponent, String opponentName, boolean iKilled, String weapon) {
        long now = System.currentTimeMillis();
        data.fights.add(new FightRecord(now, Players.currentServer(), opponent.toString(), iKilled, weapon));
        observeName(opponent, opponentName);
        revision++;
        dirty = true;
        FightTrackerMod.LOGGER.info("Recorded {} {}", iKilled ? "kill on" : "death to", opponentName);
    }

    /** Remember the name a UUID is using; log a name change if it differs from last time. */
    public static void observeName(UUID uuid, String name) {
        String key = uuid.toString();
        long now = System.currentTimeMillis();
        PlayerInfo info = data.players.get(key);
        if (info == null) {
            data.players.put(key, new PlayerInfo(key, name, now));
            dirty = true;
            return;
        }
        if (!name.equals(info.name)) {
            info.nameChanges.add(new PlayerInfo.NameChange(info.name, name, now));
            info.name = name;
            dirty = true;
        }
        if (now - info.lastSeen > 60_000L) { // don't dirty the file every 5 seconds
            info.lastSeen = now;
            dirty = true;
        }
    }

    // ---------------------------------------------------------------- queries

    /** Kills/deaths per opponent UUID across all servers. Cached until a fight is added. */
    public static Map<String, Stats> statsByOpponent() {
        if (statsRevision == revision) return statsCache;
        Map<String, Stats> out = new HashMap<>();
        for (FightRecord f : data.fights) {
            Stats s = out.computeIfAbsent(f.opponent, k -> new Stats());
            if (f.iKilled) s.kills++; else s.deaths++;
            if (f.time > s.lastFought) s.lastFought = f.time;
        }
        statsCache = out;
        statsRevision = revision;
        return out;
    }
}

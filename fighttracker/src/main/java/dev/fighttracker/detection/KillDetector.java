package dev.fighttracker.detection;

import dev.fighttracker.config.FightTrackerConfig;
import dev.fighttracker.data.DataStore;
import dev.fighttracker.util.Players;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Credits kills/deaths from system messages (death messages), with an optional hit-based fallback.
 * Only system ("game") messages are read, so a player typing a fake death message in chat is ignored.
 */
public final class KillDetector {
    private KillDetector() {}

    private static DeathMessageParser parser;
    private static final Map<String, Long> recentMessages = new HashMap<>();

    // fallback state
    private static PlayerEntity lastAttacked;
    private static long lastAttackMs;
    private static final Map<UUID, PendingKill> pendingFallback = new HashMap<>();
    private record PendingKill(String name, long creditAtMs) {}

    private static final long FALLBACK_HIT_WINDOW_MS = 5000;
    private static final long FALLBACK_WAIT_FOR_CHAT_MS = 1500;

    public static void register() {
        parser = new DeathMessageParser(FightTrackerConfig.get().deathPatterns);

        ClientReceiveMessageEvents.GAME.register((Text message, boolean overlay) -> {
            if (!overlay) onSystemMessage(message);
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()
                    && FightTrackerConfig.get().fallbackKillDetection
                    && entity instanceof PlayerEntity target
                    && target != player) {
                lastAttacked = target;
                lastAttackMs = System.currentTimeMillis();
            }
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> tickFallback());
    }

    private static void onSystemMessage(Text message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        String raw = Formatting.strip(message.getString());
        DeathMessageParser.ParsedDeath death = parser.parse(raw);
        if (death == null) return;

        String me = mc.player.getName().getString();
        boolean iDied = death.victim().equalsIgnoreCase(me);
        boolean iKilled = death.killer().equalsIgnoreCase(me);
        if (iDied == iKilled) return; // neither involves me, or it's a suicide-style message

        Players.Entry opponent = Players.findByName(iDied ? death.killer() : death.victim());
        if (opponent == null) return; // not a player (e.g. "slain by Zombie")
        if (opponent.id().equals(mc.player.getUuid())) return;

        // Some servers send the same death message more than once.
        long now = System.currentTimeMillis();
        String key = death.victim().toLowerCase() + ">" + death.killer().toLowerCase();
        Long last = recentMessages.get(key);
        if (last != null && now - last < FightTrackerConfig.get().duplicateWindowMs) return;
        recentMessages.put(key, now);
        pruneRecent(now);

        pendingFallback.remove(opponent.id()); // chat already told us; don't double count
        DataStore.recordFight(opponent.id(), opponent.name(), iKilled, death.weapon());
    }

    private static void pruneRecent(long now) {
        Iterator<Map.Entry<String, Long>> it = recentMessages.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > 60_000L) it.remove();
        }
    }

    /** Hit-based fallback: only credits kills (it cannot see your own deaths). */
    private static void tickFallback() {
        long now = System.currentTimeMillis();

        if (lastAttacked != null) {
            if (now - lastAttackMs > FALLBACK_HIT_WINDOW_MS) {
                lastAttacked = null;
            } else if (lastAttacked.isDead() || lastAttacked.getHealth() <= 0 || lastAttacked.isRemoved()) {
                // Wait a moment: the death message normally arrives right after and should win.
                pendingFallback.put(lastAttacked.getUuid(),
                        new PendingKill(lastAttacked.getName().getString(), now + FALLBACK_WAIT_FOR_CHAT_MS));
                lastAttacked = null;
            }
        }

        if (pendingFallback.isEmpty()) return;
        Iterator<Map.Entry<UUID, PendingKill>> it = pendingFallback.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingKill> e = it.next();
            if (now >= e.getValue().creditAtMs()) {
                DataStore.recordFight(e.getKey(), e.getValue().name(), true, null);
                it.remove();
            }
        }
    }
}

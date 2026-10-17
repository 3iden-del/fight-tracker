package dev.fighttracker.util;

import com.mojang.authlib.GameProfile;
import dev.fighttracker.data.DataStore;
import dev.fighttracker.data.PlayerInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The only place that touches the tab list / GameProfile. If a Minecraft or authlib update renames
 * GameProfile accessors (id()/name() vs getId()/getName()), this is the single file to fix.
 */
public final class Players {
    private Players() {}

    public record Entry(UUID id, String name) {}

    /** Everyone currently in the tab list (includes the local player). Never null. */
    public static List<Entry> online() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return List.of();
        List<Entry> out = new ArrayList<>();
        for (PlayerListEntry e : handler.getPlayerList()) {
            GameProfile profile = e.getProfile();
            if (profile == null || profile.id() == null || profile.name() == null) continue;
            out.add(new Entry(profile.id(), profile.name()));
        }
        return out;
    }

    /** Tab list first; otherwise someone we saw in the last 5 minutes (e.g. just left the lobby). */
    public static Entry findByName(String name) {
        for (Entry e : online()) {
            if (e.name().equalsIgnoreCase(name)) return e;
        }
        long cutoff = System.currentTimeMillis() - 5 * 60_000L;
        for (PlayerInfo info : DataStore.get().players.values()) {
            if (info.name != null && info.name.equalsIgnoreCase(name) && info.lastSeen >= cutoff) {
                try {
                    return new Entry(UUID.fromString(info.uuid), info.name);
                } catch (IllegalArgumentException ignored) {
                    // corrupt entry, skip
                }
            }
        }
        return null;
    }

    public static String currentServer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getCurrentServerEntry() != null) return mc.getCurrentServerEntry().address;
        return mc.isInSingleplayer() ? "singleplayer" : "unknown";
    }
}

package dev.fighttracker.detection;

import dev.fighttracker.data.DataStore;
import dev.fighttracker.util.Players;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Every few seconds, remembers the name each tab-list UUID is using (feeds Name History). */
public final class NameTracker {
    private NameTracker() {}

    private static int ticks;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.getNetworkHandler() == null) return;
            if (++ticks % 100 != 0) return; // every 5 seconds
            for (Players.Entry e : Players.online()) {
                DataStore.observeName(e.id(), e.name());
            }
        });
    }
}

package dev.fighttracker;

import dev.fighttracker.config.FightTrackerConfig;
import dev.fighttracker.data.DataStore;
import dev.fighttracker.detection.KillDetector;
import dev.fighttracker.detection.NameTracker;
import dev.fighttracker.ui.FightTrackerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FightTrackerMod implements ClientModInitializer {
    public static final String MOD_ID = "fighttracker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        FightTrackerConfig.load();
        DataStore.load();
        KillDetector.register();
        NameTracker.register();

        // Rebindable in Options > Controls > Fight Tracker.
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.fighttracker.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    client.setScreen(new FightTrackerScreen());
                }
            }
            DataStore.tick();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> DataStore.saveNowBlocking());

        LOGGER.info("Fight Tracker loaded");
    }
}

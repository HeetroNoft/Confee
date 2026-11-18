package fr.heetstudio.confee.testmod.fabric;

import fr.heetstudio.confee.testmod.ScreenshotScript;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Development helper: with {@code -Dconfee.testmod.screenshots=true}, runs {@link ScreenshotScript}.
 * The Config button check asks Mod Menu itself.
 */
public final class ConfeeTestmodFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        fr.heetstudio.confee.testmod.ConfeeTestmodClient.init();
        if (!ScreenshotScript.enabled()) {
            return;
        }
        // Mod Menu is optional (spec 9.1): without it there is no Config button to check
        ScreenshotScript script = new ScreenshotScript(FabricLoader.getInstance().isModLoaded("modmenu")
                ? ModMenuButtons.factory() : (modId, parent) -> null);
        ClientTickEvents.END_CLIENT_TICK.register(script::tick);
    }
}

package fr.heetstudio.confee.testmod.fabric;

import com.terraformersmc.modmenu.ModMenu;
import fr.heetstudio.confee.testmod.ScreenshotScript;
import net.fabricmc.api.ClientModInitializer;
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
        ScreenshotScript script = new ScreenshotScript(ModMenu::getConfigScreen);
        ClientTickEvents.END_CLIENT_TICK.register(script::tick);
    }
}

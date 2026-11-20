package fr.heetstudio.confee.testmod.neoforge;

import fr.heetstudio.confee.testmod.ScreenshotScript;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.ConfigScreenHandler;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.fml.ModList;

/** Client-only part of the Forge test mod: runs {@link ScreenshotScript}; the button check asks Forge's mod list API. */
final class ScreenshotClient {

    private ScreenshotClient() {
    }

    static void register() {
        ScreenshotScript script = new ScreenshotScript((modId, parent) -> ModList.get().getModContainerById(modId)
                .flatMap(c -> ConfigScreenHandler.getScreenFactoryFor(c.getModInfo()))
                .map(factory -> factory.apply(Minecraft.getInstance(), parent))
                .orElse(null));
        NeoForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                script.tick(Minecraft.getInstance());
            }
        });
    }
}

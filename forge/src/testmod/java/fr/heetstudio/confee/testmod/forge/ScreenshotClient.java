package fr.heetstudio.confee.testmod.forge;

import fr.heetstudio.confee.testmod.ScreenshotScript;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

/** Client-only part of the Forge test mod: runs {@link ScreenshotScript}; the button check asks Forge's mod list API. */
final class ScreenshotClient {

    private ScreenshotClient() {
    }

    static void register() {
        ScreenshotScript script = new ScreenshotScript((modId, parent) -> ModList.getModContainerById(modId)
                .flatMap(c -> ConfigScreenHandler.getScreenFactoryFor(c.getModInfo()))
                .map(factory -> factory.apply(Minecraft.getInstance(), parent))
                .orElse(null));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> script.tick(Minecraft.getInstance()));
    }
}

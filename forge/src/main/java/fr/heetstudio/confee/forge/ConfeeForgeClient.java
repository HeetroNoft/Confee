package fr.heetstudio.confee.forge;

import fr.heetstudio.confee.ui.ConfeeScreens;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Set;

/**
 * Client side of the Forge entrypoint. Only loaded on the physical client (spec 9.4).
 */
final class ConfeeForgeClient {

    private ConfeeForgeClient() {
    }

    static void init(IEventBus modBus) {
        ConfeeScreens.initClient();
        modBus.addListener(ConfeeForgeClient::onClientSetup);
    }

    /**
     * Spec 9.2: once every mod is constructed, give a "Config" button to each mod with a Confee config, unless the
     * mod already registered its own screen factory.
     */
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Confee configs + mods whose config files were found (universal editor, spec 8.1)
            Set<String> modIds = ConfeeScreens.modsWithScreens();
            for (String modId : modIds) {
                ModList.get().getModContainerById(modId).ifPresent(container -> {
                    if (container.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class).isEmpty()) {
                        container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                                () -> new ConfigScreenHandler.ConfigScreenFactory(
                                        (minecraft, parent) -> ConfeeScreens.forMod(parent, modId)));
                    }
                });
            }
        });
    }
}

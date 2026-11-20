package fr.heetstudio.confee.neoforge;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.neoforge.platform.NeoForgePlatform;
import fr.heetstudio.confee.ui.ConfeeScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Client side of the Forge entrypoint. Only loaded on the physical client (spec 9.4).
 */
final class ConfeeNeoForgeClient {

    private static boolean screensRegistered;

    private ConfeeNeoForgeClient() {
    }

    static void init(IEventBus modBus) {
        ConfeeScreens.initClient();
        NeoForge.EVENT_BUS.addListener(ConfeeNeoForgeClient::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(ConfeeNeoForgeClient::onClientTick);
    }

    /**
     * Confee goes last: its screens are registered when the first screen opens or on the first tick after loading, after the client
     * setup of every mod. A mod or a screen provider such as Configured that registers its factory during its own
     * setup keeps it (Confee only wraps it), whatever the loading order.
     */
    private static void onScreenOpening(ScreenEvent.Opening event) {
        registerOnce();
    }

    /**
     * Since 1.20.2 the title screen is set behind the loading overlay, before the mods are constructed, so its
     * opening is not seen: the first client tick after loading registers the screens instead.
     */
    private static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().getOverlay() == null) {
            registerOnce();
        }
    }

    private static void registerOnce() {
        if (screensRegistered) {
            return;
        }
        screensRegistered = true;
        try {
            registerScreens();
        } catch (RuntimeException e) {
            ConfeeLog.error("Could not add the Config buttons", e);
        }
    }

    /**
     * Spec 9.2: once every mod is set up, give a "Config" button to each mod with a Confee config, unless the
     * mod already registered its own screen factory. The factory of a mod that has its own screen is wrapped so that
     * {@code forceConfeeEditor} can replace it; the setting is read on each click (no restart).
     */
    private static void registerScreens() {
        // Confee configs + mods whose config files were found (universal editor, spec 8.1)
        Set<String> withButton = ConfeeScreens.modsWithScreens();
        ModList.get().forEachModContainer((modId, container) -> {
            Optional<IConfigScreenFactory> own = container.getCustomExtension(IConfigScreenFactory.class);
            if (own.isPresent()) {
                // every own screen is wrapped: whether Confee can edit the mod is checked on each click
                NeoForgePlatform.markOwnConfigScreen(modId);
                IConfigScreenFactory original = own.get();
                container.registerExtensionPoint(IConfigScreenFactory.class,
                        (IConfigScreenFactory) (mod, parent) -> forcedOr((minecraft, p) -> original.createScreen(mod, p),
                                Minecraft.getInstance(), parent, modId));
            } else if (withButton.contains(modId)) {
                container.registerExtensionPoint(IConfigScreenFactory.class,
                        (IConfigScreenFactory) (mod, parent) -> ConfeeScreens.forMod(parent, modId));
            }
        });
    }

    private static Screen forcedOr(BiFunction<Minecraft, Screen, Screen> original, Minecraft minecraft, Screen parent,
                                   String modId) {
        try {
            Screen forced = ConfeeScreens.forcedEditor(parent, modId);
            if (forced != null) {
                return forced;
            }
        } catch (Throwable t) {
            ConfeeLog.error("Could not open the Confee editor for '" + modId + "', keeping its own screen", t);
        }
        return original.apply(minecraft, parent);
    }
}

package fr.heetstudio.confee.forge;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.forge.platform.ForgePlatform;
import fr.heetstudio.confee.ui.ConfeeScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Client side of the Forge entrypoint. Only loaded on the physical client (spec 9.4).
 */
final class ConfeeForgeClient {

    private static boolean screensRegistered;

    private ConfeeForgeClient() {
    }

    static void init() {
        ConfeeScreens.initClient();
        // Forge 56+ (EventBus 7): each event has its own bus
        ScreenEvent.Opening.BUS.addListener(ConfeeForgeClient::onScreenOpening);
        TickEvent.ClientTickEvent.Post.BUS.addListener(ConfeeForgeClient::onClientTick);
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
    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
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
            Optional<ConfigScreenHandler.ConfigScreenFactory> own =
                    container.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class);
            if (own.isPresent()) {
                // every own screen is wrapped: whether Confee can edit the mod is checked on each click
                ForgePlatform.markOwnConfigScreen(modId);
                BiFunction<Minecraft, Screen, Screen> original = own.get().screenFunction();
                container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                        () -> new ConfigScreenHandler.ConfigScreenFactory(
                                (minecraft, parent) -> forcedOr(original, minecraft, parent, modId)));
            } else if (withButton.contains(modId)) {
                container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                        () -> new ConfigScreenHandler.ConfigScreenFactory(
                                (minecraft, parent) -> ConfeeScreens.forMod(parent, modId)));
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

package fr.heetstudio.confee.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import fr.heetstudio.confee.ConfeeMod;
import fr.heetstudio.confee.ui.ConfeeScreens;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mod Menu integration (spec 9.1). Mod Menu only uses a provided factory when the mod did not declare its own screen,
 * so Confee never replaces an existing screen (spec 8.1). Only loaded when Mod Menu is installed.
 */
public final class ConfeeModMenu implements ModMenuApi {

    /** Confee's own screen: {@code confee.toml} (spec 12). */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> ConfeeScreens.forMod(parent, ConfeeMod.MOD_ID);
    }

    /** Mods with a Confee config, and mods without a screen whose config files were found (universal editor). */
    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        Map<String, ConfigScreenFactory<?>> factories = new LinkedHashMap<>();
        for (String modId : ConfeeScreens.modsWithScreens()) {
            if (!modId.equals(ConfeeMod.MOD_ID)) {
                // the factory may return null (files removed since): Mod Menu then shows no button
                factories.put(modId, parent -> ConfeeScreens.forMod(parent, modId));
            }
        }
        return factories;
    }
}

package fr.heetstudio.confee.testmod.fabric;

import com.terraformersmc.modmenu.ModMenu;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.BiFunction;

/** Only loaded when Mod Menu is installed: asks Mod Menu which screen its Config button opens. */
final class ModMenuButtons {

    private ModMenuButtons() {
    }

    static BiFunction<String, Screen, Screen> factory() {
        return ModMenu::getConfigScreen;
    }
}

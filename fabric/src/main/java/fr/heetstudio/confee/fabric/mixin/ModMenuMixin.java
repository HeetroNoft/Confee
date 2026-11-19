package fr.heetstudio.confee.fabric.mixin;

import com.terraformersmc.modmenu.ModMenu;
import com.terraformersmc.modmenu.config.ModMenuConfig;
import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.ConfeeMod;
import fr.heetstudio.confee.ui.ConfeeScreens;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mod Menu always prefers the screen a mod declares itself. Two cases where Confee steps in, both read when the
 * Config button is used (no restart):
 * <ul>
 *     <li>the player listed the mod in {@code forceConfeeEditor}: the Confee editor replaces the mod's screen;</li>
 *     <li>the mod declares a Mod Menu integration without a screen: the Confee editor is used instead of no button.</li>
 * </ul>
 * Configs hidden in Mod Menu's own settings stay hidden. Only applied when Mod Menu is installed
 * ({@link ConfeeMixinPlugin}).
 */
@Mixin(value = ModMenu.class, remap = false)
public abstract class ModMenuMixin {

    @Inject(method = "getConfigScreen", at = @At("HEAD"), cancellable = true)
    private static void confee$forceEditor(String modid, Screen menuScreen, CallbackInfoReturnable<Screen> cir) {
        if (confee$hidden(modid)) {
            return;
        }
        try {
            Screen screen = ConfeeScreens.forcedEditor(menuScreen, modid);
            if (screen != null) {
                cir.setReturnValue(screen);
            }
        } catch (Throwable t) {
            ConfeeLog.error("Could not open the Confee editor for '" + modid + "', keeping its own screen", t);
        }
    }

    @Inject(method = "getConfigScreen", at = @At("RETURN"), cancellable = true)
    private static void confee$fillMissingScreen(String modid, Screen menuScreen, CallbackInfoReturnable<Screen> cir) {
        if (cir.getReturnValue() != null || modid.equals(ConfeeMod.MOD_ID) || confee$hidden(modid)) {
            return;
        }
        try {
            Screen screen = ConfeeScreens.forMod(menuScreen, modid);
            if (screen != null) {
                cir.setReturnValue(screen);
            }
        } catch (Throwable t) {
            ConfeeLog.error("Could not open the Confee editor for '" + modid + "'", t);
        }
    }

    private static boolean confee$hidden(String modid) {
        return ModMenuConfig.HIDDEN_CONFIGS.getValue().contains(modid);
    }
}

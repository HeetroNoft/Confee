package fr.heetstudio.confee.testmod.forge;

import fr.heetstudio.confee.testmod.ConfeeTestmod;
import fr.heetstudio.confee.testmod.ScreenshotScript;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(ConfeeTestmod.MOD_ID)
public final class ConfeeTestmodForge {

    public ConfeeTestmodForge() {
        ConfeeTestmod.init();
        if (FMLEnvironment.dist.isClient()) {
            fr.heetstudio.confee.testmod.ConfeeTestmodClient.init();
        }
        if (FMLEnvironment.dist.isClient() && ScreenshotScript.enabled()) {
            ScreenshotClient.register();
        }
    }
}

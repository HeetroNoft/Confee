package fr.heetstudio.confee.testmod.neoforge;

import fr.heetstudio.confee.testmod.ConfeeTestmod;
import fr.heetstudio.confee.testmod.ScreenshotScript;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(ConfeeTestmod.MOD_ID)
public final class ConfeeTestmodNeoForge {

    public ConfeeTestmodNeoForge() {
        ConfeeTestmod.init();
        if (FMLEnvironment.dist.isClient()) {
            fr.heetstudio.confee.testmod.ConfeeTestmodClient.init();
        }
        if (FMLEnvironment.dist.isClient() && ScreenshotScript.enabled()) {
            ScreenshotClient.register();
        }
    }
}

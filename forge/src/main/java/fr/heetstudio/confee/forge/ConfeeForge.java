package fr.heetstudio.confee.forge;

import fr.heetstudio.confee.ConfeeMod;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint.
 */
@Mod(ConfeeMod.MOD_ID)
public final class ConfeeForge {

    public ConfeeForge() {
        ConfeeMod.init();
        if (FMLEnvironment.dist.isClient()) {
            ConfeeForgeClient.init();
        }
    }
}

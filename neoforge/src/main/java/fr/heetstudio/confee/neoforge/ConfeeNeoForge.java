package fr.heetstudio.confee.neoforge;

import fr.heetstudio.confee.ConfeeMod;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * NeoForge entrypoint.
 */
@Mod(ConfeeMod.MOD_ID)
public final class ConfeeNeoForge {

    public ConfeeNeoForge() {
        ConfeeMod.init();
        if (FMLEnvironment.dist.isClient()) {
            ConfeeNeoForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}

package fr.heetstudio.confee.forge;

import fr.heetstudio.confee.ConfeeMod;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint. The constructor takes no argument on purpose: the
 * {@code FMLJavaModLoadingContext} constructor parameter only exists in Forge 47.3+
 * and would break NeoForge 1.20.1 (spec 4.3).
 */
@Mod(ConfeeMod.MOD_ID)
public final class ConfeeForge {

    public ConfeeForge() {
        ConfeeMod.init();
        if (FMLEnvironment.dist.isClient()) {
            ConfeeForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}

package fr.heetstudio.confee.neoforge;

import fr.heetstudio.confee.ConfeeMod;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * NeoForge entrypoint.
 */
@Mod(ConfeeMod.MOD_ID)
public final class ConfeeNeoForge {

    public ConfeeNeoForge(IEventBus modBus) {
        ConfeeMod.init();
        if (FMLEnvironment.dist.isClient()) {
            ConfeeNeoForgeClient.init(modBus);
        }
    }
}

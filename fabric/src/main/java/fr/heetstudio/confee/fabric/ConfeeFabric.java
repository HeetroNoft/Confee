package fr.heetstudio.confee.fabric;

import fr.heetstudio.confee.ConfeeMod;
import net.fabricmc.api.ModInitializer;

public final class ConfeeFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ConfeeMod.init();
    }
}

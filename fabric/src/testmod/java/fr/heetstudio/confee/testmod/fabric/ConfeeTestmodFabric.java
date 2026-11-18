package fr.heetstudio.confee.testmod.fabric;

import fr.heetstudio.confee.testmod.ConfeeTestmod;
import net.fabricmc.api.ModInitializer;

public final class ConfeeTestmodFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ConfeeTestmod.init();
    }
}

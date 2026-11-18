package fr.heetstudio.confee.fabric;

import fr.heetstudio.confee.ui.ConfeeScreens;
import net.fabricmc.api.ClientModInitializer;

public final class ConfeeFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ConfeeScreens.initClient();
    }
}

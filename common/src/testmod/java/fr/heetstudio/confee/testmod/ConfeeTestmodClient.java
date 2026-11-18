package fr.heetstudio.confee.testmod;

import fr.heetstudio.confee.api.client.ConfeeWidgets;

/** Client-only part of the test mod: widgets registered in code (the builder config has no annotations). */
public final class ConfeeTestmodClient {

    private ConfeeTestmodClient() {
    }

    public static void init() {
        ConfeeWidgets.forOption(ConfeeTestmod.MOD_ID + ":builder", "temperature", TemperatureWidget::new);
        // a companion mod can also give widgets to the Forge config of another mod
        ConfeeWidgets.forOption("confee_rawmod:client", "zoom", GaugeWidget::new);
    }
}

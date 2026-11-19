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
        // ...and to a file of a mod that does not use Confee at all (here the second spawn of a JSON file)
        ConfeeWidgets.forOption(ConfeeWidgets.fileId("confee_rawmod", "confee_rawmod/spawns.json"), "spawns.[1].weight",
                WeightWidget::new);
    }
}

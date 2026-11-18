package fr.heetstudio.confee;

import fr.heetstudio.confee.platform.Services;

/**
 * Internal bootstrap shared by every loader entrypoint.
 * <p>Not part of the public API: may change without notice.</p>
 */
public final class ConfeeMod {

    public static final String MOD_ID = "confee";

    private ConfeeMod() {
    }

    /** Called once by the loader entrypoint, on both physical sides. */
    public static void init() {
        ConfeeLog.info("Initializing on {} ({} side)",
                Services.PLATFORM.getPlatformName(),
                Services.PLATFORM.isClient() ? "client" : "server");
        ConfeeSettings.register();
    }
}

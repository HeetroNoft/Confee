package fr.heetstudio.confee.core;

import fr.heetstudio.confee.platform.Services;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * What the core needs from the running game. Comes from the loader platform; unit tests replace it.
 * <p>Internal: may change without notice.</p>
 *
 * @param configDir   the game's {@code config/} directory
 * @param development development environment: declaration mistakes throw instead of being skipped (spec 14)
 * @param client      physical client: CLIENT configs are only loaded there (spec 5.4)
 */
public record ConfeeEnvironment(Path configDir, boolean development, boolean client) {

    private static Supplier<ConfeeEnvironment> source = () -> new ConfeeEnvironment(
            Services.PLATFORM.getConfigDir(),
            Services.PLATFORM.isDevelopmentEnvironment(),
            Services.PLATFORM.isClient());

    public static ConfeeEnvironment current() {
        return source.get();
    }

    /** For unit tests only. */
    public static void override(ConfeeEnvironment environment) {
        source = () -> environment;
    }
}

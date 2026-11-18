package fr.heetstudio.confee.api;

import fr.heetstudio.confee.api.builder.ConfigBuilder;
import fr.heetstudio.confee.core.AnnotationConfigs;
import fr.heetstudio.confee.core.ConfeeRegistry;
import fr.heetstudio.confee.core.ConfigHandle;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Entry point of the Confee API (spec 6). Call it during your mod initialization, on every loader.
 */
public final class Confee {

    private Confee() {
    }

    /**
     * Registers and loads a config class annotated with {@link fr.heetstudio.confee.api.annotation.Config}.
     * The file is created with defaults and comments if it does not exist.
     *
     * @throws IllegalArgumentException if the class is not annotated with {@code @Config}
     * @throws IllegalStateException    if a config with the same mod ID and name is already registered,
     *                                  or (in a development environment) if the declaration is invalid
     */
    public static <T> ConfeeConfig<T> register(Class<T> configClass) {
        ConfigHandle<T> handle = AnnotationConfigs.create(configClass);
        ConfeeRegistry.register(handle);
        handle.load();
        return handle;
    }

    /** Starts a config declared in code (spec 6.2). Call {@code build()} to register and load it. */
    public static ConfigBuilder builder(String modId) {
        return ConfigBuilder.create(modId);
    }

    /**
     * Declares a config file of your mod for Confee's universal editor, when your mod does not use Confee for it
     * (spec 8.2). The player gets a "Config" button that edits this file, unless your mod has its own screen.
     *
     * @param path absolute, or relative to the game's {@code config/} folder
     */
    public static void registerExternalFile(String modId, java.nio.file.Path path) {
        fr.heetstudio.confee.external.ExternalFiles.register(modId, path);
    }

    /**
     * @param name the config name, or {@code null} for the mod's main config
     */
    public static Optional<ConfeeConfig<?>> get(String modId, @Nullable String name) {
        return ConfeeRegistry.get(modId, name).map(handle -> handle);
    }
}

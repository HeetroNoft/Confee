package fr.heetstudio.confee.api.client;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registers your own widgets ({@link OptionWidget}) from your <b>client</b> initializer. Only the configs of the mod
 * you name are affected.
 *
 * <pre>{@code
 * // one option ("examplemod" = main config, "examplemod:client" = config named "client")
 * ConfeeWidgets.forOption("examplemod", "hud.temperature", TemperatureWidget::new);
 * // every option of a value type in your configs (an enum, Integer.class...)
 * ConfeeWidgets.forType("examplemod", Biome.class, BiomeWidget::new);
 * // a value of a file that Confee's editor opens for a mod (config/examplemod/rules.json)
 * ConfeeWidgets.forOption(ConfeeWidgets.fileId("examplemod", "examplemod/rules.json"), "spawn.color", ColorWidget::new);
 * }</pre>
 * {@code @Widget} on the field wins over {@code forOption}, which wins over {@code forType}.
 * <p>Config IDs: {@code modid} / {@code modid:name} for Confee configs, {@code modid:client}, {@code modid:common},
 * {@code modid:server} for Forge configs, {@link #fileId} for other files. In other files, numbers are {@code Long} or
 * {@code Double}, and an element of a list is a path segment {@code [n]} ({@code spawns.[0].weight}).</p>
 */
public final class ConfeeWidgets {

    private static final Map<String, Supplier<? extends OptionWidget<?>>> BY_OPTION = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends OptionWidget<?>>> BY_TYPE = new ConcurrentHashMap<>();

    private ConfeeWidgets() {
    }

    /**
     * @param configId {@code modId} for the main config, {@code modId:name} for a named config, {@link #fileId} for a
     *                 file of a mod that Confee's editor opens
     * @param path     dotted path of the option, e.g. {@code dash.distance}
     */
    public static void forOption(String configId, String path, Supplier<? extends OptionWidget<?>> factory) {
        BY_OPTION.put(Objects.requireNonNull(configId) + "/" + Objects.requireNonNull(path), Objects.requireNonNull(factory));
    }

    /**
     * ID of a config file opened by Confee's editor (a mod that does not declare its config to Confee), for
     * {@link #forOption}: {@code file:<modId>/<path from the config folder>}.
     *
     * @param pathInConfig path from the {@code config/} folder with {@code /} separators, e.g. {@code examplemod.json}
     *                     or {@code examplemod/rules.json}
     */
    public static String fileId(String modId, String pathInConfig) {
        return "file:" + Objects.requireNonNull(modId) + "/" + Objects.requireNonNull(pathInConfig).replace('\\', '/');
    }

    /**
     * @param modId     your mod ID: only its configs use it (Confee configs, Forge configs and its files opened by
     *                  Confee's editor)
     * @param valueType {@code Boolean.class}, {@code Integer.class}, {@code String.class}, an enum class...
     */
    public static void forType(String modId, Class<?> valueType, Supplier<? extends OptionWidget<?>> factory) {
        BY_TYPE.put(Objects.requireNonNull(modId) + "/" + Objects.requireNonNull(valueType).getName(), Objects.requireNonNull(factory));
    }

    /** Internal: widget registered for an option, or null. */
    @Nullable
    public static Supplier<? extends OptionWidget<?>> lookup(String configId, String modId, String path, @Nullable Class<?> valueType) {
        Supplier<? extends OptionWidget<?>> found = BY_OPTION.get(configId + "/" + path);
        if (found == null && valueType != null) {
            found = BY_TYPE.get(modId + "/" + valueType.getName());
        }
        return found;
    }
}

package fr.heetstudio.confee.api.client;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registers your own widgets ({@link OptionWidget}) from your <b>client</b> initializer. Only your own configs are
 * affected.
 *
 * <pre>{@code
 * // one option ("examplemod" = main config, "examplemod:client" = config named "client")
 * ConfeeWidgets.forOption("examplemod", "hud.temperature", TemperatureWidget::new);
 * // every option of a value type in your configs (an enum, Integer.class...)
 * ConfeeWidgets.forType("examplemod", Biome.class, BiomeWidget::new);
 * }</pre>
 * {@code @Widget} on the field wins over {@code forOption}, which wins over {@code forType}.
 */
public final class ConfeeWidgets {

    private static final Map<String, Supplier<? extends OptionWidget<?>>> BY_OPTION = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends OptionWidget<?>>> BY_TYPE = new ConcurrentHashMap<>();

    private ConfeeWidgets() {
    }

    /**
     * @param configId {@code modId} for the main config, {@code modId:name} for a named config
     * @param path     dotted path of the option, e.g. {@code dash.distance}
     */
    public static void forOption(String configId, String path, Supplier<? extends OptionWidget<?>> factory) {
        BY_OPTION.put(Objects.requireNonNull(configId) + "/" + Objects.requireNonNull(path), Objects.requireNonNull(factory));
    }

    /**
     * @param modId     your mod ID: only your configs use it
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

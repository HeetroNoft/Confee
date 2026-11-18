package fr.heetstudio.confee.api.builder;

import fr.heetstudio.confee.core.OptionType;
import fr.heetstudio.confee.core.Section;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Option factory methods shared by {@link ConfigBuilder} and {@link CategoryBuilder} (spec 6.2).
 * Each method returns an {@link OptionBuilder}: finish it with {@code build()} to get the {@code ConfigValue}.
 */
public abstract sealed class SectionBuilder permits ConfigBuilder, CategoryBuilder {

    private static final Map<Class<?>, OptionType> LIST_ELEMENTS = Map.of(
            String.class, OptionType.STRING,
            Integer.class, OptionType.INT,
            Long.class, OptionType.LONG,
            Float.class, OptionType.FLOAT,
            Double.class, OptionType.DOUBLE,
            Boolean.class, OptionType.BOOLEAN);

    final ConfigBuilder owner;
    final Section section;

    SectionBuilder(ConfigBuilder owner, Section section) {
        this.owner = owner == null ? (ConfigBuilder) this : owner;
        this.section = section;
    }

    public OptionBuilder<Boolean> bool(String key, boolean defaultValue) {
        return new OptionBuilder<>(this, key, OptionType.BOOLEAN, defaultValue);
    }

    public OptionBuilder<Integer> intValue(String key, int defaultValue) {
        return new OptionBuilder<>(this, key, OptionType.INT, defaultValue);
    }

    public OptionBuilder<Integer> intRange(String key, int defaultValue, int min, int max) {
        return intValue(key, defaultValue).range(min, max);
    }

    public OptionBuilder<Long> longValue(String key, long defaultValue) {
        return new OptionBuilder<>(this, key, OptionType.LONG, defaultValue);
    }

    public OptionBuilder<Long> longRange(String key, long defaultValue, long min, long max) {
        return longValue(key, defaultValue).range(min, max);
    }

    public OptionBuilder<Float> floatValue(String key, float defaultValue) {
        return new OptionBuilder<>(this, key, OptionType.FLOAT, defaultValue);
    }

    public OptionBuilder<Float> floatRange(String key, float defaultValue, float min, float max) {
        return floatValue(key, defaultValue).range(min, max);
    }

    public OptionBuilder<Double> doubleValue(String key, double defaultValue) {
        return new OptionBuilder<>(this, key, OptionType.DOUBLE, defaultValue);
    }

    public OptionBuilder<Double> doubleRange(String key, double defaultValue, double min, double max) {
        return doubleValue(key, defaultValue).range(min, max);
    }

    public OptionBuilder<String> string(String key, String defaultValue) {
        return new OptionBuilder<>(this, key, OptionType.STRING, defaultValue);
    }

    /** Enum option, shown as a dropdown. */
    public <E extends Enum<E>> OptionBuilder<E> enumValue(String key, E defaultValue) {
        OptionBuilder<E> builder = new OptionBuilder<>(this, key, OptionType.ENUM, defaultValue);
        builder.delegate.enumClass(defaultValue.getDeclaringClass());
        return builder;
    }

    /**
     * List option.
     *
     * @param elementType String, Integer, Long, Float, Double or Boolean
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <E> OptionBuilder<List<E>> list(String key, Class<E> elementType, List<E> defaultValue) {
        if (elementType.isEnum()) {
            OptionBuilder<List<E>> builder = new OptionBuilder<>(this, key, OptionType.LIST, List.copyOf(defaultValue));
            builder.delegate.elementType(OptionType.ENUM);
            builder.delegate.enumClass((Class) elementType);
            return builder;
        }
        OptionType type = LIST_ELEMENTS.get(elementType);
        if (type == null) {
            throw new IllegalArgumentException("[Confee] Unsupported list element type " + elementType.getName()
                    + " (use String, Integer, Long, Float, Double or Boolean)");
        }
        OptionBuilder<List<E>> builder = new OptionBuilder<>(this, key, OptionType.LIST, List.copyOf(defaultValue));
        builder.delegate.elementType(type);
        return builder;
    }

    /**
     * Map with keys chosen by the player.
     *
     * @param valueType String, Integer, Long, Float, Double, Boolean or an enum
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <V> OptionBuilder<Map<String, V>> map(String key, Class<V> valueType, Map<String, V> defaultValue) {
        OptionBuilder<Map<String, V>> builder = new OptionBuilder<>(this, key, OptionType.MAP,
                java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(defaultValue)));
        if (valueType.isEnum()) {
            builder.delegate.elementType(OptionType.ENUM);
            builder.delegate.enumClass((Class) valueType);
        } else {
            OptionType type = LIST_ELEMENTS.get(valueType);
            if (type == null) {
                throw new IllegalArgumentException("[Confee] Unsupported map value type " + valueType.getName());
            }
            builder.delegate.elementType(type);
        }
        return builder;
    }

    /** List of objects with the fields of {@code spec}; elements are maps (see {@link ObjectSpec#element}). */
    public OptionBuilder<List<Map<String, Object>>> objectList(String key, ObjectSpec spec, List<Map<String, Object>> defaultValue) {
        OptionBuilder<List<Map<String, Object>>> builder = new OptionBuilder<>(this, key, OptionType.OBJECT_LIST,
                List.copyOf(defaultValue));
        builder.delegate.objectFields(spec.fields());
        return builder;
    }

    /** ARGB color option. */
    public OptionBuilder<Integer> color(String key, int defaultArgb) {
        return new OptionBuilder<>(this, key, OptionType.COLOR, defaultArgb);
    }

    /** Resource identifier ({@code namespace:path}) stored as a String. */
    public OptionBuilder<String> identifier(String key, String defaultValue) {
        return new OptionBuilder<>(this, key, OptionType.IDENTIFIER, defaultValue);
    }

    /** Item ID ({@code minecraft:diamond}): item suggestions, icon, item picker and preview. */
    public OptionBuilder<String> item(String key, String defaultItem) {
        return identifier(key, defaultItem).item();
    }

    /** Block ID ({@code minecraft:stone}): block suggestions, icon, block picker and preview. */
    public OptionBuilder<String> block(String key, String defaultBlock) {
        return identifier(key, defaultBlock).block();
    }

    /** Key ({@code key.keyboard.g}, {@code key.mouse.right}...), edited by pressing it. */
    public OptionBuilder<String> keybind(String key, String defaultKey) {
        OptionBuilder<String> builder = string(key, defaultKey);
        builder.delegate.widgetHint(fr.heetstudio.confee.core.WidgetHint.KEYBIND);
        return builder;
    }

    /** Several values of an enum, shown as check boxes. The value is ordered like the enum, without duplicates. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <E extends Enum<E>> OptionBuilder<List<E>> enumSet(String key, Class<E> enumClass, java.util.Collection<E> defaultValues) {
        List<E> sorted = defaultValues.stream().distinct().sorted(java.util.Comparator.comparingInt(Enum::ordinal)).toList();
        OptionBuilder<List<E>> builder = new OptionBuilder<>(this, key, OptionType.LIST, sorted);
        builder.delegate.elementType(OptionType.ENUM);
        builder.delegate.enumClass((Class) enumClass);
        builder.delegate.widgetHint(fr.heetstudio.confee.core.WidgetHint.CHECKBOXES);
        return builder;
    }

    // ------------------------------------------------------------------ decorations (screen only)

    /** Adds a title, separator, text, image or button after the options declared so far. */
    public SectionBuilder decoration(fr.heetstudio.confee.api.Decoration decoration) {
        owner.checkNotBuilt();
        section.addDecoration(java.util.Objects.requireNonNull(decoration));
        return this;
    }

    public SectionBuilder header(String text) {
        return decoration(fr.heetstudio.confee.api.Decoration.header(text));
    }

    public SectionBuilder separator() {
        return decoration(fr.heetstudio.confee.api.Decoration.separator());
    }

    public SectionBuilder info(String text) {
        return decoration(fr.heetstudio.confee.api.Decoration.info(text));
    }

    public SectionBuilder image(String texture, int height) {
        return decoration(fr.heetstudio.confee.api.Decoration.image(texture, height));
    }

    /** Button running {@code action} on the client (never on a dedicated server). */
    public SectionBuilder button(String text, Runnable action) {
        return decoration(fr.heetstudio.confee.api.Decoration.button(text, action));
    }

    /** Creates (or reopens) a category / sub-section. */
    public CategoryBuilder category(String key) {
        owner.checkNotBuilt();
        if (key == null || key.isBlank() || key.contains(".")) {
            throw new IllegalArgumentException("[Confee] Invalid category key '" + key + "'");
        }
        Section sub = section.find(key)
                .map(node -> {
                    if (node instanceof Section existing) {
                        return existing;
                    }
                    throw new IllegalArgumentException("[Confee] Category '" + key + "' has the same key as an option");
                })
                .orElseGet(() -> section.add(new Section(key, null, null)));
        return new CategoryBuilder(owner, sub);
    }

    /** Declares a category's options in a block. */
    public SectionBuilder category(String key, Consumer<CategoryBuilder> body) {
        body.accept(category(key));
        return this;
    }
}

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

    /**
     * <p>Example: {@code b.bool("showHud", true).build()}</p>
     */
    public OptionBuilder<Boolean> bool(String key, boolean defaultValue) {
        return new OptionBuilder<>(this, key, OptionType.BOOLEAN, defaultValue);
    }

    /**
     * <p>Example: {@code b.intValue("lives", 3).build()}</p>
     */
    public OptionBuilder<Integer> intValue(String key, int defaultValue) {
        return new OptionBuilder<>(this, key, OptionType.INT, defaultValue);
    }

    /**
     * <p>Example: {@code b.intRange("volume", 70, 0, 100).slider(5).build()}</p>
     */
    public OptionBuilder<Integer> intRange(String key, int defaultValue, int min, int max) {
        return intValue(key, defaultValue).range(min, max);
    }

    /**
     * <p>Example: {@code b.longValue("seed", 0L).build()}</p>
     */
    public OptionBuilder<Long> longValue(String key, long defaultValue) {
        return new OptionBuilder<>(this, key, OptionType.LONG, defaultValue);
    }

    /**
     * <p>Example: {@code b.longRange("cooldownMs", 500L, 0L, 10_000L).build()}</p>
     */
    public OptionBuilder<Long> longRange(String key, long defaultValue, long min, long max) {
        return longValue(key, defaultValue).range(min, max);
    }

    /**
     * <p>Example: {@code b.floatValue("scale", 1.0f).build()}</p>
     */
    public OptionBuilder<Float> floatValue(String key, float defaultValue) {
        return new OptionBuilder<>(this, key, OptionType.FLOAT, defaultValue);
    }

    /**
     * <p>Example: {@code b.floatRange("opacity", 0.8f, 0f, 1f).slider(0.05).build()}</p>
     */
    public OptionBuilder<Float> floatRange(String key, float defaultValue, float min, float max) {
        return floatValue(key, defaultValue).range(min, max);
    }

    /**
     * <p>Example: {@code b.doubleValue("ratio", 0.25).build()}</p>
     */
    public OptionBuilder<Double> doubleValue(String key, double defaultValue) {
        return new OptionBuilder<>(this, key, OptionType.DOUBLE, defaultValue);
    }

    /**
     * <p>Example: {@code b.doubleRange("distance", 4.0, 1.0, 10.0).build()}</p>
     */
    public OptionBuilder<Double> doubleRange(String key, double defaultValue, double min, double max) {
        return doubleValue(key, defaultValue).range(min, max);
    }

    /**
     * <p>Example: {@code b.string("title", "Hello").build()}</p>
     */
    public OptionBuilder<String> string(String key, String defaultValue) {
        return new OptionBuilder<>(this, key, OptionType.STRING, defaultValue);
    }

    /**
     * Enum option, shown as a dropdown.
     * <p>Example: {@code b.enumValue("difficulty", Difficulty.NORMAL).build()}</p>
     */
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
    /**
     * <p>Example: {@code b.list("numbers", Integer.class, List.of(1, 2, 3)).build()}</p>
     */
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
     * List of IDs of a registry, with suggestions (and icons for items and blocks). No duplicates, like every list.
     * <pre>{@code b.idList("mobs", "minecraft:entity_type", "minecraft:zombie").build();}</pre>
     * <p>Example: {@code b.idList("mobs", "minecraft:entity_type", "minecraft:zombie").build()}</p>
     */
    public OptionBuilder<List<String>> idList(String key, String registry, String... defaultValues) {
        return list(key, String.class, List.of(defaultValues)).registry(registry);
    }

    /**
     * List of item IDs: icons, suggestions and the item picker. Add {@code .tags()} to accept {@code #tags}.
     * <p>Example: {@code b.itemList("loot", "minecraft:diamond", "minecraft:emerald").build()}</p>
     */
    public OptionBuilder<List<String>> itemList(String key, String... defaultValues) {
        return list(key, String.class, List.of(defaultValues)).item();
    }

    /**
     * List of block IDs: icons, suggestions and the block picker. Add {@code .tags()} to accept {@code #tags}.
     * <p>Example: {@code b.blockList("blacklist", "minecraft:dirt").tags().build()}</p>
     */
    public OptionBuilder<List<String>> blockList(String key, String... defaultValues) {
        return list(key, String.class, List.of(defaultValues)).block();
    }

    /**
     * List kept sorted and without duplicates (a set the player edits as a list).
     * <p>Example: {@code b.sortedList("labels", String.class, List.of("alpha", "beta")).build()}</p>
     */
    public <E> OptionBuilder<List<E>> sortedList(String key, Class<E> elementType, List<E> defaultValue) {
        return list(key, elementType, defaultValue).sorted();
    }

    /**
     * Map with keys chosen by the player.
     *
     * @param valueType String, Integer, Long, Float, Double, Boolean or an enum
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * <p>Example: {@code b.map("biomeWeights", Integer.class, Map.of("plains", 10)).build()}</p>
     */
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

    /**
     * List of objects with the fields of {@code spec}; elements are maps (see {@link ObjectSpec#element}).
     * <p>Example: {@code b.objectList("waypoints", spec, List.of(spec.element("name", "home"))).build()}</p>
     */
    public OptionBuilder<List<Map<String, Object>>> objectList(String key, ObjectSpec spec, List<Map<String, Object>> defaultValue) {
        OptionBuilder<List<Map<String, Object>>> builder = new OptionBuilder<>(this, key, OptionType.OBJECT_LIST,
                List.copyOf(defaultValue));
        builder.delegate.objectFields(spec.fields());
        return builder;
    }

    /**
     * ARGB color option.
     * <p>Example: {@code b.color("tint", 0xFF3C8527).build()}</p>
     */
    public OptionBuilder<Integer> color(String key, int defaultArgb) {
        return new OptionBuilder<>(this, key, OptionType.COLOR, defaultArgb);
    }

    /**
     * Resource identifier ({@code namespace:path}) stored as a String.
     * <p>Example: {@code b.identifier("structure", "minecraft:village_plains").build()}</p>
     */
    public OptionBuilder<String> identifier(String key, String defaultValue) {
        return new OptionBuilder<>(this, key, OptionType.IDENTIFIER, defaultValue);
    }

    /**
     * Item ID ({@code minecraft:diamond}): item suggestions, icon, item picker and preview.
     * <p>Example: {@code b.item("reward", "minecraft:diamond").build()}</p>
     */
    public OptionBuilder<String> item(String key, String defaultItem) {
        return identifier(key, defaultItem).item();
    }

    /**
     * Block ID ({@code minecraft:stone}): block suggestions, icon, block picker and preview.
     * <p>Example: {@code b.block("floor", "minecraft:stone").build()}</p>
     */
    public OptionBuilder<String> block(String key, String defaultBlock) {
        return identifier(key, defaultBlock).block();
    }

    /**
     * Key ({@code key.keyboard.g}, {@code key.mouse.right}...), edited by pressing it.
     * <p>Example: {@code b.keybind("dashKey", "key.keyboard.r").build()}</p>
     */
    public OptionBuilder<String> keybind(String key, String defaultKey) {
        OptionBuilder<String> builder = string(key, defaultKey);
        builder.delegate.widgetHint(fr.heetstudio.confee.core.WidgetHint.KEYBIND);
        return builder;
    }

    /** Several values of an enum, shown as check boxes. The value is ordered like the enum, without duplicates. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * <p>Example: {@code b.enumSet("qualities", Quality.class, EnumSet.of(Quality.LOW)).build()}</p>
     */
    public <E extends Enum<E>> OptionBuilder<List<E>> enumSet(String key, Class<E> enumClass, java.util.Collection<E> defaultValues) {
        List<E> sorted = defaultValues.stream().distinct().sorted(java.util.Comparator.comparingInt(Enum::ordinal)).toList();
        OptionBuilder<List<E>> builder = new OptionBuilder<>(this, key, OptionType.LIST, sorted);
        builder.delegate.elementType(OptionType.ENUM);
        builder.delegate.enumClass((Class) enumClass);
        builder.delegate.widgetHint(fr.heetstudio.confee.core.WidgetHint.CHECKBOXES);
        return builder;
    }

    // ------------------------------------------------------------------ decorations (screen only)

    /**
     * Adds a title, separator, text, image or button after the options declared so far.
     * <p>Example: {@code b.decoration(Decoration.warning("Needs a restart."))}</p>
     */
    public SectionBuilder decoration(fr.heetstudio.confee.api.Decoration decoration) {
        owner.checkNotBuilt();
        section.addDecoration(java.util.Objects.requireNonNull(decoration));
        return this;
    }

    /**
     * <p>Example: {@code b.header("Collections")}</p>
     */
    public SectionBuilder header(String text) {
        return decoration(fr.heetstudio.confee.api.Decoration.header(text));
    }

    /**
     * <p>Example: {@code b.separator()}</p>
     */
    public SectionBuilder separator() {
        return decoration(fr.heetstudio.confee.api.Decoration.separator());
    }

    /**
     * <p>Example: {@code b.info("These options only change the HUD.")}</p>
     */
    public SectionBuilder info(String text) {
        return decoration(fr.heetstudio.confee.api.Decoration.info(text));
    }

    /**
     * <p>Example: {@code b.image("examplemod:textures/gui/hud.png", 48)}</p>
     */
    public SectionBuilder image(String texture, int height) {
        return decoration(fr.heetstudio.confee.api.Decoration.image(texture, height));
    }

    /**
     * Button running {@code action} on the client (never on a dedicated server).
     * <p>Example: {@code b.button("Open the wiki", () -> Util.getPlatform().openUri(WIKI))}</p>
     */
    public SectionBuilder button(String text, Runnable action) {
        return decoration(fr.heetstudio.confee.api.Decoration.button(text, action));
    }

    /**
     * Creates (or reopens) a category / sub-section.
     * <p>Example: {@code CategoryBuilder dash = b.category("dash").icon("minecraft:feather");}</p>
     */
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

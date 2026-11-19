package fr.heetstudio.confee.api.builder;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.api.ChangeListener;
import fr.heetstudio.confee.api.ConfigValue;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.OptionType;
import fr.heetstudio.confee.core.WidgetHint;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes one option of a builder config (spec 6.2). Finish with {@link #build()}.
 *
 * @param <T> value type
 */
public final class OptionBuilder<T> {

    private final SectionBuilder parent;
    private final String key;
    private final T defaultValue;
    final Option.Builder<T> delegate;
    private final List<ChangeListener<T>> listeners = new ArrayList<>();
    private boolean built;

    OptionBuilder(SectionBuilder parent, String key, OptionType type, T defaultValue) {
        parent.owner.checkNotBuilt();
        this.parent = parent;
        this.key = key;
        this.defaultValue = defaultValue;
        this.delegate = Option.<T>builder(key, type).defaultValue(defaultValue);
    }

    /**
     * Comment written in the file, also the tooltip when there is no translation.
     * <p>Example: {@code b.bool("showHud", true).comment("Show the HUD").build()}</p>
     */
    public OptionBuilder<T> comment(String comment) {
        delegate.comment(comment);
        return this;
    }

    /**
     * Display name used when there is no translation key.
     * <p>Example: {@code b.intValue("hudOpacity", 80).name("HUD opacity").build()}</p>
     */
    public OptionBuilder<T> name(String name) {
        delegate.displayName(name);
        return this;
    }

    /**
     * Bounds of a numeric option (either may be null).
     * <p>Example: {@code b.intValue("lives", 3).range(1, 9).build()  // on a list of numbers: each value}</p>
     */
    public OptionBuilder<T> range(Number min, Number max) {
        delegate.range(min, max);
        return this;
    }

    /**
     * Shows the option as a slider; needs both bounds.
     * <p>Example: {@code b.floatRange("opacity", 0.8f, 0f, 1f).slider(0.05).build()}</p>
     */
    public OptionBuilder<T> slider(double step) {
        delegate.widgetHint(WidgetHint.SLIDER).step(step);
        return this;
    }

    /**
     * <p>Example: {@code b.bool("fancyWorldgen", false).requiresRestart().build()}</p>
     */
    public OptionBuilder<T> requiresRestart() {
        delegate.requiresRestart(true);
        return this;
    }

    /**
     * Saved in the file but not shown on the screen.
     * <p>Example: {@code b.intValue("configVersion", 2).hidden().build()}</p>
     */
    public OptionBuilder<T> hidden() {
        delegate.hidden(true);
        return this;
    }

    /**
     * Regular expression a String option must fully match.
     * <p>Example: {@code b.string("code", "abc").pattern("[a-z_]+").build()  // on a List<String>: each value}</p>
     */
    public OptionBuilder<T> pattern(String regex) {
        delegate.pattern(regex);
        return this;
    }

    /**
     * <p>Example: {@code b.string("nickname", "Steve").maxLength(16).build()  // on a List<String>: each value}</p>
     */
    public OptionBuilder<T> maxLength(int maxLength) {
        delegate.maxLength(maxLength);
        return this;
    }

    /**
     * For a color option: allow editing the alpha channel.
     * <p>Example: {@code b.color("overlay", 0x802D7DD2).alpha().build()}</p>
     */
    public OptionBuilder<T> alpha() {
        delegate.colorAlpha(true);
        return this;
    }

    /**
     * Preview shown in the third column of the screen (optional).
     * <p>Example: {@code b.enumValue("quality", Quality.HIGH).preview(Preview.image("examplemod:textures/q_{value}.png")).build()}</p>
     */
    public OptionBuilder<T> preview(fr.heetstudio.confee.api.Preview preview) {
        delegate.preview(preview);
        return this;
    }

    /**
     * Editable only when the condition holds, grayed out otherwise. Condition: {@code "path"} (boolean option is
     * true), {@code "!path"} or {@code "path=VALUE"}; {@code path} is the dotted path of an option of this config.
     * <p>Example: {@code b.intValue("hudOpacity", 80).enabledIf("showHud").build()}</p>
     */
    public OptionBuilder<T> enabledIf(String condition) {
        delegate.enabledIf(condition);
        return this;
    }

    /**
     * Shown only when the condition holds (same syntax as {@link #enabledIf}).
     * <p>Example: {@code b.bool("insaneWarning", true).visibleIf("quality=INSANE").build()}</p>
     */
    public OptionBuilder<T> visibleIf(String condition) {
        delegate.visibleIf(condition);
        return this;
    }

    /**
     * Unit shown after the value of a number, e.g. "chunks" (plain text or translation key).
     * <p>Example: {@code b.intValue("renderDistance", 16).unit("chunks").build()}</p>
     */
    public OptionBuilder<T> unit(String unit) {
        delegate.unit(unit);
        return this;
    }

    /**
     * For a List option: the same value may appear twice. By default a list never holds the same value twice
     * (duplicates are removed when the file is loaded and when the screen saves).
     * <p>Example: {@code b.list("diceRolls", Integer.class, List.of(6, 6, 1)).allowDuplicates().build()}</p>
     */
    public OptionBuilder<T> allowDuplicates() {
        delegate.allowDuplicates(true);
        return this;
    }

    /**
     * For a List option: number of values allowed. The screen greys out "+ Add" at {@code max} and the remove buttons
     * at {@code min}.
     * <p>Example: {@code b.list("homes", String.class, List.of("spawn")).size(1, 5).build()}</p>
     */
    public OptionBuilder<T> size(int min, int max) {
        delegate.size(min, max);
        return this;
    }

    /**
     * For a List option: at most {@code max} values.
     * <p>Example: {@code b.itemList("hotbar", "minecraft:torch").maxSize(9).build()}</p>
     */
    public OptionBuilder<T> maxSize(int max) {
        delegate.size(0, max);
        return this;
    }

    /**
     * For a List option: kept sorted (natural order, enums in declaration order); no move arrows in the screen.
     * <p>Example: {@code b.list("labels", String.class, List.of("b", "a")).sorted().build()}</p>
     */
    public OptionBuilder<T> sorted() {
        delegate.sorted(true);
        return this;
    }

    /**
     * For a List of String: "Dirt" and "dirt" are the same value when looking for duplicates.
     * <p>Example: {@code b.list("bannedWords", String.class, List.of("spam")).ignoreCase().build()}</p>
     */
    public OptionBuilder<T> ignoreCase() {
        delegate.ignoreCase(true);
        return this;
    }

    /**
     * Multi-line text area for a String option.
     * <p>Example: {@code b.string("motd", "Welcome!").multiline().build()}</p>
     */
    public OptionBuilder<T> multiline() {
        delegate.widgetHint(fr.heetstudio.confee.core.WidgetHint.MULTILINE);
        return this;
    }

    /**
     * Registry used to suggest values, e.g. "minecraft:entity_type" (identifier, String or List of String options).
     * For items and blocks, prefer {@link #item()} / {@link #block()}.
     * <p>Example: {@code b.list("mobs", String.class, List.of()).registry("minecraft:entity_type").build()}</p>
     */
    public OptionBuilder<T> registry(String registry) {
        delegate.registry(registry);
        return this;
    }

    /**
     * The value is an item ID: item suggestions, icon, item picker and automatic preview.
     * <p>Example: {@code b.string("reward", "minecraft:diamond").item().build()}</p>
     */
    public OptionBuilder<T> item() {
        return registry("minecraft:item");
    }

    /**
     * The value is a block ID: block suggestions, icon, block picker and automatic preview.
     * <p>Example: {@code b.string("floor", "minecraft:stone").block().build()}</p>
     */
    public OptionBuilder<T> block() {
        return registry("minecraft:block");
    }

    /**
     * Entity type ID ({@code minecraft:zombie}): suggestions, spawn egg icons, picker.
     * <p>Example: {@code b.string("favoriteMob", "minecraft:axolotl").entity().build()}</p>
     */
    public OptionBuilder<T> entity() {
        return registry("minecraft:entity_type");
    }

    /**
     * Biome ID ({@code minecraft:plains}): from the world when one is loaded, else from the game and mod files.
     * <p>Example: {@code b.list("spawnBiomes", String.class, List.of("minecraft:plains")).biome().tags().build()}</p>
     */
    public OptionBuilder<T> biome() {
        return registry("minecraft:worldgen/biome");
    }

    /**
     * Dimension ID ({@code minecraft:the_nether}).
     * <p>Example: {@code b.string("home", "minecraft:overworld").dimension().build()}</p>
     */
    public OptionBuilder<T> dimension() {
        return registry("minecraft:dimension");
    }

    /**
     * Structure ID ({@code minecraft:village_plains}).
     * <p>Example: {@code b.string("target", "minecraft:village_plains").structure().build()}</p>
     */
    public OptionBuilder<T> structure() {
        return registry("minecraft:worldgen/structure");
    }

    /**
     * Enchantment ID ({@code minecraft:sharpness}): enchanted book icons, picker.
     * <p>Example: {@code b.string("favorite", "minecraft:mending").enchantment().build()}</p>
     */
    public OptionBuilder<T> enchantment() {
        return registry("minecraft:enchantment");
    }

    /**
     * Status effect ID ({@code minecraft:speed}): effect icons, picker.
     * <p>Example: {@code b.string("bonus", "minecraft:speed").effect().build()}</p>
     */
    public OptionBuilder<T> effect() {
        return registry("minecraft:mob_effect");
    }

    /**
     * Sound ID ({@code minecraft:block.note_block.bell}).
     * <p>Example: {@code b.string("alert", "minecraft:block.note_block.bell").sound().build()}</p>
     */
    public OptionBuilder<T> sound() {
        return registry("minecraft:sound_event");
    }

    /**
     * Fluid ID ({@code minecraft:water}): bucket icons, picker.
     * <p>Example: {@code b.string("liquid", "minecraft:water").fluid().build()}</p>
     */
    public OptionBuilder<T> fluid() {
        return registry("minecraft:fluid");
    }

    /**
     * Also suggest and accept tags of the registry ({@code #minecraft:logs}). Needs a registry
     * ({@link #registry(String)} or a shortcut such as {@link #item()}).
     * <p>Example: {@code b.blockList("breakable", "#minecraft:logs").tags().build()}</p>
     */
    public OptionBuilder<T> tags() {
        delegate.allowTags(true);
        return this;
    }

    /**
     * Text shown under the name on the screen (plain text or translation key). Not written in the file: see
     * {@link #comment(String)}. Without it, the translation {@code config.<modId>.<path>.description} is used.
     * <p>Example: {@code b.bool("showHud", true).description("Shown under the name.").build()}</p>
     */
    public OptionBuilder<T> description(String description) {
        delegate.description(description);
        return this;
    }

    /**
     * Values suggested while typing (String, identifier or List of String options).
     * <p>Example: {@code b.string("direction", "north").suggestions("north", "south", "east", "west").build()}</p>
     */
    public OptionBuilder<T> suggestions(String... values) {
        java.util.List<String> fixed = java.util.List.of(values);
        delegate.suggestions(() -> fixed);
        return this;
    }

    /** Values suggested while typing, computed on the client when the screen opens. */
    public OptionBuilder<T> suggestions(fr.heetstudio.confee.api.SuggestionProvider provider) {
        delegate.suggestions(provider);
        return this;
    }

    /**
     * Number field with − and + buttons ({@code step} per click, Shift = 10 steps).
     * <p>Example: {@code b.intRange("lives", 5, 0, 99).stepper(1).build()}</p>
     */
    public OptionBuilder<T> stepper(double step) {
        delegate.widgetHint(WidgetHint.STEPPER).step(step);
        return this;
    }

    /**
     * <p>Example: {@code b.bool("showHud", true).listener((oldValue, newValue) -> refreshHud()).build()}</p>
     */
    public OptionBuilder<T> listener(ChangeListener<T> listener) {
        listeners.add(listener);
        return this;
    }

    /**
     * Adds the option to its category and returns its handle.
     *
     * @throws IllegalStateException in a development environment when the declaration is invalid.
     *                               In production the problem is logged and a detached value holding the default
     *                               is returned, so the mod keeps working (spec 14).
     * <p>Example: {@code ConfigValue<Boolean> showHud = b.bool("showHud", true).build(); showHud.get();}</p>
     */
    public ConfigValue<T> build() {
        parent.owner.checkNotBuilt();
        if (built) {
            throw new IllegalStateException("[Confee] Option '" + key + "' was already built");
        }
        built = true;
        Option<T> option;
        try {
            option = delegate.build();
            parent.section.add(option);
        } catch (RuntimeException e) {
            if (parent.owner.environment().development()) {
                throw new IllegalStateException("[Confee] Invalid option '" + key + "' in config '"
                        + parent.owner.modId() + "': " + e.getMessage(), e);
            }
            ConfeeLog.warn("Ignoring option '{}' of config '{}': {}", key, parent.owner.modId(), e.getMessage());
            return new DetachedValue<>(defaultValue);
        }
        listeners.forEach(option::addListener);
        return option;
    }

    /** Value of an option that could not be declared: keeps its default, never saved. */
    private static final class DetachedValue<T> implements ConfigValue<T> {
        private T value;
        private final T defaultValue;

        DetachedValue(T defaultValue) {
            this.value = defaultValue;
            this.defaultValue = defaultValue;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public void set(T value) {
            this.value = value;
        }

        @Override
        public T getDefault() {
            return defaultValue;
        }

        @Override
        public void reset() {
            value = defaultValue;
        }

        @Override
        public void addListener(ChangeListener<T> listener) {
            // never saved nor loaded: nothing to notify
        }
    }
}

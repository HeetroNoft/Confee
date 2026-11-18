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

    /** Comment written in the file, also the tooltip when there is no translation. */
    public OptionBuilder<T> comment(String comment) {
        delegate.comment(comment);
        return this;
    }

    /** Display name used when there is no translation key. */
    public OptionBuilder<T> name(String name) {
        delegate.displayName(name);
        return this;
    }

    /** Bounds of a numeric option (either may be null). */
    public OptionBuilder<T> range(Number min, Number max) {
        delegate.range(min, max);
        return this;
    }

    /** Shows the option as a slider; needs both bounds. */
    public OptionBuilder<T> slider(double step) {
        delegate.widgetHint(WidgetHint.SLIDER).step(step);
        return this;
    }

    public OptionBuilder<T> requiresRestart() {
        delegate.requiresRestart(true);
        return this;
    }

    /** Saved in the file but not shown on the screen. */
    public OptionBuilder<T> hidden() {
        delegate.hidden(true);
        return this;
    }

    /** Regular expression a String option must fully match. */
    public OptionBuilder<T> pattern(String regex) {
        delegate.pattern(regex);
        return this;
    }

    public OptionBuilder<T> maxLength(int maxLength) {
        delegate.maxLength(maxLength);
        return this;
    }

    /** For a color option: allow editing the alpha channel. */
    public OptionBuilder<T> alpha() {
        delegate.colorAlpha(true);
        return this;
    }

    /** Preview shown in the third column of the screen (optional). */
    public OptionBuilder<T> preview(fr.heetstudio.confee.api.Preview preview) {
        delegate.preview(preview);
        return this;
    }

    /**
     * Editable only when the condition holds, grayed out otherwise. Condition: {@code "path"} (boolean option is
     * true), {@code "!path"} or {@code "path=VALUE"}; {@code path} is the dotted path of an option of this config.
     */
    public OptionBuilder<T> enabledIf(String condition) {
        delegate.enabledIf(condition);
        return this;
    }

    /** Shown only when the condition holds (same syntax as {@link #enabledIf}). */
    public OptionBuilder<T> visibleIf(String condition) {
        delegate.visibleIf(condition);
        return this;
    }

    /** Unit shown after the value of a number, e.g. "chunks" (plain text or translation key). */
    public OptionBuilder<T> unit(String unit) {
        delegate.unit(unit);
        return this;
    }

    /** Multi-line text area for a String option. */
    public OptionBuilder<T> multiline() {
        delegate.widgetHint(fr.heetstudio.confee.core.WidgetHint.MULTILINE);
        return this;
    }

    /**
     * Registry used to suggest values, e.g. "minecraft:entity_type" (identifier, String or List of String options).
     * For items and blocks, prefer {@link #item()} / {@link #block()}.
     */
    public OptionBuilder<T> registry(String registry) {
        delegate.registry(registry);
        return this;
    }

    /** The value is an item ID: item suggestions, icon, item picker and automatic preview. */
    public OptionBuilder<T> item() {
        return registry("minecraft:item");
    }

    /** The value is a block ID: block suggestions, icon, block picker and automatic preview. */
    public OptionBuilder<T> block() {
        return registry("minecraft:block");
    }

    /** Entity type ID ({@code minecraft:zombie}): suggestions, spawn egg icons, picker. */
    public OptionBuilder<T> entity() {
        return registry("minecraft:entity_type");
    }

    /** Biome ID ({@code minecraft:plains}): from the world when one is loaded, else from the game and mod files. */
    public OptionBuilder<T> biome() {
        return registry("minecraft:worldgen/biome");
    }

    /** Dimension ID ({@code minecraft:the_nether}). */
    public OptionBuilder<T> dimension() {
        return registry("minecraft:dimension");
    }

    /** Structure ID ({@code minecraft:village_plains}). */
    public OptionBuilder<T> structure() {
        return registry("minecraft:worldgen/structure");
    }

    /** Enchantment ID ({@code minecraft:sharpness}): enchanted book icons, picker. */
    public OptionBuilder<T> enchantment() {
        return registry("minecraft:enchantment");
    }

    /** Status effect ID ({@code minecraft:speed}): effect icons, picker. */
    public OptionBuilder<T> effect() {
        return registry("minecraft:mob_effect");
    }

    /** Sound ID ({@code minecraft:block.note_block.bell}). */
    public OptionBuilder<T> sound() {
        return registry("minecraft:sound_event");
    }

    /** Fluid ID ({@code minecraft:water}): bucket icons, picker. */
    public OptionBuilder<T> fluid() {
        return registry("minecraft:fluid");
    }

    /**
     * Also suggest and accept tags of the registry ({@code #minecraft:logs}). Needs a registry
     * ({@link #registry(String)} or a shortcut such as {@link #item()}).
     */
    public OptionBuilder<T> tags() {
        delegate.allowTags(true);
        return this;
    }

    /**
     * Text shown under the name on the screen (plain text or translation key). Not written in the file: see
     * {@link #comment(String)}. Without it, the translation {@code config.<modId>.<path>.description} is used.
     */
    public OptionBuilder<T> description(String description) {
        delegate.description(description);
        return this;
    }

    /** Values suggested while typing (String, identifier or List of String options). */
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

    /** Number field with − and + buttons ({@code step} per click, Shift = 10 steps). */
    public OptionBuilder<T> stepper(double step) {
        delegate.widgetHint(WidgetHint.STEPPER).step(step);
        return this;
    }

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

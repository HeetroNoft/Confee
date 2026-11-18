package fr.heetstudio.confee.core;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.api.ChangeListener;
import fr.heetstudio.confee.api.ConfigValue;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * A single config option (spec 5.1). Built with {@link #builder(String, OptionType)}.
 * <p>Internal: may change without notice. Mods use it through {@link ConfigValue}.</p>
 *
 * @param <T> value type: Boolean, Integer, Long, Float, Double, String, an enum, List, Integer (COLOR)
 *            or String (IDENTIFIER)
 */
public final class Option<T> implements Node, ConfigValue<T> {

    private final String key;
    private final OptionType type;
    @Nullable
    private final T defaultValue;
    private final ValueStore<T> store;
    @Nullable
    private final Number min;
    @Nullable
    private final Number max;
    @Nullable
    private final Number step;
    @Nullable
    private final List<T> allowedValues;
    @Nullable
    private final Class<? extends Enum<?>> enumClass;
    @Nullable
    private final OptionType elementType;
    @Nullable
    private final Pattern pattern;
    private final int maxLength;
    private final boolean colorAlpha;
    @Nullable
    private final String displayName;
    @Nullable
    private final String comment;
    private final boolean requiresRestart;
    private final boolean hidden;
    private final boolean readOnly;
    @Nullable
    private final java.util.function.Predicate<Object> validator;
    @Nullable
    private final fr.heetstudio.confee.api.Preview preview;
    @Nullable
    private final String enabledIf;
    @Nullable
    private final String visibleIf;
    @Nullable
    private final String unit;
    @Nullable
    private final String registry;
    private final List<ObjectField> objectFields;
    /** Tags of the registry ({@code #ns:path}) are accepted and suggested. */
    private final boolean allowTags;
    @Nullable
    private final String description;
    @Nullable
    private final fr.heetstudio.confee.api.SuggestionProvider suggestions;
    /** Supplier of a client-only {@code OptionWidget}, or null. Typed loosely: this class is loaded on servers. */
    @Nullable
    private final java.util.function.Supplier<?> widgetFactory;
    private final WidgetHint widgetHint;
    private final List<ChangeListener<T>> listeners = new CopyOnWriteArrayList<>();
    private String path;

    private Option(Builder<T> b) {
        this.key = b.key;
        this.type = b.type;
        this.defaultValue = b.defaultValue;
        this.store = b.store != null ? b.store : ValueStore.memory(b.defaultValue);
        this.min = b.min;
        this.max = b.max;
        this.step = b.step;
        this.allowedValues = b.allowedValues == null ? null : List.copyOf(b.allowedValues);
        this.enumClass = b.enumClass;
        this.elementType = b.elementType;
        this.pattern = b.pattern;
        this.maxLength = b.maxLength;
        this.colorAlpha = b.colorAlpha;
        this.displayName = b.displayName;
        this.comment = b.comment;
        this.requiresRestart = b.requiresRestart;
        this.hidden = b.hidden;
        this.readOnly = b.readOnly;
        this.validator = b.validator;
        this.preview = b.preview;
        this.enabledIf = b.enabledIf;
        this.visibleIf = b.visibleIf;
        this.unit = b.unit;
        this.registry = b.registry;
        this.objectFields = b.objectFields == null ? List.of() : List.copyOf(b.objectFields);
        this.widgetHint = b.widgetHint;
        this.description = b.description;
        this.allowTags = b.allowTags;
        this.suggestions = b.suggestions;
        this.widgetFactory = b.widgetFactory;
        this.path = b.key;
    }

    public static <T> Builder<T> builder(String key, OptionType type) {
        return new Builder<>(key, type);
    }

    // ---------------------------------------------------------------- ConfigValue

    @Override
    public T get() {
        return store.get();
    }

    @Override
    public void set(T value) {
        Validated<T> result = validate(value);
        if (result.status() == Validated.Status.REPLACED_BY_DEFAULT) {
            ConfeeLog.warn("Rejected value {} for option '{}': {}", value, path, result.message());
            return;
        }
        if (result.status() == Validated.Status.CORRECTED) {
            ConfeeLog.warn("Option '{}': {}", path, result.message());
        }
        apply(result.value());
    }

    @Override
    @Nullable
    public T getDefault() {
        return defaultValue;
    }

    @Override
    public void reset() {
        if (defaultValue != null) {
            apply(defaultValue);
        }
    }

    @Override
    public void addListener(ChangeListener<T> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    /** Stores an already validated value and notifies listeners if it changed (spec 9.6). */
    public void apply(T newValue) {
        T old = store.get();
        if (Objects.equals(old, newValue)) {
            return;
        }
        store.set(newValue);
        for (ChangeListener<T> listener : listeners) {
            try {
                listener.onChange(old, newValue);
            } catch (RuntimeException e) {
                ConfeeLog.error("Change listener of option '" + path + "' threw an exception", e);
            }
        }
    }

    public boolean isDefault() {
        return defaultValue != null && Objects.equals(defaultValue, store.get());
    }

    // ---------------------------------------------------------------- validation (spec 5.3)

    /**
     * Converts and checks a raw value (from a file, a widget or the API). Never throws.
     * Wrong type → default value; out of bounds → clamped.
     */
    @SuppressWarnings("unchecked")
    public Validated<T> validate(@Nullable Object raw) {
        Object coerced;
        try {
            if (allowTags && type == OptionType.IDENTIFIER && raw instanceof String s && s.startsWith("#")) {
                // a tag: same rules as an identifier, after the '#'
                coerced = "#" + Coercion.coerce(s.substring(1), type, enumClass, elementType, objectFields);
                return (Validated<T>) checkConstraints(coerced);
            }
            coerced = Coercion.coerce(raw, type, enumClass, elementType, objectFields);
        } catch (IllegalArgumentException e) {
            return (Validated<T>) Validated.replaced(defaultValue, e.getMessage());
        }
        return (Validated<T>) checkConstraints(coerced);
    }

    private Validated<?> checkConstraints(Object value) {
        if (widgetHint == WidgetHint.CHECKBOXES && value instanceof List<?> list) {
            // a set of enum values: no duplicate, declaration order
            value = list.stream().distinct().sorted(java.util.Comparator.comparingInt(e -> ((Enum<?>) e).ordinal())).toList();
        }
        if (type.isNumeric() && (min != null || max != null)) {
            return clamp((Number) value);
        }
        if (type == OptionType.STRING) {
            String s = (String) value;
            if (maxLength > 0 && s.length() > maxLength) {
                return Validated.replaced(defaultValue, "text longer than " + maxLength + " characters");
            }
            if (pattern != null && !pattern.matcher(s).matches()) {
                return Validated.replaced(defaultValue, "text does not match " + pattern.pattern());
            }
        }
        if (allowedValues != null && !allowedValues.contains(value)) {
            return Validated.replaced(defaultValue, "'" + value + "' is not one of " + allowedValues);
        }
        if (validator != null && !validator.test(value)) {
            return Validated.replaced(defaultValue, "'" + value + "' is refused by the mod");
        }
        return Validated.ok(value);
    }

    private Validated<?> clamp(Number value) {
        double v = value.doubleValue();
        if (min != null && v < min.doubleValue()) {
            return Validated.corrected(Coercion.toType(min, type),
                    value + " is below the minimum, clamped to " + min);
        }
        if (max != null && v > max.doubleValue()) {
            return Validated.corrected(Coercion.toType(max, type),
                    value + " is above the maximum, clamped to " + max);
        }
        return Validated.ok(value);
    }

    // ---------------------------------------------------------------- accessors

    @Override
    public String key() {
        return key;
    }

    /** Dotted path from the schema root, e.g. {@code dash.distance}. */
    public String path() {
        return path;
    }

    void setPath(String path) {
        this.path = path;
    }

    public OptionType type() {
        return type;
    }

    @Nullable
    public Number min() {
        return min;
    }

    @Nullable
    public Number max() {
        return max;
    }

    @Nullable
    public Number step() {
        return step;
    }

    @Nullable
    public List<T> allowedValues() {
        return allowedValues;
    }

    @Nullable
    public Class<? extends Enum<?>> enumClass() {
        return enumClass;
    }

    @Nullable
    public OptionType elementType() {
        return elementType;
    }

    @Nullable
    public Pattern pattern() {
        return pattern;
    }

    /** Maximum text length, or 0 when unlimited. */
    public int maxLength() {
        return maxLength;
    }

    /** For COLOR options: whether the alpha channel is edited too. */
    public boolean colorAlpha() {
        return colorAlpha;
    }

    @Override
    @Nullable
    public String displayName() {
        return displayName;
    }

    @Override
    @Nullable
    public String comment() {
        return comment;
    }

    public boolean requiresRestart() {
        return requiresRestart;
    }

    public boolean hidden() {
        return hidden;
    }

    /** Optional preview shown in the third column of the screen. */
    @Nullable
    public fr.heetstudio.confee.api.Preview preview() {
        return preview;
    }

    /** Condition ({@code path}, {@code !path}, {@code path=VALUE}) for the option to be editable, or null. */
    @Nullable
    public String enabledIf() {
        return enabledIf;
    }

    /** Condition for the option to be shown, or null. */
    @Nullable
    public String visibleIf() {
        return visibleIf;
    }

    /** Unit shown after the value ({@code chunks}), text or translation key, or null. */
    @Nullable
    public String unit() {
        return unit;
    }

    /** For IDENTIFIER options: registry used for suggestions ({@code minecraft:block}), or null. */
    @Nullable
    public String registry() {
        return registry;
    }

    /** For OBJECT_LIST options: the fields of each element. */
    public List<ObjectField> objectFields() {
        return objectFields;
    }

    /** Shown but not editable (complex values of external files). */
    public boolean readOnly() {
        return readOnly;
    }

    public WidgetHint widgetHint() {
        return widgetHint;
    }

    /** Text shown under the name ({@code @Description}), or null. */
    @Nullable
    public String description() {
        return description;
    }

    /** Values suggested by the mod while typing, or null. */
    @Nullable
    public fr.heetstudio.confee.api.SuggestionProvider suggestions() {
        return suggestions;
    }

    /** Factory of the mod's own widget (a client-only {@code OptionWidget}), or null. */
    @Nullable
    public java.util.function.Supplier<?> widgetFactory() {
        return widgetFactory;
    }

    /** Tags ({@code #ns:path}) of the registry are accepted. */
    public boolean allowTags() {
        return allowTags;
    }

    @Override
    public String toString() {
        return "Option[" + path + "=" + store.get() + "]";
    }

    // ---------------------------------------------------------------- builder

    public static final class Builder<T> {
        private final String key;
        private final OptionType type;
        private T defaultValue;
        private ValueStore<T> store;
        private Number min;
        private Number max;
        private Number step;
        private List<T> allowedValues;
        private Class<? extends Enum<?>> enumClass;
        private OptionType elementType;
        private Pattern pattern;
        private int maxLength;
        private boolean colorAlpha;
        private String displayName;
        private String comment;
        private boolean requiresRestart;
        private boolean hidden;
        private boolean readOnly;
        private java.util.function.Predicate<Object> validator;
        private fr.heetstudio.confee.api.Preview preview;
        private String enabledIf;
        private String visibleIf;
        private String unit;
        private String registry;
        private List<ObjectField> objectFields;
        private WidgetHint widgetHint = WidgetHint.AUTO;
        private String description;
        private boolean allowTags;
        private fr.heetstudio.confee.api.SuggestionProvider suggestions;
        private java.util.function.Supplier<?> widgetFactory;

        private Builder(String key, OptionType type) {
            if (key == null || key.isBlank() || key.contains(".")) {
                throw new IllegalArgumentException("Invalid option key '" + key + "': must be non-empty and contain no '.'");
            }
            this.key = key;
            this.type = Objects.requireNonNull(type);
        }

        public Builder<T> defaultValue(@Nullable T value) {
            this.defaultValue = value;
            return this;
        }

        public Builder<T> store(ValueStore<T> store) {
            this.store = store;
            return this;
        }

        public Builder<T> range(@Nullable Number min, @Nullable Number max) {
            this.min = min;
            this.max = max;
            return this;
        }

        public Builder<T> step(@Nullable Number step) {
            this.step = step;
            return this;
        }

        public Builder<T> allowedValues(@Nullable List<T> values) {
            this.allowedValues = values;
            return this;
        }

        public Builder<T> enumClass(Class<? extends Enum<?>> enumClass) {
            this.enumClass = enumClass;
            return this;
        }

        public Builder<T> elementType(OptionType elementType) {
            this.elementType = elementType;
            return this;
        }

        public Builder<T> pattern(@Nullable String regex) {
            this.pattern = regex == null || regex.isEmpty() ? null : Pattern.compile(regex);
            return this;
        }

        public Builder<T> maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder<T> colorAlpha(boolean alpha) {
            this.colorAlpha = alpha;
            return this;
        }

        public Builder<T> displayName(@Nullable String name) {
            this.displayName = name;
            return this;
        }

        public Builder<T> comment(@Nullable String comment) {
            this.comment = comment;
            return this;
        }

        public Builder<T> requiresRestart(boolean requiresRestart) {
            this.requiresRestart = requiresRestart;
            return this;
        }

        public Builder<T> hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public Builder<T> preview(@Nullable fr.heetstudio.confee.api.Preview preview) {
            this.preview = preview;
            return this;
        }

        public Builder<T> enabledIf(@Nullable String condition) {
            this.enabledIf = condition == null || condition.isBlank() ? null : condition.trim();
            return this;
        }

        public Builder<T> visibleIf(@Nullable String condition) {
            this.visibleIf = condition == null || condition.isBlank() ? null : condition.trim();
            return this;
        }

        public Builder<T> unit(@Nullable String unit) {
            this.unit = unit == null || unit.isBlank() ? null : unit;
            return this;
        }

        public Builder<T> registry(@Nullable String registry) {
            this.registry = registry == null || registry.isBlank() ? null : registry;
            return this;
        }

        public Builder<T> objectFields(List<ObjectField> fields) {
            this.objectFields = fields;
            return this;
        }

        /** Extra check from the source (a Forge spec validator), run after type and bounds. */
        public Builder<T> validator(@Nullable java.util.function.Predicate<Object> validator) {
            this.validator = validator;
            return this;
        }

        public Builder<T> readOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        public Builder<T> description(@Nullable String description) {
            this.description = description == null || description.isBlank() ? null : description;
            return this;
        }

        public Builder<T> allowTags(boolean allowTags) {
            this.allowTags = allowTags;
            return this;
        }

        public Builder<T> suggestions(@Nullable fr.heetstudio.confee.api.SuggestionProvider suggestions) {
            this.suggestions = suggestions;
            return this;
        }

        public Builder<T> widgetFactory(@Nullable java.util.function.Supplier<?> factory) {
            this.widgetFactory = factory;
            return this;
        }

        public Builder<T> widgetHint(WidgetHint hint) {
            this.widgetHint = Objects.requireNonNull(hint);
            return this;
        }

        /**
         * @throws IllegalStateException when the declaration is inconsistent (spec 14: clear message for the dev)
         */
        public Option<T> build() {
            List<String> problems = new ArrayList<>();
            if (type == OptionType.ENUM && enumClass == null) {
                problems.add("ENUM option needs an enum class");
            }
            if (type == OptionType.LIST && elementType == null) {
                problems.add("LIST option needs an element type");
            }
            if (type == OptionType.LIST && elementType == OptionType.LIST) {
                problems.add("nested lists are not supported. Fix: use a list of objects (List<YourSectionClass>)");
            }
            if (type == OptionType.LIST && elementType == OptionType.ENUM && enumClass == null) {
                problems.add("a list of enums needs the enum class");
            }
            if (type == OptionType.MAP && (elementType == null || !ObjectField.ALLOWED.contains(elementType))) {
                problems.add("a map needs scalar values (String, Integer, Long, Float, Double, Boolean or an enum)");
            }
            if (type == OptionType.MAP && elementType == OptionType.ENUM && enumClass == null) {
                problems.add("a map of enums needs the enum class");
            }
            if (type == OptionType.OBJECT_LIST && (objectFields == null || objectFields.isEmpty())) {
                problems.add("a list of objects needs at least one field");
            }
            boolean textual = type == OptionType.IDENTIFIER || type == OptionType.STRING
                    || (type == OptionType.LIST && elementType == OptionType.STRING);
            if (widgetHint == WidgetHint.MULTILINE && type != OptionType.STRING) {
                problems.add("@Multiline / multiline() is only allowed on String options");
            }
            if (registry != null && !textual) {
                problems.add("@Registry, @Item and @Block need a ResourceLocation, String or List<String> value");
            }
            if (allowTags && registry == null) {
                problems.add("tags need a registry. Fix: use @Registry(..., tags = true) or a shortcut such as @Item(tags = true)");
            }
            if (registry != null && !registry.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+")) {
                problems.add("'" + registry + "' is not a valid registry ID. Fix: e.g. \"minecraft:entity_type\" or \"minecraft:worldgen/biome\"");
            }
            if (suggestions != null && !textual) {
                problems.add("suggestions need a ResourceLocation, String or List<String> value. Fix: for a fixed choice, use an enum");
            }
            if (widgetHint == WidgetHint.STEPPER && !type.isNumeric()) {
                problems.add("@Stepper / stepper() is only allowed on numbers");
            }
            if (widgetHint == WidgetHint.KEYBIND && type != OptionType.STRING) {
                problems.add("@Keybind / keybind() needs a String value such as \"key.keyboard.g\"");
            }
            if (widgetHint == WidgetHint.CHECKBOXES && (type != OptionType.LIST || elementType != OptionType.ENUM)) {
                problems.add("check boxes need a set of enum values (Set<YourEnum> / EnumSet<YourEnum>, or enumSet() in the builder)");
            }
            if ((min != null || max != null) && !type.isNumeric()) {
                problems.add("a range (@Range / range()) is only allowed on numbers");
            }
            if (min != null && max != null && min.doubleValue() > max.doubleValue()) {
                problems.add("min (" + min + ") is greater than max (" + max + ")");
            }
            if (widgetHint == WidgetHint.SLIDER && (min == null || max == null)) {
                problems.add("a slider needs both bounds. Fix: add @Range(min = ..., max = ...) or use intRange()/doubleRange()");
            }
            if (step != null && step.doubleValue() <= 0) {
                problems.add("step must be positive");
            }
            if ((pattern != null || maxLength > 0) && type != OptionType.STRING) {
                problems.add("@Pattern / @MaxLength are only allowed on String options");
            }
            if (!problems.isEmpty()) {
                throw new IllegalStateException("Invalid option '" + key + "': " + String.join("; ", problems));
            }
            Option<T> option = new Option<>(this);
            if (defaultValue != null) {
                Validated<T> check = option.validate(defaultValue);
                if (!check.isOk()) {
                    throw new IllegalStateException("Invalid default value for option '" + key + "': " + check.message());
                }
            }
            return option;
        }
    }
}

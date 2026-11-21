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
    /** The mod's own check ({@code @Validator}); on a list of simple values, each value. */
    @Nullable
    private final fr.heetstudio.confee.api.ValueValidator<Object> modValidator;
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
    private final boolean allowDuplicates;
    private final boolean reorderable;
    private final List<Enum<?>> filters;
    private final List<fr.heetstudio.confee.api.PickerStyle> pickerStyles;
    private final fr.heetstudio.confee.api.PickerStyle pickerDefault;
    private final int minSize;
    private final int maxSize;
    private final boolean sorted;
    private final boolean ignoreCase;
    @Nullable
    private final Number elementMin;
    @Nullable
    private final Number elementMax;
    @Nullable
    private final Pattern elementPattern;
    private final int elementMaxLength;
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
        this.modValidator = b.modValidator;
        this.preview = b.preview;
        this.enabledIf = b.enabledIf;
        this.visibleIf = b.visibleIf;
        this.unit = b.unit;
        this.registry = b.registry;
        this.objectFields = b.objectFields == null ? List.of() : List.copyOf(b.objectFields);
        this.widgetHint = b.widgetHint;
        this.allowDuplicates = b.allowDuplicates;
        this.reorderable = b.reorderable;
        this.filters = List.copyOf(b.filters);
        this.pickerStyles = List.copyOf(b.pickerStyles);
        this.pickerDefault = b.pickerDefault;
        this.minSize = b.minSize;
        this.maxSize = b.maxSize;
        this.sorted = b.sorted;
        this.ignoreCase = b.ignoreCase;
        this.elementMin = b.elementMin;
        this.elementMax = b.elementMax;
        this.elementPattern = b.elementPattern;
        this.elementMaxLength = b.elementMaxLength;
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
        List<String> fixes = new ArrayList<>();
        if (type == OptionType.LIST && value instanceof List<?> list) {
            List<Object> elements = new ArrayList<>(list.size());
            for (Object element : list) {
                Validated<Object> check = checkElement(element);
                if (check.status() == Validated.Status.REPLACED_BY_DEFAULT) {
                    return Validated.replaced(defaultValue, check.message());
                }
                if (check.status() == Validated.Status.CORRECTED) {
                    fixes.add(check.message());
                }
                elements.add(check.value());
            }
            List<?> result = elements;
            if (!allowDuplicates && hasDuplicates(result)) {
                result = withoutDuplicates(result);
                fixes.add("duplicate values removed");
            }
            if (sorted) {
                List<?> ordered = sortedCopy(result);
                if (!ordered.equals(result)) {
                    result = ordered;
                    fixes.add("values sorted");
                }
            }
            if (result.size() > maxSize) {
                result = result.subList(0, maxSize);
                fixes.add("more than " + maxSize + " values, the last ones were removed");
            }
            if (result.size() < minSize) {
                return Validated.replaced(defaultValue, "fewer than " + minSize + " values");
            }
            value = List.copyOf(result);
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
        if (type != OptionType.LIST) {
            String reason = modCheck(value);
            if (reason != null) {
                return Validated.refused(defaultValue, "'" + value + "' is refused by the mod: " + reason, reason);
            }
        }
        return fixes.isEmpty() ? Validated.ok(value) : Validated.corrected(value, String.join(", ", fixes));
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

    /** Picker styles allowed by the dev ({@code @Picker(styles)}), empty = all, see {@link Pickers}. */
    public List<fr.heetstudio.confee.api.PickerStyle> pickerStyles() {
        return pickerStyles;
    }

    /** Picker style chosen by the dev ({@code AUTO} = the default of the kind of entry), see {@link Pickers}. */
    public fr.heetstudio.confee.api.PickerStyle pickerDefault() {
        return pickerDefault;
    }

    /**
     * For an ID option: the entries it is limited to ({@code EntityFilter}, {@code ItemFilter}...; any of them),
     * empty = all. Checked by the screen (suggestions, picker, field error).
     */
    public List<Enum<?>> filters() {
        return filters;
    }

    /** For a list or a list of objects: the screen shows the ▲ ▼ move buttons ({@code @Reorderable}). False by default. */
    public boolean reorderable() {
        return reorderable;
    }

    /** For a list: whether the same value may appear twice ({@code @AllowDuplicates}). False by default. */
    public boolean allowDuplicates() {
        return allowDuplicates;
    }

    /** For a list: smallest number of values ({@code @Size(min)}), 0 by default. */
    public int minSize() {
        return minSize;
    }

    /** For a list: largest number of values ({@code @Size(max)}), {@link Integer#MAX_VALUE} by default. */
    public int maxSize() {
        return maxSize;
    }

    /** For a list: kept sorted ({@code @Sorted}); the screen hides the move arrows. */
    public boolean sorted() {
        return sorted;
    }

    /** For a list of texts: duplicates are found without case ({@code @IgnoreCase}). */
    public boolean ignoreCase() {
        return ignoreCase;
    }

    /** For a list of numbers: lower bound of each value ({@code @Range} on the list), or null. */
    @Nullable
    public Number elementMin() {
        return elementMin;
    }

    /** For a list of numbers: upper bound of each value ({@code @Range} on the list), or null. */
    @Nullable
    public Number elementMax() {
        return elementMax;
    }

    /** For a list of texts: pattern each value must match ({@code @Pattern} on the list), or null. */
    @Nullable
    public Pattern elementPattern() {
        return elementPattern;
    }

    /** For a list of texts: maximum length of each value ({@code @MaxLength} on the list), 0 when none. */
    public int elementMaxLength() {
        return elementMaxLength;
    }

    /**
     * The value as the screen saves it: for a list, duplicates removed (first occurrence kept) and sorted with
     * {@code @Sorted}. The screen lets the player type a duplicate; it disappears silently on save.
     */
    @SuppressWarnings("unchecked")
    public T normalizeList(T value) {
        if (type != OptionType.LIST || !(value instanceof List<?> list)) {
            return value;
        }
        List<?> result = list;
        if (!allowDuplicates && hasDuplicates(result)) {
            result = withoutDuplicates(result);
        }
        if (sorted) {
            result = sortedCopy(result);
        }
        return result == list ? value : (T) List.copyOf(result);
    }

    /** Key used to find duplicates: the lower-case text with {@code @IgnoreCase}, else the value itself. */
    public Object duplicateKey(Object element) {
        return ignoreCase && element instanceof String s ? s.toLowerCase(java.util.Locale.ROOT) : element;
    }

    private boolean hasDuplicates(List<?> list) {
        java.util.Set<Object> seen = new java.util.HashSet<>();
        for (Object element : list) {
            if (!seen.add(duplicateKey(element))) {
                return true;
            }
        }
        return false;
    }

    private List<?> withoutDuplicates(List<?> list) {
        java.util.Set<Object> seen = new java.util.HashSet<>();
        List<Object> out = new ArrayList<>(list.size());
        for (Object element : list) {
            if (seen.add(duplicateKey(element))) {
                out.add(element);
            }
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<?> sortedCopy(List<?> list) {
        List<Object> out = new ArrayList<>(list);
        java.util.Comparator<Object> order;
        if (elementType == OptionType.ENUM) {
            order = java.util.Comparator.comparingInt(e -> ((Enum<?>) e).ordinal());
        } else if (elementType == OptionType.STRING && ignoreCase) {
            order = (a, b) -> String.CASE_INSENSITIVE_ORDER.compare((String) a, (String) b);
        } else if (elementType != null && elementType.isNumeric()) {
            order = java.util.Comparator.comparingDouble(e -> ((Number) e).doubleValue());
        } else {
            order = (a, b) -> ((Comparable) a).compareTo(b);
        }
        out.sort(order);
        return out;
    }

    /**
     * Converts and checks one value of a list (range, pattern, max length). The screen uses it to show an error on the
     * field of that value. Never throws.
     */
    public Validated<Object> validateElement(@Nullable Object raw) {
        if (type != OptionType.LIST || elementType == null) {
            return Validated.replaced(null, "not a list");
        }
        Object element;
        try {
            element = Coercion.coerce(raw, elementType, enumClass, null);
        } catch (IllegalArgumentException e) {
            return Validated.replaced(null, e.getMessage());
        }
        return checkElement(element);
    }

    private Validated<Object> checkElement(Object element) {
        if ((elementMin != null || elementMax != null) && element instanceof Number n) {
            double v = n.doubleValue();
            if (elementMin != null && v < elementMin.doubleValue()) {
                return Validated.corrected(Coercion.toType(elementMin, elementType), n + " is below " + elementMin + ", clamped");
            }
            if (elementMax != null && v > elementMax.doubleValue()) {
                return Validated.corrected(Coercion.toType(elementMax, elementType), n + " is above " + elementMax + ", clamped");
            }
        }
        if (elementMaxLength > 0 && element instanceof String s && s.length() > elementMaxLength) {
            return Validated.replaced(null, "'" + s + "' is longer than " + elementMaxLength + " characters");
        }
        if (elementPattern != null && element instanceof String s && !elementPattern.matcher(s).matches()) {
            return Validated.replaced(null, "'" + s + "' does not match " + elementPattern.pattern());
        }
        String reason = modCheck(element);
        if (reason != null) {
            return Validated.refused(null, "'" + element + "' is refused by the mod: " + reason, reason);
        }
        return Validated.ok(element);
    }

    /** Message of the mod's own validator, or null when the value is accepted (or the validator failed). */
    @Nullable
    private String modCheck(Object value) {
        if (modValidator == null || value == null) {
            return null;
        }
        try {
            String reason = modValidator.validate(value);
            return reason == null || reason.isBlank() ? null : reason;
        } catch (RuntimeException | LinkageError e) {
            // a broken validator of a mod must not make the value unusable (spec 14)
            fr.heetstudio.confee.ConfeeLog.warn("Validator of option '{}' failed on '{}': {}", path(), value, e.toString());
            return null;
        }
    }

    /** Whether the mod gave its own check ({@code @Validator}). */
    public boolean hasModValidator() {
        return modValidator != null;
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
        private fr.heetstudio.confee.api.ValueValidator<Object> modValidator;
        private fr.heetstudio.confee.api.Preview preview;
        private String enabledIf;
        private String visibleIf;
        private String unit;
        private String registry;
        private List<ObjectField> objectFields;
        private WidgetHint widgetHint = WidgetHint.AUTO;
        private boolean allowDuplicates;
        private boolean reorderable;
        private List<Enum<?>> filters = List.of();
        private List<fr.heetstudio.confee.api.PickerStyle> pickerStyles = List.of();
        private fr.heetstudio.confee.api.PickerStyle pickerDefault = fr.heetstudio.confee.api.PickerStyle.AUTO;
        private int minSize;
        private int maxSize = Integer.MAX_VALUE;
        private boolean sorted;
        private boolean ignoreCase;
        private Number elementMin;
        private Number elementMax;
        private Pattern elementPattern;
        private int elementMaxLength;
        private String patternError;
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

        /** Text (or, on a list, each text) must match this regular expression. */
        public Builder<T> pattern(@Nullable String regex) {
            try {
                this.pattern = regex == null || regex.isEmpty() ? null : Pattern.compile(regex);
                this.patternError = null;
            } catch (java.util.regex.PatternSyntaxException e) {
                this.patternError = "'" + regex + "' is not a valid regular expression (" + e.getDescription() + ")";
            }
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

        /** The mod's own check ({@code @Validator}); on a list of simple values it checks each value. */
        @SuppressWarnings("unchecked")
        public Builder<T> modValidator(@Nullable fr.heetstudio.confee.api.ValueValidator<?> validator) {
            this.modValidator = (fr.heetstudio.confee.api.ValueValidator<Object>) validator;
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

        /** For a list: the same value may appear twice (by default duplicates are removed). */
        public Builder<T> allowDuplicates(boolean allow) {
            this.allowDuplicates = allow;
            return this;
        }

        /** For an ID option: entries it is limited to (EntityFilter, ItemFilter, EffectFilter, EnchantmentFilter, BiomeFilter). */
        public Builder<T> filters(List<? extends Enum<?>> filters) {
            this.filters = List.copyOf(filters);
            return this;
        }

        /** For a list or a list of objects: show the ▲ ▼ move buttons. */
        public Builder<T> reorderable(boolean reorderable) {
            this.reorderable = reorderable;
            return this;
        }

        /** Picker styles the player can choose (empty = all). */
        public Builder<T> pickerStyles(List<fr.heetstudio.confee.api.PickerStyle> styles) {
            this.pickerStyles = List.copyOf(styles);
            return this;
        }

        /** Picker style shown first ({@code AUTO} = default of the kind of entry). */
        public Builder<T> pickerDefault(fr.heetstudio.confee.api.PickerStyle style) {
            this.pickerDefault = Objects.requireNonNull(style);
            return this;
        }

        /** For a list: number of values allowed ({@code 0 <= min <= max}). */
        public Builder<T> size(int min, int max) {
            this.minSize = min;
            this.maxSize = max;
            return this;
        }

        /** For a list: kept sorted (natural order, enums in declaration order). */
        public Builder<T> sorted(boolean sorted) {
            this.sorted = sorted;
            return this;
        }

        /** For a list of texts: "Dirt" and "dirt" are the same value when looking for duplicates. */
        public Builder<T> ignoreCase(boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
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
            if (patternError != null) {
                problems.add("@Pattern / pattern(): " + patternError);
            }
            if (type == OptionType.LIST) {
                // on a list, @Range / @Pattern / @MaxLength apply to each value (one name to learn, not two)
                elementMin = min;
                elementMax = max;
                elementPattern = pattern;
                elementMaxLength = maxLength;
                min = null;
                max = null;
                pattern = null;
                maxLength = 0;
            }
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
            listProblems(problems);
            pickerProblems(problems);
            filterProblems(problems);
            if (widgetHint == WidgetHint.MULTILINE && type != OptionType.STRING) {
                problems.add("@Multiline / multiline() is only allowed on String options");
            }
            if (registry != null && !textual) {
                problems.add("@Registry, @Item and @Block need a ResourceLocation, String or List<String> value");
            }
            if (PseudoRegistries.isModList(String.valueOf(registry)) && type == OptionType.IDENTIFIER) {
                problems.add("@ModId / modId() needs a String or List<String> field: a mod ID such as \"sodium\" is not"
                        + " a ResourceLocation");
            }
            if (allowTags && PseudoRegistries.isModList(String.valueOf(registry))) {
                problems.add("mods have no tags. Fix: remove tags from @ModId / modId()");
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
            if (sorted && defaultValue instanceof List<?>) {
                // a sorted list: the default value is sorted for the dev
                defaultValue = option.normalizeList(defaultValue);
                option = new Option<>(this);
            }
            if (defaultValue != null) {
                Validated<T> check = option.validate(defaultValue);
                if (!check.isOk()) {
                    throw new IllegalStateException("Invalid default value for option '" + key + "': " + check.message()
                            + defaultValueFix(check.message()));
                }
            }
            return option;
        }

        /** A filter must match the registry of the option (an EntityFilter on an entity option...). */
        private void filterProblems(List<String> problems) {
            for (Enum<?> filter : filters) {
                String expected = switch (filter.getClass().getSimpleName()) {
                    case "EntityFilter" -> "minecraft:entity_type";
                    case "ItemFilter" -> "minecraft:item";
                    case "EffectFilter" -> "minecraft:mob_effect";
                    case "EnchantmentFilter" -> "minecraft:enchantment";
                    case "BiomeFilter" -> "minecraft:worldgen/biome";
                    default -> null;
                };
                boolean ok = expected != null && (expected.equals(registry)
                        || (filter instanceof fr.heetstudio.confee.api.ItemFilter && "minecraft:block".equals(registry)));
                if (!ok) {
                    problems.add(filter.getClass().getSimpleName() + "." + filter.name() + " does not fit "
                            + (registry == null ? "an option without registry" : "the registry " + registry)
                            + ". Fix: EntityFilter with @Entity, ItemFilter with @Item / @Block, EffectFilter with @Effect,"
                            + " EnchantmentFilter with @Enchantment, BiomeFilter with @Biome");
                    return;
                }
            }
        }

        /** Mistakes in the picker styles (spec 14). */
        private void pickerProblems(List<String> problems) {
            boolean customized = !pickerStyles.isEmpty() || pickerDefault != fr.heetstudio.confee.api.PickerStyle.AUTO;
            if (customized && !Pickers.hasPicker(registry)) {
                problems.add("@Picker / picker() needs an ID option that has a picker: @Item, @Block, @Entity, @Enchantment,"
                        + " @Effect, @Fluid or @ModId (or .item(), .block(), .modId()...)");
            }
            if (pickerStyles.contains(fr.heetstudio.confee.api.PickerStyle.AUTO)) {
                problems.add("AUTO is not a picker style. Fix: @Picker(styles = ...) takes GRID, LIST and/or DETAILED;"
                        + " AUTO is only for defaultStyle");
            }
            if (!pickerStyles.isEmpty() && pickerDefault != fr.heetstudio.confee.api.PickerStyle.AUTO
                    && !pickerStyles.contains(pickerDefault)) {
                problems.add("the default picker style " + pickerDefault + " is not in the allowed styles " + pickerStyles
                        + ". Fix: add it to @Picker(styles) / pickerStyles(), or change defaultStyle");
            }
        }

        /** Mistakes in the list options (spec 14): what is wrong and how to fix it. */
        private void listProblems(List<String> problems) {
            boolean list = type == OptionType.LIST;
            String where = list ? "" : " (this option is a " + type.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + ")";
            if (allowDuplicates && !list) {
                problems.add("@AllowDuplicates / allowDuplicates() is only allowed on List options" + where);
            }
            if ((minSize != 0 || maxSize != Integer.MAX_VALUE) && !list) {
                problems.add("@Size / size() is only allowed on List options" + where);
            }
            if (minSize < 0 || maxSize < minSize) {
                problems.add("invalid size: min " + minSize + ", max " + maxSize
                        + ". Fix: 0 <= min <= max, e.g. @Size(min = 1, max = 10)");
            }
            if (reorderable && !list && type != OptionType.OBJECT_LIST) {
                problems.add("@Reorderable / reorderable() is only allowed on List options and lists of objects" + where);
            }
            if (reorderable && sorted) {
                problems.add("@Reorderable and @Sorted cannot be used together: a sorted list keeps its own order."
                        + " Fix: keep one of them");
            }
            if (sorted && !list) {
                problems.add("@Sorted / sorted() is only allowed on List options" + where);
            }
            if (ignoreCase && !(list && elementType == OptionType.STRING)) {
                problems.add("@IgnoreCase / ignoreCase() needs a List<String>" + where);
            }
            if (ignoreCase && allowDuplicates) {
                problems.add("@IgnoreCase only changes how duplicates are found, it does nothing with @AllowDuplicates."
                        + " Fix: remove one of them");
            }
            if (list && (elementMin != null || elementMax != null) && (elementType == null || !elementType.isNumeric())) {
                problems.add("@Range / range() on a list bounds each value: it needs a list of numbers (List<Integer>,"
                        + " List<Double>...), not a List<" + (elementType == null ? "?" : elementType.name().toLowerCase(java.util.Locale.ROOT)) + ">."
                        + " Fix: for the number of values, use @Size / size()");
            }
            if (list && elementMin != null && elementMax != null && elementMin.doubleValue() > elementMax.doubleValue()) {
                problems.add("min (" + elementMin + ") is greater than max (" + elementMax + ")");
            }
            if (list && (elementPattern != null || elementMaxLength > 0) && elementType != OptionType.STRING) {
                problems.add("@Pattern / @MaxLength on a list check each value: they need a List<String>");
            }
            if (list && elementMaxLength < 0) {
                problems.add("@MaxLength must be positive");
            }
        }

        private static String defaultValueFix(@Nullable String message) {
            if (message == null) {
                return "";
            }
            if (message.contains("duplicate")) {
                return ". Fix: remove the duplicates from the default value, or allow them with @AllowDuplicates / allowDuplicates()";
            }
            if (message.contains(" values")) {
                return ". Fix: give the default value a size allowed by @Size / size()";
            }
            if (message.contains("clamped") || message.contains("does not match") || message.contains("longer than")) {
                return ". Fix: change the default value, or the @Range / @Pattern / @MaxLength of the list";
            }
            return "";
        }
    }
}

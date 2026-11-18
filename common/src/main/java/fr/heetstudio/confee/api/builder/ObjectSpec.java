package fr.heetstudio.confee.api.builder;

import fr.heetstudio.confee.core.ObjectField;
import fr.heetstudio.confee.core.OptionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fields of the elements of a list of objects (builder API).
 *
 * <pre>{@code
 * ObjectSpec preset = ObjectSpec.builder().string("id", "new").doubleValue("speed", 1.0).build();
 * b.objectList("presets", preset, List.of(preset.element("id", "fast", "speed", 2.5))).build();
 * }</pre>
 */
public final class ObjectSpec {

    private final List<ObjectField> fields;

    private ObjectSpec(List<ObjectField> fields) {
        this.fields = List.copyOf(fields);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Internal. */
    public List<ObjectField> fields() {
        return fields;
    }

    /**
     * An element: default field values, overridden by the given {@code key, value} pairs.
     *
     * @throws IllegalArgumentException on an odd number of arguments or an unknown field
     */
    public Map<String, Object> element(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("[Confee] element() takes key, value pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (ObjectField field : fields) {
            map.put(field.key(), field.defaultValue());
        }
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = String.valueOf(keyValues[i]);
            if (!map.containsKey(key)) {
                throw new IllegalArgumentException("[Confee] Unknown field '" + key + "'");
            }
            map.put(key, keyValues[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    public static final class Builder {
        private final List<ObjectField> fields = new ArrayList<>();

        private Builder() {
        }

        public Builder bool(String key, boolean defaultValue) {
            return add(key, OptionType.BOOLEAN, null, defaultValue);
        }

        public Builder intValue(String key, int defaultValue) {
            return add(key, OptionType.INT, null, defaultValue);
        }

        public Builder longValue(String key, long defaultValue) {
            return add(key, OptionType.LONG, null, defaultValue);
        }

        public Builder floatValue(String key, float defaultValue) {
            return add(key, OptionType.FLOAT, null, defaultValue);
        }

        public Builder doubleValue(String key, double defaultValue) {
            return add(key, OptionType.DOUBLE, null, defaultValue);
        }

        public Builder string(String key, String defaultValue) {
            return add(key, OptionType.STRING, null, defaultValue);
        }

        public <E extends Enum<E>> Builder enumValue(String key, E defaultValue) {
            return add(key, OptionType.ENUM, defaultValue.getDeclaringClass(), defaultValue);
        }

        private Builder add(String key, OptionType type, Class<? extends Enum<?>> enumClass, Object defaultValue) {
            fields.add(new ObjectField(key, type, enumClass, defaultValue, null));
            return this;
        }

        public ObjectSpec build() {
            if (fields.isEmpty()) {
                throw new IllegalStateException("[Confee] An object needs at least one field");
            }
            return new ObjectSpec(fields);
        }
    }
}

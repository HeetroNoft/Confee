package fr.heetstudio.confee.core;

import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * One field of the elements of a list of objects ({@link OptionType#OBJECT_LIST}), e.g. {@code speed} in
 * {@code presets = [{ id = "fast", speed = 2.5 }]}.
 * <p>Internal: may change without notice.</p>
 *
 * @param key          key in the file
 * @param type         a scalar type: BOOLEAN, INT, LONG, FLOAT, DOUBLE, STRING or ENUM
 * @param enumClass    for ENUM fields
 * @param defaultValue value of the field in a new element
 * @param displayName  explicit name, or null to humanize the key
 */
public record ObjectField(String key, OptionType type, @Nullable Class<? extends Enum<?>> enumClass,
                          Object defaultValue, @Nullable String displayName) {

    static final Set<OptionType> ALLOWED = Set.of(OptionType.BOOLEAN, OptionType.INT, OptionType.LONG,
            OptionType.FLOAT, OptionType.DOUBLE, OptionType.STRING, OptionType.ENUM);

    public ObjectField {
        if (key == null || key.isBlank() || key.contains(".")) {
            throw new IllegalArgumentException("Invalid field key '" + key + "'");
        }
        if (!ALLOWED.contains(type)) {
            throw new IllegalArgumentException("Field '" + key + "': type " + type + " is not allowed in a list of objects");
        }
        if (type == OptionType.ENUM && enumClass == null) {
            throw new IllegalArgumentException("Field '" + key + "': ENUM needs an enum class");
        }
    }
}

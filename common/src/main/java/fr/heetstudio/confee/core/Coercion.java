package fr.heetstudio.confee.core;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Converts raw values (from parsers, widgets or the API) to the Java type of an {@link OptionType}.
 * Throws {@link IllegalArgumentException} with a readable message when the value cannot be converted.
 * <p>Internal: may change without notice.</p>
 */
final class Coercion {

    private static final Pattern IDENTIFIER = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");

    private Coercion() {
    }

    static Object coerce(@Nullable Object raw, OptionType type,
                         @Nullable Class<? extends Enum<?>> enumClass,
                         @Nullable OptionType elementType) {
        return coerce(raw, type, enumClass, elementType, List.of());
    }

    static Object coerce(@Nullable Object raw, OptionType type,
                         @Nullable Class<? extends Enum<?>> enumClass,
                         @Nullable OptionType elementType, List<ObjectField> fields) {
        if (raw == null) {
            throw new IllegalArgumentException("missing value");
        }
        return switch (type) {
            case BOOLEAN -> toBoolean(raw);
            case INT -> toInt(raw);
            case LONG -> toLong(raw);
            case FLOAT -> (float) toDouble(raw);
            case DOUBLE -> toDouble(raw);
            case STRING -> toStringValue(raw);
            case ENUM -> toEnum(raw, enumClass);
            case LIST -> toList(raw, elementType, enumClass);
            case MAP -> toMap(raw, elementType, enumClass);
            case OBJECT_LIST -> toObjectList(raw, fields);
            case COLOR -> toColor(raw);
            case IDENTIFIER -> toIdentifier(raw);
        };
    }

    /** Converts a bound ({@code min} / {@code max}) to the Java type of a numeric option. */
    static Object toType(Number n, OptionType type) {
        return switch (type) {
            case INT -> n.intValue();
            case LONG -> n.longValue();
            case FLOAT -> n.floatValue();
            default -> n.doubleValue();
        };
    }

    private static boolean toBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (t.equals("true")) {
                return true;
            }
            if (t.equals("false")) {
                return false;
            }
        }
        throw wrongType(raw, "a boolean");
    }

    private static int toInt(Object raw) {
        long l = toLong(raw);
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(raw + " does not fit in an int");
        }
        return (int) l;
    }

    private static long toLong(Object raw) {
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short || raw instanceof Byte) {
            return ((Number) raw).longValue();
        }
        BigDecimal decimal;
        if (raw instanceof Number n) {
            decimal = new BigDecimal(n.toString());
        } else if (raw instanceof String s) {
            try {
                decimal = new BigDecimal(s.trim());
            } catch (NumberFormatException e) {
                throw wrongType(raw, "an integer");
            }
        } else {
            throw wrongType(raw, "an integer");
        }
        try {
            return decimal.longValueExact();
        } catch (ArithmeticException e) {
            throw wrongType(raw, "an integer");
        }
    }

    private static double toDouble(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw instanceof String s) {
            try {
                double d = Double.parseDouble(s.trim());
                if (!Double.isNaN(d)) {
                    return d;
                }
            } catch (NumberFormatException ignored) {
                // falls through to the error below
            }
        }
        throw wrongType(raw, "a number");
    }

    private static String toStringValue(Object raw) {
        if (raw instanceof String s) {
            return s;
        }
        if (raw instanceof Character c) {
            return c.toString();
        }
        throw wrongType(raw, "a text");
    }

    private static Object toEnum(Object raw, @Nullable Class<? extends Enum<?>> enumClass) {
        if (enumClass == null) {
            throw new IllegalArgumentException("no enum class");
        }
        if (enumClass.isInstance(raw)) {
            return raw;
        }
        if (raw instanceof String s) {
            String name = s.trim();
            for (Enum<?> constant : enumClass.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(name)) {
                    return constant;
                }
            }
        }
        throw new IllegalArgumentException("'" + raw + "' is not a value of " + enumClass.getSimpleName());
    }

    private static Map<String, Object> toMap(Object raw, @Nullable OptionType elementType,
                                             @Nullable Class<? extends Enum<?>> enumClass) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw wrongType(raw, "a map");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (key.isEmpty()) {
                throw new IllegalArgumentException("empty key");
            }
            try {
                out.put(key, coerce(entry.getValue(), elementType, enumClass, null));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("key '" + key + "': " + e.getMessage());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static List<Object> toObjectList(Object raw, List<ObjectField> fields) {
        if (!(raw instanceof List<?> list)) {
            throw wrongType(raw, "a list of objects");
        }
        List<Object> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> element)) {
                throw new IllegalArgumentException("element " + i + " is not an object");
            }
            Map<String, Object> converted = new LinkedHashMap<>();
            for (ObjectField field : fields) {
                Object value = element.get(field.key());
                try {
                    converted.put(field.key(), value == null ? field.defaultValue()
                            : coerce(value, field.type(), field.enumClass(), null));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("element " + i + ", '" + field.key() + "': " + e.getMessage());
                }
            }
            out.add(Collections.unmodifiableMap(converted));
        }
        return Collections.unmodifiableList(out);
    }

    private static List<Object> toList(Object raw, @Nullable OptionType elementType,
                                       @Nullable Class<? extends Enum<?>> enumClass) {
        if (!(raw instanceof List<?> list)) {
            throw wrongType(raw, "a list");
        }
        if (elementType == null) {
            throw new IllegalArgumentException("no element type");
        }
        List<Object> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            try {
                out.add(coerce(list.get(i), elementType, enumClass, null));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("element " + i + ": " + e.getMessage());
            }
        }
        return List.copyOf(out);
    }

    /** Accepts an int, or {@code #RRGGBB}, {@code #AARRGGBB}, {@code 0xRRGGBB}, {@code 0xAARRGGBB}. */
    private static int toColor(Object raw) {
        if (raw instanceof Integer i) {
            return i;
        }
        if (raw instanceof Long l && l >= 0 && l <= 0xFFFFFFFFL) {
            return (int) (long) l;
        }
        if (raw instanceof String s) {
            String t = s.trim();
            String hex = t.startsWith("#") ? t.substring(1)
                    : t.regionMatches(true, 0, "0x", 0, 2) ? t.substring(2) : null;
            if (hex != null && (hex.length() == 6 || hex.length() == 8)) {
                try {
                    long v = Long.parseLong(hex, 16);
                    return hex.length() == 6 ? (int) (0xFF000000L | v) : (int) v;
                } catch (NumberFormatException ignored) {
                    // falls through to the error below
                }
            }
        }
        throw wrongType(raw, "a color (#RRGGBB or #AARRGGBB)");
    }

    private static String toIdentifier(Object raw) {
        String s = toStringValue(raw).trim();
        if (!IDENTIFIER.matcher(s).matches()) {
            throw new IllegalArgumentException("'" + s + "' is not a valid identifier (namespace:path)");
        }
        return s;
    }

    private static IllegalArgumentException wrongType(Object raw, String expected) {
        return new IllegalArgumentException("'" + raw + "' is not " + expected);
    }
}

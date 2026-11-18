package fr.heetstudio.confee.format;

import java.util.regex.Pattern;

/**
 * Type inference for formats where every value is text (properties, INI, YAML plain scalars), spec 8.3.
 * Conservative on purpose: a value is only a number or a boolean when writing it back as such cannot change its
 * meaning ({@code 007} stays text, {@code yes} stays text).
 * <p>Internal: may change without notice.</p>
 */
public final class TextScalars {

    private static final Pattern INTEGER = Pattern.compile("[-+]?(0|[1-9][0-9]*)");
    private static final Pattern DECIMAL = Pattern.compile("[-+]?(0|[1-9][0-9]*)?\\.[0-9]+([eE][-+]?[0-9]+)?|[-+]?(0|[1-9][0-9]*)[eE][-+]?[0-9]+|[-+]?(0|[1-9][0-9]*)\\.[0-9]*");

    private TextScalars() {
    }

    /** How a boolean was written: {@code true}, {@code True} or {@code TRUE}. */
    public enum BoolCase { LOWER, CAPITALIZED, UPPER }

    /** Kind of a text value. */
    public static ValueKind kindOf(String text) {
        if (boolCase(text) != null) {
            return ValueKind.BOOLEAN;
        }
        if (INTEGER.matcher(text).matches()) {
            try {
                Long.parseLong(text.startsWith("+") ? text.substring(1) : text);
                return ValueKind.INTEGER;
            } catch (NumberFormatException e) {
                return ValueKind.STRING;
            }
        }
        if (DECIMAL.matcher(text).matches()) {
            return ValueKind.FLOAT;
        }
        return ValueKind.STRING;
    }

    /** Java value of a text value of the given kind. */
    public static Object valueOf(String text, ValueKind kind) {
        return switch (kind) {
            case BOOLEAN -> Boolean.parseBoolean(text);
            case INTEGER -> Long.parseLong(text.startsWith("+") ? text.substring(1) : text);
            case FLOAT -> Double.parseDouble(text);
            default -> text;
        };
    }

    public static BoolCase boolCase(String text) {
        return switch (text) {
            case "true", "false" -> BoolCase.LOWER;
            case "True", "False" -> BoolCase.CAPITALIZED;
            case "TRUE", "FALSE" -> BoolCase.UPPER;
            default -> null;
        };
    }

    public static String bool(boolean value, BoolCase style) {
        String text = value ? "true" : "false";
        return switch (style == null ? BoolCase.LOWER : style) {
            case LOWER -> text;
            case CAPITALIZED -> Character.toUpperCase(text.charAt(0)) + text.substring(1);
            case UPPER -> text.toUpperCase(java.util.Locale.ROOT);
        };
    }

    /** A float that reads back as a float (always has '.' or an exponent). */
    public static String floating(Number value) {
        double d = value.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("This format cannot store " + d);
        }
        return value instanceof Float f ? Float.toString(f) : Double.toString(d);
    }

    public static long integral(Object value, ParsedValue original) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        throw new IllegalArgumentException("'" + original.joinedPath() + "' expects an integer, got " + value);
    }

    public static Number fractional(Object value, ParsedValue original) {
        if (value instanceof Double || value instanceof Float) {
            return (Number) value;
        }
        throw new IllegalArgumentException("'" + original.joinedPath() + "' expects a decimal number, got " + value);
    }

    public static <T> T expect(Object value, Class<T> type, ParsedValue original) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("'" + original.joinedPath() + "' expects " + type.getSimpleName()
                    + ", got " + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        return type.cast(value);
    }
}

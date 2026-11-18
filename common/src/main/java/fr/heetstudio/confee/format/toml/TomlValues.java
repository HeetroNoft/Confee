package fr.heetstudio.confee.format.toml;

import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.ValueKind;
import fr.heetstudio.confee.format.ValueWriter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Writes TOML values. Used for surgical edits (keeps the original kind and style) and by {@link TomlConfigWriter}.
 * <p>Internal: may change without notice.</p>
 */
public final class TomlValues implements ValueWriter {

    public static final TomlValues INSTANCE = new TomlValues();

    private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9_-]+");

    private TomlValues() {
    }

    @Override
    public String write(ParsedValue original, Object newValue, String lineSeparator) {
        return switch (original.kind()) {
            case BOOLEAN -> bool(expect(newValue, Boolean.class, original));
            case INTEGER -> integer(integral(newValue, original),
                    original.style() instanceof TomlStyle.IntegerStyle is ? is : TomlStyle.IntegerStyle.DEFAULT);
            case FLOAT -> floating(fractional(newValue, original));
            case STRING -> string(expect(newValue, String.class, original),
                    original.style() instanceof TomlStyle.StringStyle ss ? ss : TomlStyle.StringStyle.DEFAULT,
                    lineSeparator);
            case LIST -> list(original, expect(newValue, List.class, original), lineSeparator);
            default -> throw new IllegalArgumentException("Value '" + original.joinedPath() + "' is read-only");
        };
    }

    // ------------------------------------------------------------------ scalars

    public static String bool(boolean value) {
        return value ? "true" : "false";
    }

    public static String integer(long value, TomlStyle.IntegerStyle style) {
        if (style.radix() != 10 && value >= 0) {
            String digits = Long.toString(value, style.radix());
            if (style.upperCase()) {
                digits = digits.toUpperCase(Locale.ROOT);
            }
            String prefix = switch (style.radix()) {
                case 16 -> "0x";
                case 8 -> "0o";
                default -> "0b";
            };
            return prefix + digits;
        }
        String text = Long.toString(value);
        return style.plusSign() && value >= 0 ? "+" + text : text;
    }

    /** Always written as a float (contains '.', an exponent, inf or nan), so the kind never changes. */
    public static String floating(Number value) {
        double d = value.doubleValue();
        if (Double.isNaN(d)) {
            return "nan";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "inf" : "-inf";
        }
        // Float.toString avoids printing 0.1f as 0.10000000149011612
        return value instanceof Float f ? Float.toString(f) : Double.toString(d);
    }

    public static String string(String value, TomlStyle.StringStyle style, String lineSeparator) {
        return switch (style.quote()) {
            case BASIC -> basic(value);
            case LITERAL -> canBeLiteral(value, false) ? "'" + value + "'" : basic(value);
            case ML_BASIC -> "\"\"\"" + (style.openNewline() ? lineSeparator : "") + multilineBasicBody(value) + "\"\"\"";
            case ML_LITERAL -> canBeLiteral(value, true) && !value.contains("'''")
                    ? "'''" + (style.openNewline() ? lineSeparator : "") + value + "'''"
                    : "\"\"\"" + (style.openNewline() ? lineSeparator : "") + multilineBasicBody(value) + "\"\"\"";
        };
    }

    public static String basic(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\f' -> out.append("\\f");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c < 0x20 || c == 0x7F) {
                        out.append(String.format("\\u%04X", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String multilineBasicBody(String value) {
        StringBuilder out = new StringBuilder(value.length());
        int quoteRun = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                quoteRun++;
                // never let three quotes in a row appear, and never end the body with a quote
                if (quoteRun == 3 || i == value.length() - 1) {
                    out.append("\\\"");
                    quoteRun = 0;
                } else {
                    out.append(c);
                }
                continue;
            }
            quoteRun = 0;
            if (c == '\\') {
                out.append("\\\\");
            } else if ((c < 0x20 && c != '\n' && c != '\t' && !isCrlf(value, i)) || c == 0x7F) {
                out.append(String.format("\\u%04X", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean canBeLiteral(String value, boolean multiline) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\t') {
                continue;
            }
            if (multiline && (c == '\n' || isCrlf(value, i))) {
                continue;
            }
            if (c < 0x20 || c == 0x7F || (!multiline && c == '\'')) {
                return false;
            }
        }
        return true;
    }

    /** A '\r' is only allowed raw in a multi-line string as part of "\r\n". */
    private static boolean isCrlf(String value, int i) {
        return value.charAt(i) == '\r' && i + 1 < value.length() && value.charAt(i + 1) == '\n';
    }

    // ------------------------------------------------------------------ lists

    private String list(ParsedValue original, List<?> items, String lineSeparator) {
        TomlStyle.ArrayStyle style = original.style() instanceof TomlStyle.ArrayStyle as ? as
                : new TomlStyle.ArrayStyle(false, false, false, "", "", null, null);
        ValueKind elementKind = original.elementKind() != null ? original.elementKind() : guessKind(items);
        String[] parts = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            parts[i] = element(items.get(i), elementKind, style, lineSeparator, original);
        }
        return array(parts, style, lineSeparator);
    }

    static String array(String[] parts, TomlStyle.ArrayStyle style, String lineSeparator) {
        if (parts.length == 0) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        if (style.multiline()) {
            String indent = style.elementIndent().isEmpty() ? "    " : style.elementIndent();
            for (int i = 0; i < parts.length; i++) {
                out.append(lineSeparator).append(indent).append(parts[i]);
                if (i < parts.length - 1 || style.trailingComma()) {
                    out.append(',');
                }
            }
            out.append(lineSeparator).append(style.closingIndent());
        } else {
            String pad = style.spaceInside() ? " " : "";
            out.append(pad).append(String.join(", ", parts));
            if (style.trailingComma()) {
                out.append(',');
            }
            out.append(pad);
        }
        return out.append(']').toString();
    }

    private static String element(Object item, @Nullable ValueKind kind, TomlStyle.ArrayStyle style,
                                  String lineSeparator, ParsedValue original) {
        if (kind == null) {
            throw new IllegalArgumentException("Cannot write element " + item + " of '" + original.joinedPath() + "'");
        }
        return switch (kind) {
            case BOOLEAN -> bool(expect(item, Boolean.class, original));
            case INTEGER -> integer(integral(item, original),
                    style.elementIntegerStyle() != null ? style.elementIntegerStyle() : TomlStyle.IntegerStyle.DEFAULT);
            case FLOAT -> floating(fractional(item, original));
            case STRING -> {
                TomlStyle.StringStyle ss = style.elementStringStyle() != null ? style.elementStringStyle()
                        : TomlStyle.StringStyle.DEFAULT;
                // multi-line strings inside a list are written as basic strings
                if (ss.quote() == TomlStyle.Quote.ML_BASIC || ss.quote() == TomlStyle.Quote.ML_LITERAL) {
                    ss = TomlStyle.StringStyle.DEFAULT;
                }
                yield string(expect(item, String.class, original), ss, lineSeparator);
            }
            default -> throw new IllegalArgumentException("Unsupported list element in '" + original.joinedPath() + "'");
        };
    }

    @Nullable
    private static ValueKind guessKind(List<?> items) {
        if (items.isEmpty()) {
            return null;
        }
        Object first = items.get(0);
        if (first instanceof Boolean) {
            return ValueKind.BOOLEAN;
        }
        if (first instanceof Long || first instanceof Integer) {
            return ValueKind.INTEGER;
        }
        if (first instanceof Double || first instanceof Float) {
            return ValueKind.FLOAT;
        }
        return ValueKind.STRING;
    }

    // ------------------------------------------------------------------ keys

    /** Writes a key segment, quoting it when it is not a bare key. */
    public static String key(String segment) {
        return BARE_KEY.matcher(segment).matches() ? segment : basic(segment);
    }

    public static String dottedKey(List<String> path) {
        StringBuilder out = new StringBuilder();
        for (String segment : path) {
            if (out.length() > 0) {
                out.append('.');
            }
            out.append(key(segment));
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ type checks (spec 8.3: a type never changes)

    private static <T> T expect(Object value, Class<T> type, ParsedValue original) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("'" + original.joinedPath() + "' expects " + type.getSimpleName()
                    + ", got " + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        return type.cast(value);
    }

    private static long integral(Object value, ParsedValue original) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        throw new IllegalArgumentException("'" + original.joinedPath() + "' expects an integer, got " + value);
    }

    private static Number fractional(Object value, ParsedValue original) {
        if (value instanceof Double || value instanceof Float) {
            return (Number) value;
        }
        throw new IllegalArgumentException("'" + original.joinedPath() + "' expects a decimal number, got " + value);
    }
}

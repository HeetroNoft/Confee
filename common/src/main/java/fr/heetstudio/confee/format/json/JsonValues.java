package fr.heetstudio.confee.format.json;

import fr.heetstudio.confee.format.ListStyle;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.TextScalars;
import fr.heetstudio.confee.format.ValueKind;
import fr.heetstudio.confee.format.ValueWriter;
import fr.heetstudio.confee.format.toml.TomlStyle;
import fr.heetstudio.confee.format.toml.TomlValues;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Writes JSON / JSON5 values in the style of the original ones (quotes, hexadecimal, list layout).
 * <p>Internal: may change without notice.</p>
 */
public final class JsonValues implements ValueWriter {

    private final boolean json5;

    public JsonValues(boolean json5) {
        this.json5 = json5;
    }

    @Override
    public String write(ParsedValue original, Object newValue, String lineSeparator) {
        return switch (original.kind()) {
            case BOOLEAN -> TextScalars.expect(newValue, Boolean.class, original) ? "true" : "false";
            case INTEGER -> integer(TextScalars.integral(newValue, original),
                    original.style() instanceof TomlStyle.IntegerStyle is ? is : TomlStyle.IntegerStyle.DEFAULT);
            case FLOAT -> floating(TextScalars.fractional(newValue, original));
            case STRING -> string(TextScalars.expect(newValue, String.class, original),
                    original.style() instanceof JsonParser.StringStyle ss ? ss.quote() : '"');
            case LIST -> list(original, TextScalars.expect(newValue, List.class, original), lineSeparator);
            default -> throw new IllegalArgumentException("Value '" + original.joinedPath() + "' is read-only");
        };
    }

    String integer(long value, TomlStyle.IntegerStyle style) {
        if (json5) {
            return TomlValues.integer(value, style);
        }
        return Long.toString(value);
    }

    String floating(Number value) {
        double d = value.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            if (!json5) {
                throw new IllegalArgumentException("JSON cannot store " + d);
            }
            return Double.isNaN(d) ? "NaN" : d > 0 ? "Infinity" : "-Infinity";
        }
        return TextScalars.floating(value);
    }

    /** Escaped string with the given quote character. */
    public static String string(String value, char quote) {
        StringBuilder out = new StringBuilder(value.length() + 2).append(quote);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == quote) {
                out.append('\\').append(c);
                continue;
            }
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x7F || c == ' ' || c == ' ') {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append(quote).toString();
    }

    private String list(ParsedValue original, List<?> items, String lineSeparator) {
        ListStyle style = original.style() instanceof ListStyle ls ? ls : ListStyle.INLINE;
        ValueKind kind = original.elementKind() != null ? original.elementKind() : guess(items);
        String[] parts = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            parts[i] = element(items.get(i), kind, style.elementStyle(), original);
        }
        return style.format(parts, lineSeparator);
    }

    private String element(Object item, @Nullable ValueKind kind, @Nullable Object style, ParsedValue original) {
        if (kind == null) {
            throw new IllegalArgumentException("Cannot write element " + item + " of '" + original.joinedPath() + "'");
        }
        return switch (kind) {
            case BOOLEAN -> TextScalars.expect(item, Boolean.class, original) ? "true" : "false";
            case INTEGER -> integer(TextScalars.integral(item, original),
                    style instanceof TomlStyle.IntegerStyle is ? is : TomlStyle.IntegerStyle.DEFAULT);
            case FLOAT -> floating(TextScalars.fractional(item, original));
            case STRING -> string(TextScalars.expect(item, String.class, original),
                    style instanceof JsonParser.StringStyle ss ? ss.quote() : '"');
            default -> throw new IllegalArgumentException("Unsupported list element in '" + original.joinedPath() + "'");
        };
    }

    @Nullable
    static ValueKind guess(List<?> items) {
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
}

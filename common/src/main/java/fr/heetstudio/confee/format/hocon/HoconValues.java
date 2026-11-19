package fr.heetstudio.confee.format.hocon;

import fr.heetstudio.confee.format.ListStyle;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.TextScalars;
import fr.heetstudio.confee.format.ValueKind;
import fr.heetstudio.confee.format.ValueWriter;
import fr.heetstudio.confee.format.json.JsonValues;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Writes HOCON values in the style of the original ones: quotes, {@code """}, unquoted text when the new text allows
 * it, list layout (commas or line breaks). A concatenation is written back as one string or one array.
 * <p>Internal: may change without notice.</p>
 */
public final class HoconValues implements ValueWriter {

    private static final Pattern NUMBER_LIKE = Pattern.compile("-?[0-9.].*");
    private static final String FORBIDDEN = "$\"{}[]:=,+#`^?!@*&\\";

    @Override
    public String write(ParsedValue original, Object newValue, String lineSeparator) {
        return switch (original.kind()) {
            case BOOLEAN -> TextScalars.expect(newValue, Boolean.class, original) ? "true" : "false";
            case INTEGER -> Long.toString(TextScalars.integral(newValue, original));
            case FLOAT -> floating(TextScalars.fractional(newValue, original));
            case STRING -> string(TextScalars.expect(newValue, String.class, original), original.style());
            case LIST -> list(original, TextScalars.expect(newValue, List.class, original), lineSeparator);
            default -> throw new IllegalArgumentException("Value '" + original.joinedPath() + "' is read-only");
        };
    }

    private static String floating(Number value) {
        double d = value.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("HOCON cannot store " + d);
        }
        return TextScalars.floating(value);
    }

    static String string(String value, @Nullable Object style) {
        HoconStyle.Quote quote = style instanceof HoconStyle.StringStyle ss ? ss.quote() : HoconStyle.Quote.QUOTED;
        return switch (quote) {
            case TRIPLE -> value.contains("\"\"\"") || value.endsWith("\"") ? JsonValues.string(value, '"') : "\"\"\"" + value + "\"\"\"";
            case UNQUOTED -> canStayUnquoted(value) ? value : JsonValues.string(value, '"');
            default -> JsonValues.string(value, '"');
        };
    }

    /** Text that reads back as the same string without quotes. */
    static boolean canStayUnquoted(String value) {
        if (value.isEmpty() || !value.equals(value.strip()) || value.contains("//") || value.contains("\n")
                || value.contains("\r") || value.contains("\t")) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("true") || lower.equals("false") || lower.equals("null") || NUMBER_LIKE.matcher(value).matches()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (FORBIDDEN.indexOf(value.charAt(i)) >= 0) {
                return false;
            }
        }
        // a run of spaces would read back as the same text, but keep it simple: single spaces only
        return !value.contains("  ");
    }

    private static String list(ParsedValue original, List<?> items, String lineSeparator) {
        HoconStyle.ArrayStyle style = original.style() instanceof HoconStyle.ArrayStyle as ? as
                : new HoconStyle.ArrayStyle(ListStyle.INLINE, true, false);
        ValueKind kind = original.elementKind() != null ? original.elementKind() : guess(items);
        String[] parts = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            parts[i] = element(items.get(i), kind, style.layout().elementStyle(), original);
        }
        ListStyle layout = style.layout();
        if (!style.commas() && layout.multiline() && parts.length > 0) {
            // elements separated by line breaks only
            String indent = layout.elementIndent().isEmpty() ? "  " : layout.elementIndent();
            StringBuilder out = new StringBuilder("[");
            for (String part : parts) {
                out.append(lineSeparator).append(indent).append(part);
            }
            return out.append(lineSeparator).append(layout.closingIndent()).append(']').toString();
        }
        return layout.format(parts, lineSeparator);
    }

    private static String element(Object item, @Nullable ValueKind kind, @Nullable Object style, ParsedValue original) {
        if (kind == null) {
            throw new IllegalArgumentException("Cannot write element " + item + " of '" + original.joinedPath() + "'");
        }
        return switch (kind) {
            case BOOLEAN -> TextScalars.expect(item, Boolean.class, original) ? "true" : "false";
            case INTEGER -> Long.toString(TextScalars.integral(item, original));
            case FLOAT -> floating(TextScalars.fractional(item, original));
            case STRING -> string(TextScalars.expect(item, String.class, original), style);
            default -> throw new IllegalArgumentException("Unsupported list element in '" + original.joinedPath() + "'");
        };
    }

    @Nullable
    private static ValueKind guess(List<?> items) {
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

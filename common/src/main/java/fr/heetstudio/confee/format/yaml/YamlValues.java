package fr.heetstudio.confee.format.yaml;

import fr.heetstudio.confee.format.ListStyle;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.TextScalars;
import fr.heetstudio.confee.format.ValueKind;
import fr.heetstudio.confee.format.ValueWriter;
import fr.heetstudio.confee.format.json.JsonValues;
import fr.heetstudio.confee.format.toml.TomlStyle;
import fr.heetstudio.confee.format.toml.TomlValues;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Writes YAML values in the style of the original ones. A plain string that would read back as another type
 * ({@code true}, {@code 12}, {@code null}...) or break the syntax is written in double quotes, so types never change.
 * <p>Internal: may change without notice.</p>
 */
public final class YamlValues implements ValueWriter {

    public static final YamlValues INSTANCE = new YamlValues();

    private static final Set<String> SPECIAL = Set.of("null", "~", "true", "false", "yes", "no", "on", "off", "y", "n",
            ".inf", "-.inf", "+.inf", ".nan");

    private YamlValues() {
    }

    @Override
    public String write(ParsedValue original, Object newValue, String lineSeparator) {
        YamlParser.ScalarStyle style = original.style() instanceof YamlParser.ScalarStyle ss ? ss : YamlParser.ScalarStyle.PLAIN;
        return switch (original.kind()) {
            case BOOLEAN -> TextScalars.bool(TextScalars.expect(newValue, Boolean.class, original), style.boolCase());
            case INTEGER -> TomlValues.integer(TextScalars.integral(newValue, original),
                    style.integer() != null ? style.integer() : TomlStyle.IntegerStyle.DEFAULT);
            case FLOAT -> floating(TextScalars.fractional(newValue, original));
            case STRING -> string(TextScalars.expect(newValue, String.class, original), style.quote());
            case LIST -> list(original, TextScalars.expect(newValue, List.class, original), lineSeparator);
            default -> throw new IllegalArgumentException("Value '" + original.joinedPath() + "' is read-only");
        };
    }

    static String floating(Number value) {
        double d = value.doubleValue();
        if (Double.isNaN(d)) {
            return ".nan";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? ".inf" : "-.inf";
        }
        return TextScalars.floating(value);
    }

    public static String string(String value, YamlParser.Quote quote) {
        return switch (quote) {
            case PLAIN -> safePlain(value) ? value : JsonValues.string(value, '"');
            case SINGLE -> value.indexOf('\n') < 0 && value.indexOf('\r') < 0 && noControl(value)
                    ? "'" + value.replace("'", "''") + "'" : JsonValues.string(value, '"');
            case DOUBLE -> JsonValues.string(value, '"');
        };
    }

    /** Whether the text can be written without quotes and reads back as the same string. */
    public static boolean safePlain(String value) {
        if (value.isEmpty() || !value.equals(value.strip()) || !noControl(value)) {
            return false;
        }
        if ("-?:,[]{}#&*!|>'\"%@`".indexOf(value.charAt(0)) >= 0) {
            return false;
        }
        if (value.contains(": ") || value.contains(" #") || value.endsWith(":") || value.contains("\t")) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (SPECIAL.contains(lower) || lower.startsWith("0x") || lower.startsWith("0o")) {
            return false;
        }
        return TextScalars.kindOf(value) == ValueKind.STRING && !value.matches("[-+]?[0-9][0-9_.,eE+-]*");
    }

    private static boolean noControl(String value) {
        for (int k = 0; k < value.length(); k++) {
            char c = value.charAt(k);
            if (c < 0x20 || c == 0x7F) {
                return false;
            }
        }
        return true;
    }

    private String list(ParsedValue original, List<?> items, String lineSeparator) {
        ValueKind kind = original.elementKind() != null ? original.elementKind() : guess(items);
        if (original.style() instanceof YamlParser.BlockListStyle block) {
            if (items.isEmpty()) {
                return "[]";
            }
            StringBuilder out = new StringBuilder();
            String indent = " ".repeat(block.column());
            for (int k = 0; k < items.size(); k++) {
                if (k > 0) {
                    out.append(lineSeparator).append(indent);
                }
                out.append("- ").append(element(items.get(k), kind, block.elementStyle(), original));
            }
            return out.toString();
        }
        ListStyle style = original.style() instanceof ListStyle ls ? ls : ListStyle.INLINE;
        String[] parts = new String[items.size()];
        for (int k = 0; k < items.size(); k++) {
            YamlParser.ScalarStyle es = style.elementStyle() instanceof YamlParser.ScalarStyle ss ? ss : null;
            String part = element(items.get(k), kind, es, original);
            // inside [ ], a plain string must not contain ',' or brackets
            if (kind == ValueKind.STRING && (es == null || es.quote() == YamlParser.Quote.PLAIN)
                    && (part.contains(",") || part.contains("[") || part.contains("]"))) {
                part = JsonValues.string((String) items.get(k), '"');
            }
            parts[k] = part;
        }
        return style.format(parts, lineSeparator);
    }

    private static String element(Object item, @Nullable ValueKind kind, @Nullable YamlParser.ScalarStyle style,
                                  ParsedValue original) {
        if (kind == null) {
            throw new IllegalArgumentException("Cannot write element " + item + " of '" + original.joinedPath() + "'");
        }
        YamlParser.ScalarStyle s = style == null ? YamlParser.ScalarStyle.PLAIN : style;
        return switch (kind) {
            case BOOLEAN -> TextScalars.bool(TextScalars.expect(item, Boolean.class, original), s.boolCase());
            case INTEGER -> TomlValues.integer(TextScalars.integral(item, original),
                    s.integer() != null ? s.integer() : TomlStyle.IntegerStyle.DEFAULT);
            case FLOAT -> floating(TextScalars.fractional(item, original));
            case STRING -> string(TextScalars.expect(item, String.class, original), s.quote());
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

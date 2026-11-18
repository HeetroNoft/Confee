package fr.heetstudio.confee.format.properties;

import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.TextScalars;
import fr.heetstudio.confee.format.ValueWriter;
import fr.heetstudio.confee.format.toml.TomlStyle;
import fr.heetstudio.confee.format.toml.TomlValues;

/**
 * Writes {@code .properties} values: escapes as {@code Properties.store} does (non-ASCII as {@code \\uXXXX}, so the
 * file stays readable whatever its encoding).
 * <p>Internal: may change without notice.</p>
 */
public final class PropertiesValues implements ValueWriter {

    public static final PropertiesValues INSTANCE = new PropertiesValues();

    private PropertiesValues() {
    }

    @Override
    public String write(ParsedValue original, Object newValue, String lineSeparator) {
        PropertiesParser.Style style = original.style() instanceof PropertiesParser.Style ps ? ps : null;
        return switch (original.kind()) {
            case BOOLEAN -> TextScalars.bool(TextScalars.expect(newValue, Boolean.class, original),
                    style == null ? null : style.boolCase());
            case INTEGER -> TomlValues.integer(TextScalars.integral(newValue, original),
                    style != null && style.integer() != null ? style.integer() : TomlStyle.IntegerStyle.DEFAULT);
            case FLOAT -> TextScalars.floating(TextScalars.fractional(newValue, original));
            case STRING -> escape(TextScalars.expect(newValue, String.class, original));
            default -> throw new IllegalArgumentException("Value '" + original.joinedPath() + "' is read-only");
        };
    }

    /** Escapes a value: backslash, line breaks, tabs, a leading space and non-ASCII characters. */
    public static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\f' -> out.append("\\f");
                case ' ' -> out.append(i == 0 ? "\\ " : " ");
                default -> {
                    if (c < 0x20 || c > 0x7E) {
                        out.append(String.format("\\u%04X", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /** Escapes a key: as a value, plus the separators. */
    public static String escapeKey(String key) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '=' || c == ':' || c == '#' || c == '!' || c == ' ') {
                out.append('\\').append(c);
            } else {
                out.append(escape(String.valueOf(c)));
            }
        }
        return out.toString();
    }
}

package fr.heetstudio.confee.format.ini;

import fr.heetstudio.confee.format.FormatAdapter;
import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.TextScalars;
import fr.heetstudio.confee.format.ValueWriter;
import fr.heetstudio.confee.format.toml.TomlStyle;
import fr.heetstudio.confee.format.toml.TomlValues;

/**
 * INI files of other mods (Confee configs cannot use INI, spec 7.1).
 * <p>Internal: may change without notice.</p>
 */
public final class IniAdapter implements FormatAdapter {

    @Override
    public String name() {
        return "INI";
    }

    @Override
    public ParsedDocument parse(SourceText source) throws FormatException {
        return IniParser.parse(source);
    }

    @Override
    public ValueWriter values() {
        return IniAdapter::write;
    }

    static String write(ParsedValue original, Object newValue, String lineSeparator) {
        IniParser.Style style = original.style() instanceof IniParser.Style is ? is : new IniParser.Style(false, null, null);
        return switch (original.kind()) {
            case BOOLEAN -> TextScalars.bool(TextScalars.expect(newValue, Boolean.class, original), style.boolCase());
            case INTEGER -> TomlValues.integer(TextScalars.integral(newValue, original),
                    style.integer() != null ? style.integer() : TomlStyle.IntegerStyle.DEFAULT);
            case FLOAT -> TextScalars.floating(TextScalars.fractional(newValue, original));
            case STRING -> text(TextScalars.expect(newValue, String.class, original), style.quoted(), original);
            default -> throw new IllegalArgumentException("Value '" + original.joinedPath() + "' is read-only");
        };
    }

    /** INI has no escapes: line breaks are refused; spaces at the ends need quotes. */
    private static String text(String value, boolean quoted, ParsedValue original) {
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("'" + original.joinedPath() + "' cannot contain a line break");
        }
        if (quoted) {
            if (value.indexOf('"') >= 0) {
                throw new IllegalArgumentException("'" + original.joinedPath() + "' cannot contain a double quote");
            }
            return "\"" + value + "\"";
        }
        if (!value.equals(value.strip())) {
            return "\"" + value + "\"";
        }
        return value;
    }
}

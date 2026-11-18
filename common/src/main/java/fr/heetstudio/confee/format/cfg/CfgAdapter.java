package fr.heetstudio.confee.format.cfg;

import fr.heetstudio.confee.format.FormatAdapter;
import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.TextScalars;
import fr.heetstudio.confee.format.ValueKind;
import fr.heetstudio.confee.format.ValueWriter;
import fr.heetstudio.confee.format.ini.IniAdapter;
import fr.heetstudio.confee.format.ini.IniParser;

import java.util.List;

/**
 * {@code .cfg} files: Forge's legacy format when the file uses it, INI otherwise (many mods write {@code key=value}
 * in a {@code .cfg}). Confee configs cannot use it (spec 7.1).
 * <p>Internal: may change without notice.</p>
 */
public final class CfgAdapter implements FormatAdapter {

    @Override
    public String name() {
        return "CFG";
    }

    @Override
    public ParsedDocument parse(SourceText source) throws FormatException {
        try {
            return ForgeCfgParser.parse(source);
        } catch (FormatException forgeError) {
            try {
                return IniParser.parse(source);
            } catch (FormatException iniError) {
                throw forgeError;
            }
        }
    }

    @Override
    public ValueWriter values() {
        return CfgAdapter::write;
    }

    private static String write(ParsedValue original, Object newValue, String lineSeparator) {
        if (!(original.style() instanceof ForgeCfgParser.Style style)) {
            return ini.values().write(original, newValue, lineSeparator);
        }
        return switch (original.kind()) {
            case BOOLEAN -> TextScalars.expect(newValue, Boolean.class, original) ? "true" : "false";
            case INTEGER -> Long.toString(TextScalars.integral(newValue, original));
            case FLOAT -> TextScalars.floating(TextScalars.fractional(newValue, original));
            case STRING -> text(TextScalars.expect(newValue, String.class, original), original);
            case LIST -> list(TextScalars.expect(newValue, List.class, original), style, original, lineSeparator);
            default -> throw new IllegalArgumentException("Value '" + original.joinedPath() + "' is read-only");
        };
    }

    private static String text(String value, ParsedValue original) {
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("'" + original.joinedPath() + "' cannot contain a line break");
        }
        return value;
    }

    private static String list(List<?> items, ForgeCfgParser.Style style, ParsedValue original, String nl) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("'" + original.joinedPath() + "': a Forge list needs at least one element here");
        }
        ValueKind kind = ForgeCfgParser.kindOf(style.prefix());
        StringBuilder out = new StringBuilder();
        String indent = style.itemIndent();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                out.append(nl).append(indent);
            }
            Object item = items.get(i);
            out.append(switch (kind) {
                case BOOLEAN -> TextScalars.expect(item, Boolean.class, original) ? "true" : "false";
                case INTEGER -> Long.toString(TextScalars.integral(item, original));
                case FLOAT -> TextScalars.floating(TextScalars.fractional(item, original));
                default -> {
                    String text = text(TextScalars.expect(item, String.class, original), original);
                    if (text.trim().equals(">") || !text.equals(text.strip())) {
                        throw new IllegalArgumentException("'" + original.joinedPath() + "': invalid list element '" + text + "'");
                    }
                    yield text;
                }
            });
        }
        return out.toString();
    }

    private static final IniAdapter ini = new IniAdapter();
}

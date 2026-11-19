package fr.heetstudio.confee.format.hocon;

import fr.heetstudio.confee.format.FormatAdapter;
import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.ValueWriter;
import org.jetbrains.annotations.Nullable;

/**
 * HOCON ({@code .conf}, {@code .hocon}), for other mods' files only (spec 7.1, V1.1). {@link #parse} reads one text;
 * the editor reads the included files with {@link HoconParser#parse(java.nio.file.Path, SourceText,
 * HoconParser.IncludeResolver)}.
 * <p>Internal: may change without notice.</p>
 */
public final class HoconAdapter implements FormatAdapter {

    private final HoconValues values = new HoconValues();

    @Override
    public String name() {
        return "HOCON";
    }

    @Override
    public ParsedDocument parse(SourceText source) throws FormatException {
        return HoconParser.parse(source);
    }

    @Override
    public ValueWriter values() {
        return values;
    }

    @Override
    @Nullable
    public String commentPrefix() {
        return "#";
    }
}

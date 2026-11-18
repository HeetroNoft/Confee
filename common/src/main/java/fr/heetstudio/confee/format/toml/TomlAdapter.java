package fr.heetstudio.confee.format.toml;

import fr.heetstudio.confee.format.FormatAdapter;
import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.ValueWriter;

/**
 * TOML (default format).
 * <p>Internal: may change without notice.</p>
 */
public final class TomlAdapter implements FormatAdapter {

    @Override
    public String name() {
        return "TOML";
    }

    @Override
    public ParsedDocument parse(SourceText source) throws FormatException {
        return TomlParser.parse(source);
    }

    @Override
    public ValueWriter values() {
        return TomlValues.INSTANCE;
    }

    @Override
    public ConfigWriter configWriter() {
        return TomlConfigWriter::write;
    }
}

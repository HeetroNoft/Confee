package fr.heetstudio.confee.format.yaml;

import fr.heetstudio.confee.format.FormatAdapter;
import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.ValueWriter;

/**
 * YAML ({@code .yml}, {@code .yaml}).
 * <p>Internal: may change without notice.</p>
 */
public final class YamlAdapter implements FormatAdapter {

    @Override
    public String name() {
        return "YAML";
    }

    @Override
    public ParsedDocument parse(SourceText source) throws FormatException {
        return YamlParser.parse(source);
    }

    @Override
    public ValueWriter values() {
        return YamlValues.INSTANCE;
    }

    @Override
    public ConfigWriter configWriter() {
        return YamlConfigWriter::write;
    }
}

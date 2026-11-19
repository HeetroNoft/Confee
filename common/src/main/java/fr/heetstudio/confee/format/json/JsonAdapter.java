package fr.heetstudio.confee.format.json;

import fr.heetstudio.confee.format.FormatAdapter;
import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.ValueWriter;
import org.jetbrains.annotations.Nullable;

/**
 * JSON, JSONC and JSON5.
 * <p>Internal: may change without notice.</p>
 */
public final class JsonAdapter implements FormatAdapter {

    private final String name;
    private final boolean json5;
    private final boolean confeeFormat;
    private final JsonValues values;

    /**
     * @param confeeFormat whether a Confee config can be written in this dialect (JSON and JSON5, not JSONC)
     */
    public JsonAdapter(String name, boolean json5, boolean confeeFormat) {
        this.name = name;
        this.json5 = json5;
        this.confeeFormat = confeeFormat;
        this.values = new JsonValues(json5);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ParsedDocument parse(SourceText source) throws FormatException {
        return JsonParser.parse(source, json5);
    }

    @Override
    public ValueWriter values() {
        return values;
    }

    @Override
    @Nullable
    public String commentPrefix() {
        return json5 ? "//" : null;
    }

    @Override
    @Nullable
    public ConfigWriter configWriter() {
        return confeeFormat ? (schema, lineSeparator, previous) -> JsonConfigWriter.write(schema, lineSeparator, previous, json5) : null;
    }
}

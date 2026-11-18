package fr.heetstudio.confee.format;

import fr.heetstudio.confee.core.ConfigSchema;
import org.jetbrains.annotations.Nullable;

/**
 * One file format (spec 7.1): position-aware reading, style-keeping value writing (spec 7.2) and, for the formats a
 * Confee config can use, writing a whole config file (spec 7.3).
 * <p>Internal: may change without notice.</p>
 */
public interface FormatAdapter {

    /** Display name for logs and the "cannot read" screen. */
    String name();

    /**
     * Reads a file of another mod: value types are inferred from the text (spec 8.3).
     *
     * @throws FormatException when the syntax is broken
     */
    ParsedDocument parse(SourceText source) throws FormatException;

    /**
     * Reads a file owned by Confee. Text-only formats (properties) keep every value as text, so the schema decides the
     * type. Defaults to {@link #parse}.
     */
    default ParsedDocument parseForSchema(SourceText source) throws FormatException {
        return parse(source);
    }

    /** Writes new values in place of parsed ones, keeping their style. */
    ValueWriter values();

    /** Writes a whole Confee config file, or null when a Confee config cannot use this format. */
    @Nullable
    default ConfigWriter configWriter() {
        return null;
    }

    /** Writes a whole Confee config file (spec 7.3). */
    @FunctionalInterface
    interface ConfigWriter {
        /**
         * @param previous the current file when it exists and could be read: its unknown keys are kept (spec 5.3)
         */
        String write(ConfigSchema schema, String lineSeparator, @Nullable ParsedDocument previous);
    }
}

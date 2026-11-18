package fr.heetstudio.confee.format;

/**
 * Writes a new value in place of a parsed one, keeping its kind and style (spec 8.3: a type never changes).
 * <p>Internal: may change without notice.</p>
 */
@FunctionalInterface
public interface ValueWriter {

    /**
     * @param original      the value being replaced (gives kind and style)
     * @param newValue      the new Java value
     * @param lineSeparator line separator of the document
     * @return the text to put between {@code original.start()} and {@code original.end()}
     * @throws IllegalArgumentException if {@code newValue} does not fit the original kind
     */
    String write(ParsedValue original, Object newValue, String lineSeparator);
}

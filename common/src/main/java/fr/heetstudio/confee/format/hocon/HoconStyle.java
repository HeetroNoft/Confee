package fr.heetstudio.confee.format.hocon;

import fr.heetstudio.confee.format.ListStyle;

/**
 * How a HOCON value was written, so a new value is written the same way (spec 7.2).
 * <p>Internal: may change without notice.</p>
 */
public final class HoconStyle {

    private HoconStyle() {
    }

    /** How a string was written. */
    public enum Quote {
        /** {@code "text"} */
        QUOTED,
        /** {@code """text"""} */
        TRIPLE,
        /** {@code text} without quotes (kept when the new text needs no quotes) */
        UNQUOTED,
        /** several parts concatenated ({@code foo "bar" baz}): written back as one quoted string */
        CONCATENATED
    }

    public record StringStyle(Quote quote) {
    }

    /**
     * A list of scalars.
     *
     * @param layout       bracket layout of the (first) array
     * @param commas       elements separated by commas (else by line breaks only)
     * @param concatenated several arrays concatenated ({@code [1] [2]}): written back as one array
     */
    public record ArrayStyle(ListStyle layout, boolean commas, boolean concatenated) {
    }
}

package fr.heetstudio.confee.format.toml;

import org.jetbrains.annotations.Nullable;

/**
 * How a TOML value was written, so a new value can be written the same way (spec 8.3).
 * <p>Internal: may change without notice.</p>
 */
public final class TomlStyle {

    private TomlStyle() {
    }

    public enum Quote {
        /** {@code "text"} */
        BASIC,
        /** {@code 'text'} */
        LITERAL,
        /** {@code """text"""} */
        ML_BASIC,
        /** {@code '''text'''} */
        ML_LITERAL
    }

    /**
     * @param quote        quoting style
     * @param openNewline  for multi-line strings: whether a line break follows the opening delimiter
     */
    public record StringStyle(Quote quote, boolean openNewline) {
        public static final StringStyle DEFAULT = new StringStyle(Quote.BASIC, false);
    }

    /**
     * @param radix     10, 16, 8 or 2
     * @param upperCase for hexadecimal: whether digits were upper case
     * @param plusSign  for decimal: whether an explicit '+' was written
     */
    public record IntegerStyle(int radix, boolean upperCase, boolean plusSign) {
        public static final IntegerStyle DEFAULT = new IntegerStyle(10, false, false);
    }

    /**
     * @param multiline           one element per line
     * @param spaceInside         {@code [ a, b ]} rather than {@code [a, b]} (single-line only)
     * @param trailingComma       comma after the last element
     * @param elementIndent       indentation of elements (multi-line only)
     * @param closingIndent       indentation of the closing bracket (multi-line only)
     * @param elementStringStyle  style of the first string element, if any
     * @param elementIntegerStyle style of the first integer element, if any
     */
    public record ArrayStyle(boolean multiline, boolean spaceInside, boolean trailingComma,
                             String elementIndent, String closingIndent,
                             @Nullable StringStyle elementStringStyle,
                             @Nullable IntegerStyle elementIntegerStyle) {
    }
}

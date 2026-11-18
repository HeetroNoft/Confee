package fr.heetstudio.confee.format;

import org.jetbrains.annotations.Nullable;

/**
 * Layout of a bracketed list ({@code [a, b]}) as written in a file, so a new list can be written the same way.
 * Used by the JSON and YAML flow lists.
 * <p>Internal: may change without notice.</p>
 *
 * @param multiline      one element per line
 * @param spaceInside    {@code [ a, b ]} rather than {@code [a, b]} (single line)
 * @param trailingComma  comma after the last element
 * @param elementIndent  indentation of elements (multi-line)
 * @param closingIndent  indentation of the closing bracket (multi-line)
 * @param elementStyle   style of the first element (format-specific), if any
 */
public record ListStyle(boolean multiline, boolean spaceInside, boolean trailingComma, String elementIndent,
                        String closingIndent, @Nullable Object elementStyle) {

    public static final ListStyle INLINE = new ListStyle(false, false, false, "", "", null);

    /**
     * @param text          whole document
     * @param open          offset of '['
     * @param close         offset of ']'
     * @param firstElement  offset of the first element, or -1
     * @param trailingComma whether a comma follows the last element
     */
    public static ListStyle detect(String text, int open, int close, int firstElement, boolean trailingComma,
                                   @Nullable Object elementStyle) {
        String body = text.substring(open, close + 1);
        boolean multiline = body.indexOf('\n') >= 0;
        boolean spaceInside = open + 1 < text.length() && (text.charAt(open + 1) == ' ' || text.charAt(open + 1) == '\t');
        String elementIndent = "";
        String closingIndent = "";
        if (multiline) {
            elementIndent = firstElement < 0 ? "  " : indentBefore(text, firstElement);
            closingIndent = indentBefore(text, close);
        }
        return new ListStyle(multiline, spaceInside, trailingComma, elementIndent, closingIndent, elementStyle);
    }

    /** Whitespace between the previous line break and {@code offset}, or "" if other text precedes. */
    public static String indentBefore(String text, int offset) {
        int i = offset;
        while (i > 0 && (text.charAt(i - 1) == ' ' || text.charAt(i - 1) == '\t')) {
            i--;
        }
        if (i == 0 || text.charAt(i - 1) == '\n') {
            return text.substring(i, offset);
        }
        return "";
    }

    /** Writes {@code [a, b]} with this layout. */
    public String format(String[] parts, String lineSeparator) {
        if (parts.length == 0) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        if (multiline) {
            String indent = elementIndent.isEmpty() ? "  " : elementIndent;
            for (int i = 0; i < parts.length; i++) {
                out.append(lineSeparator).append(indent).append(parts[i]);
                if (i < parts.length - 1 || trailingComma) {
                    out.append(',');
                }
            }
            out.append(lineSeparator).append(closingIndent);
        } else {
            String pad = spaceInside ? " " : "";
            out.append(pad).append(String.join(", ", parts));
            if (trailingComma) {
                out.append(',');
            }
            out.append(pad);
        }
        return out.append(']').toString();
    }
}

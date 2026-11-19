package fr.heetstudio.confee.format;

/**
 * Adds, removes and moves the elements of a {@link ParsedList} by changing only the text of the elements involved
 * (spec 7.2): everything else is copied as is.
 * <p>Internal: may change without notice.</p>
 */
public final class ListEdits {

    private ListEdits() {
    }

    /**
     * Adds a copy of the last element after it (without the comments above it), with the spacing used between the
     * existing elements.
     */
    public static String duplicateLast(String text, ParsedList list) {
        int n = list.items().size();
        if (n == 0) {
            throw new IllegalArgumentException("Empty list: no element to copy");
        }
        ParsedList.Item last = list.items().get(n - 1);
        String separator = list.separator();
        if (n >= 2) {
            String between = text.substring(list.items().get(n - 2).end(), last.start());
            if (isPlainSeparator(between)) {
                separator = between;
            }
        }
        return text.substring(0, last.end()) + separator + text.substring(last.valueStart(), last.end())
                + text.substring(last.end());
    }

    /** Removes an element and the separator that goes with it; the last element leaves {@link ParsedList#emptyText()}. */
    public static String remove(String text, ParsedList list, int index) {
        int n = list.items().size();
        check(index, n);
        if (n == 1) {
            ParsedList.Item only = list.items().get(0);
            if (list.emptyText().isEmpty()) {
                return text.substring(0, only.start()) + text.substring(only.end());
            }
            return text.substring(0, list.start()) + list.emptyText() + text.substring(list.end());
        }
        if (index < n - 1) {
            return text.substring(0, list.items().get(index).start()) + text.substring(list.items().get(index + 1).start());
        }
        return text.substring(0, list.items().get(index - 1).end()) + text.substring(list.items().get(index).end());
    }

    /** Swaps an element with the next one ({@code delta} = 1) or the previous one ({@code delta} = -1). */
    public static String move(String text, ParsedList list, int index, int delta) {
        int n = list.items().size();
        check(index, n);
        int other = index + delta;
        if (delta != 1 && delta != -1 || other < 0 || other >= n) {
            return text;
        }
        ParsedList.Item a = list.items().get(Math.min(index, other));
        ParsedList.Item b = list.items().get(Math.max(index, other));
        return text.substring(0, a.start()) + text.substring(b.start(), b.end()) + text.substring(a.end(), b.start())
                + text.substring(a.start(), a.end()) + text.substring(b.end());
    }

    /** Only commas, dashes and whitespace: no comment that a copy would duplicate. */
    private static boolean isPlainSeparator(String between) {
        for (int i = 0; i < between.length(); i++) {
            char c = between.charAt(i);
            if (!Character.isWhitespace(c) && c != ',' && c != '-') {
                return false;
            }
        }
        return true;
    }

    private static void check(int index, int size) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Element " + index + " of " + size);
        }
    }
}

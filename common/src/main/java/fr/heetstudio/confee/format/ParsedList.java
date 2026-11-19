package fr.heetstudio.confee.format;

import java.util.List;

/**
 * A list whose elements are not all simple scalars of one kind (objects, lists, mixed): its elements are edited one by
 * one (spec 8.2 bis, nested structures). Records where each element is in the text, so that elements can be added,
 * removed and moved by changing only their own text.
 * <p>Internal: may change without notice.</p>
 *
 * @param path      full path of the list
 * @param start     offset of the first character of the list text (first element for block lists)
 * @param end       offset just after the list text
 * @param items     text range of each element, in order
 * @param separator text put between two elements when the list has a single element to copy the spacing from
 * @param emptyText text that replaces the whole list when its last element is removed ({@code []}, or "" when the
 *                  list disappears, like a TOML array of tables)
 */
public record ParsedList(List<String> path, int start, int end, List<Item> items, String separator, String emptyText) {

    public ParsedList {
        path = List.copyOf(path);
        items = List.copyOf(items);
    }

    /**
     * Text range of one element.
     *
     * @param start      start of the comment lines just above the element, else of the element itself: removing or
     *                   moving the element takes its comments along
     * @param valueStart start of the element itself (what a copy of it starts with)
     * @param end        offset just after the element
     */
    public record Item(int start, int valueStart, int end) {
        public Item(int start, int end) {
            this(start, start, end);
        }
    }

    /** Path segment of the element at {@code index}: {@code [0]}, {@code [1]}... */
    public static String segment(int index) {
        return "[" + index + "]";
    }

    /** Index of an element path segment ({@code [3]} → 3), or -1. */
    public static int indexOf(String segment) {
        if (segment.length() < 3 || segment.charAt(0) != '[' || segment.charAt(segment.length() - 1) != ']') {
            return -1;
        }
        try {
            return Integer.parseInt(segment.substring(1, segment.length() - 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

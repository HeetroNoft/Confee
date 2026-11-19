package fr.heetstudio.confee.format;

import java.util.ArrayList;
import java.util.List;

/**
 * What is inside the elements of {@link ParsedList}s: values and objects whose path goes through an element
 * ({@code rules.[0].name}), plus the lists themselves. Kept apart from {@link ParsedDocument#values()} so that
 * Confee's own files keep reading a list of objects as one value; only the editor of other mods' files uses it.
 * <p>Internal: may change without notice.</p>
 */
public final class Nested {

    public static final Nested EMPTY = new Nested();

    private final List<ParsedValue> values = new ArrayList<>();
    private final List<ParsedTable> tables = new ArrayList<>();
    private final List<ParsedList> lists = new ArrayList<>();

    public List<ParsedValue> values() {
        return values;
    }

    public List<ParsedTable> tables() {
        return tables;
    }

    public List<ParsedList> lists() {
        return lists;
    }

    public boolean hasList(List<String> path) {
        for (ParsedList list : lists) {
            if (list.path().equals(path)) {
                return true;
            }
        }
        return false;
    }

    /** Current sizes, to drop what was recorded since ({@link #truncate}). */
    public int[] mark() {
        return new int[]{values.size(), tables.size(), lists.size()};
    }

    public void truncate(int[] mark) {
        values.subList(mark[0], values.size()).clear();
        tables.subList(mark[1], tables.size()).clear();
        lists.subList(mark[2], lists.size()).clear();
    }
}

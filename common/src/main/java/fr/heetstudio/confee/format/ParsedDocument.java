package fr.heetstudio.confee.format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of parsing a file: the original text plus every value with its position.
 * <p>Internal: may change without notice.</p>
 */
public final class ParsedDocument {

    private final SourceText source;
    private final List<ParsedValue> values;
    private final List<ParsedTable> tables;
    private final Map<List<String>, ParsedValue> byPath = new HashMap<>();
    private final boolean looselyTyped;
    private final List<ParsedValue> nestedValues;
    private final List<ParsedTable> nestedTables;
    private final Map<List<String>, ParsedList> lists = new HashMap<>();

    public ParsedDocument(SourceText source, List<ParsedValue> values, List<ParsedTable> tables) {
        this(source, values, tables, false);
    }

    /**
     * @param looselyTyped the format does not mark strings (YAML plain scalars, properties, INI): a value that looks like
     *                     a number may be meant as text, see {@link #rawText(ParsedValue)}
     */
    public ParsedDocument(SourceText source, List<ParsedValue> values, List<ParsedTable> tables, boolean looselyTyped) {
        this(source, values, tables, looselyTyped, Nested.EMPTY);
    }

    /**
     * @param nested what is inside the elements of lists of objects, lists of lists and mixed lists
     */
    public ParsedDocument(SourceText source, List<ParsedValue> values, List<ParsedTable> tables, boolean looselyTyped,
                          Nested nested) {
        this.nestedValues = List.copyOf(nested.values());
        this.nestedTables = List.copyOf(nested.tables());
        for (ParsedList list : nested.lists()) {
            lists.put(list.path(), list);
        }
        this.looselyTyped = looselyTyped;
        this.source = source;
        this.values = List.copyOf(values);
        this.tables = List.copyOf(tables);
        for (ParsedValue value : this.values) {
            // a key repeated in the same section (INI): the first occurrence is the editable one
            byPath.putIfAbsent(value.path(), value);
        }
        for (ParsedValue value : nestedValues) {
            byPath.putIfAbsent(value.path(), value);
        }
    }

    public SourceText source() {
        return source;
    }

    public boolean looselyTyped() {
        return looselyTyped;
    }

    /** The value as written in the file. */
    public String rawText(ParsedValue value) {
        return source.text().substring(value.start(), value.end());
    }

    /** Every leaf value (scalars and lists), in document order. */
    public List<ParsedValue> values() {
        return values;
    }

    public List<ParsedTable> tables() {
        return tables;
    }

    /** Values inside the elements of {@link #lists()} (paths with {@code [n]} segments), in document order. */
    public List<ParsedValue> nestedValues() {
        return nestedValues;
    }

    /** Objects inside the elements of {@link #lists()}. */
    public List<ParsedTable> nestedTables() {
        return nestedTables;
    }

    /** Lists edited element by element (objects, lists, mixed), at any depth. */
    public java.util.Collection<ParsedList> lists() {
        return Collections.unmodifiableCollection(lists.values());
    }

    public Optional<ParsedList> list(List<String> path) {
        return Optional.ofNullable(lists.get(path));
    }

    public Optional<ParsedValue> find(List<String> path) {
        return Optional.ofNullable(byPath.get(path));
    }

    /**
     * Surgical edit (spec 7.2): returns the source text where only the text of the changed values is replaced,
     * every other character is copied as is. Values equal to what is already in the file are left untouched.
     *
     * @throws IllegalArgumentException if a value is read-only or the new value does not fit its kind
     */
    public String edit(Map<ParsedValue, Object> changes, ValueWriter writer) {
        List<Map.Entry<ParsedValue, Object>> sorted = new ArrayList<>(changes.entrySet());
        sorted.sort(Comparator.comparingInt(e -> e.getKey().start()));
        String text = source.text();
        StringBuilder out = new StringBuilder(text.length() + 16);
        int cursor = 0;
        for (Map.Entry<ParsedValue, Object> change : sorted) {
            ParsedValue target = change.getKey();
            if (!target.editable()) {
                throw new IllegalArgumentException("Value '" + target.joinedPath() + "' is read-only");
            }
            if (sameValue(target.value(), change.getValue())) {
                continue;
            }
            if (target.start() < cursor) {
                throw new IllegalArgumentException("Overlapping edits at '" + target.joinedPath() + "'");
            }
            out.append(text, cursor, target.start());
            out.append(writer.write(target, change.getValue(), source.lineSeparator()));
            cursor = target.end();
        }
        out.append(text, cursor, text.length());
        return out.toString();
    }

    /** Compares a parsed value with a new one, treating numbers by value (5L equals 5, 5.0 equals 5.0f). */
    static boolean sameValue(Object parsed, Object updated) {
        if (parsed instanceof Number a && updated instanceof Number b) {
            if (isIntegral(a) && isIntegral(b)) {
                return a.longValue() == b.longValue();
            }
            if (!isIntegral(a) && !isIntegral(b)) {
                return Double.compare(a.doubleValue(), b.doubleValue()) == 0
                        || (updated instanceof Float f && Float.compare(a.floatValue(), f) == 0);
            }
            return false;
        }
        if (parsed instanceof List<?> la && updated instanceof List<?> lb) {
            if (la.size() != lb.size()) {
                return false;
            }
            for (int i = 0; i < la.size(); i++) {
                if (!sameValue(la.get(i), lb.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(parsed, updated);
    }

    private static boolean isIntegral(Number n) {
        return n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte;
    }

    /** Values whose path starts with {@code prefix}, in document order. */
    public List<ParsedValue> valuesUnder(List<String> prefix) {
        List<ParsedValue> out = new ArrayList<>();
        for (ParsedValue value : values) {
            if (value.path().size() > prefix.size() && value.path().subList(0, prefix.size()).equals(prefix)) {
                out.add(value);
            }
        }
        return Collections.unmodifiableList(out);
    }
}

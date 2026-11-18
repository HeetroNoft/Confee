package fr.heetstudio.confee.format;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A value read from a file, with its exact position in the source text (spec 7.2).
 * <p>Internal: may change without notice.</p>
 *
 * @param path        full key path from the document root
 * @param kind        kind of the value as written
 * @param value       Java value, see {@link ValueKind}
 * @param start       offset of the first character of the value text
 * @param end         offset just after the last character of the value text
 * @param comment     comment lines directly above the key and/or at the end of its line, or {@code null}
 * @param editable    whether Confee may rewrite this value (false for read-only kinds and repeated tables)
 * @param style       format-specific information used to write a new value in the same style
 * @param elementKind for {@link ValueKind#LIST}: kind of the elements ({@code null} when the list is empty)
 * @param entryStart  offset of the start of the key, or -1 when the value is nested in another value
 * @param tablePath   path of the enclosing table header ({@code [a.b]}), empty for the root
 * @param min         lower bound declared in the file (Forge legacy {@code # Min:}), or null
 * @param max         upper bound declared in the file, or null
 */
public record ParsedValue(
        List<String> path,
        ValueKind kind,
        Object value,
        int start,
        int end,
        @Nullable String comment,
        boolean editable,
        @Nullable Object style,
        @Nullable ValueKind elementKind,
        int entryStart,
        List<String> tablePath,
        @Nullable Number min,
        @Nullable Number max
) {

    public ParsedValue(List<String> path, ValueKind kind, Object value, int start, int end, @Nullable String comment,
                       boolean editable, @Nullable Object style, @Nullable ValueKind elementKind, int entryStart,
                       List<String> tablePath) {
        this(path, kind, value, start, end, comment, editable, style, elementKind, entryStart, tablePath, null, null);
    }

    public String key() {
        return path.get(path.size() - 1);
    }

    public String joinedPath() {
        return String.join(".", path);
    }
}

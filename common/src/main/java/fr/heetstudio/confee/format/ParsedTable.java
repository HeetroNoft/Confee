package fr.heetstudio.confee.format;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A table / object / section found in a file.
 * <p>Internal: may change without notice.</p>
 *
 * @param path      full path from the document root
 * @param comment   comment directly above the table header, or {@code null}
 * @param repeated  {@code true} for an element of an array of tables ({@code [[x]]}): read-only
 * @param inline    {@code true} for an inline table ({@code key = { ... }})
 */
public record ParsedTable(List<String> path, @Nullable String comment, boolean repeated, boolean inline) {
}

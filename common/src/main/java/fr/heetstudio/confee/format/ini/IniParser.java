package fr.heetstudio.confee.format.ini;

import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedTable;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.TextScalars;
import fr.heetstudio.confee.format.ValueKind;
import fr.heetstudio.confee.format.toml.TomlStyle;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * INI parser with positions: {@code [section]}, {@code key=value} or {@code key: value}, whole-line {@code ;} or
 * {@code #} comments. Values are text (types inferred, spec 8.3); a value fully in double quotes keeps its quotes.
 * A key repeated in the same section is read-only after its first occurrence.
 * <p>Internal: may change without notice.</p>
 */
public final class IniParser {

    /** How a value was written. */
    public record Style(boolean quoted, @Nullable TextScalars.BoolCase boolCase, @Nullable TomlStyle.IntegerStyle integer) {
    }

    private IniParser() {
    }

    public static ParsedDocument parse(SourceText source) throws FormatException {
        String s = source.text();
        List<ParsedValue> values = new ArrayList<>();
        List<ParsedTable> tables = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        List<String> comment = new ArrayList<>();
        List<String> section = List.of();
        int pos = 0;
        int lineNumber = 0;
        while (pos < s.length()) {
            lineNumber++;
            int lineEnd = pos;
            while (lineEnd < s.length() && s.charAt(lineEnd) != '\n' && s.charAt(lineEnd) != '\r') {
                lineEnd++;
            }
            int p = pos;
            while (p < lineEnd && (s.charAt(p) == ' ' || s.charAt(p) == '\t' || s.charAt(p) == '﻿')) {
                p++;
            }
            int contentEnd = lineEnd;
            while (contentEnd > p && (s.charAt(contentEnd - 1) == ' ' || s.charAt(contentEnd - 1) == '\t')) {
                contentEnd--;
            }
            if (p == contentEnd) {
                comment.clear();
            } else if (s.charAt(p) == ';' || s.charAt(p) == '#') {
                String text = s.substring(p + 1, contentEnd);
                comment.add(text.startsWith(" ") ? text.substring(1) : text);
            } else if (s.charAt(p) == '[') {
                int close = s.indexOf(']', p);
                if (close < 0 || close > contentEnd) {
                    throw new FormatException("Unterminated section header", lineNumber, p - pos + 1);
                }
                section = List.of(s.substring(p + 1, close).trim());
                tables.add(new ParsedTable(section, comment.isEmpty() ? null : String.join("\n", comment), false, false));
                comment.clear();
            } else {
                int sep = -1;
                for (int k = p; k < contentEnd; k++) {
                    if (s.charAt(k) == '=' || s.charAt(k) == ':') {
                        sep = k;
                        break;
                    }
                }
                if (sep < 0) {
                    throw new FormatException("Expected 'key=value'", lineNumber, p - pos + 1);
                }
                String key = s.substring(p, sep).trim();
                int vs = sep + 1;
                while (vs < contentEnd && (s.charAt(vs) == ' ' || s.charAt(vs) == '\t')) {
                    vs++;
                }
                String raw = s.substring(vs, contentEnd);
                boolean quoted = raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")
                        && raw.indexOf('"', 1) == raw.length() - 1;
                String text = quoted ? raw.substring(1, raw.length() - 1) : raw;
                ValueKind kind = quoted ? ValueKind.STRING : TextScalars.kindOf(text);
                List<String> path = new ArrayList<>(section);
                path.add(key);
                boolean first = seen.add(path);
                values.add(new ParsedValue(List.copyOf(path), kind, TextScalars.valueOf(text, kind), vs, contentEnd,
                        comment.isEmpty() ? null : String.join("\n", comment), first,
                        new Style(quoted, TextScalars.boolCase(text),
                                kind == ValueKind.INTEGER ? new TomlStyle.IntegerStyle(10, false, text.startsWith("+")) : null),
                        null, p, section));
                comment.clear();
            }
            pos = lineEnd;
            if (pos < s.length() && s.charAt(pos) == '\r') {
                pos++;
            }
            if (pos < s.length() && s.charAt(pos) == '\n') {
                pos++;
            }
        }
        return new ParsedDocument(source, values, tables, true);
    }
}

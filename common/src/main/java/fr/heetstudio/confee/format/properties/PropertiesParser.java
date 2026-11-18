package fr.heetstudio.confee.format.properties;

import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.TextScalars;
import fr.heetstudio.confee.format.ValueKind;
import fr.heetstudio.confee.format.toml.TomlStyle;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java {@code .properties} parser with positions (spec 7.2): {@code key=value}, {@code key: value}, {@code key value},
 * {@code #} / {@code !} comments, line continuations and escapes. Never fails: a properties file has no syntax errors.
 * Keys are flat (dots are not split). When a key appears twice, Java keeps the last one: earlier ones are read-only.
 * <p>Internal: may change without notice.</p>
 */
public final class PropertiesParser {

    /** Style of a text value (to write booleans in the same case). */
    public record Style(@Nullable TextScalars.BoolCase boolCase, @Nullable TomlStyle.IntegerStyle integer) {
    }

    private PropertiesParser() {
    }

    /**
     * @param inferTypes infer booleans and numbers (other mods' files, spec 8.3); false keeps every value as text
     *                   (Confee configs: the schema decides)
     */
    public static ParsedDocument parse(SourceText source, boolean inferTypes) {
        String s = source.text();
        List<ParsedValue> values = new ArrayList<>();
        Map<String, Integer> lastIndex = new HashMap<>();
        List<String> comment = new ArrayList<>();
        int pos = 0;
        int n = s.length();
        while (pos < n) {
            // one natural line
            int lineStart = pos;
            int lineEnd = lineEnd(s, pos);
            int p = pos;
            while (p < lineEnd && isBlank(s.charAt(p))) {
                p++;
            }
            if (p == lineEnd) {
                comment.clear();
                pos = next(s, lineEnd);
                continue;
            }
            if (s.charAt(p) == '#' || s.charAt(p) == '!') {
                String text = s.substring(p + 1, lineEnd);
                comment.add((text.startsWith(" ") ? text.substring(1) : text).stripTrailing());
                pos = next(s, lineEnd);
                continue;
            }
            // logical line: may continue on the next lines
            int entryStart = p;
            StringBuilder key = new StringBuilder();
            int k = p;
            int end = lineEnd;
            while (true) {
                if (k >= end) {
                    break;
                }
                char c = s.charAt(k);
                if (c == '\\') {
                    if (k + 1 >= end) {
                        // continuation inside the key
                        k = skipContinuation(s, end);
                        end = lineEnd(s, k);
                        continue;
                    }
                    k = unescape(s, k, key);
                    continue;
                }
                if (c == '=' || c == ':' || isBlank(c)) {
                    break;
                }
                key.append(c);
                k++;
            }
            // separator
            while (k < end && isBlank(s.charAt(k))) {
                k++;
            }
            if (k < end && (s.charAt(k) == '=' || s.charAt(k) == ':')) {
                k++;
                while (k < end && isBlank(s.charAt(k))) {
                    k++;
                }
            }
            int valueStart = k;
            StringBuilder value = new StringBuilder();
            boolean continued = false;
            while (true) {
                if (k >= end) {
                    break;
                }
                char c = s.charAt(k);
                if (c == '\\') {
                    if (k + 1 >= end) {
                        if (backslashesBefore(s, k + 1, valueStart) % 2 == 1) {
                            continued = true;
                            k = skipContinuation(s, end);
                            end = lineEnd(s, k);
                            continue;
                        }
                    }
                    k = unescape(s, k, value);
                    continue;
                }
                value.append(c);
                k++;
            }
            int valueEnd = end;
            String text = value.toString();
            ValueKind kind = inferTypes && !continued ? TextScalars.kindOf(text) : ValueKind.STRING;
            Object parsed = TextScalars.valueOf(text, kind);
            Style style = new Style(TextScalars.boolCase(text),
                    kind == ValueKind.INTEGER ? new TomlStyle.IntegerStyle(10, false, text.startsWith("+")) : null);
            String name = key.toString();
            Integer previous = lastIndex.put(name, values.size());
            if (previous != null) {
                ParsedValue old = values.get(previous);
                values.set(previous, new ParsedValue(List.of(old.key() + " (" + (previous + 1) + ")"), old.kind(), old.value(),
                        old.start(), old.end(), old.comment(), false, old.style(), null, old.entryStart(), List.of()));
            }
            values.add(new ParsedValue(List.of(name), kind, parsed, valueStart, valueEnd,
                    comment.isEmpty() ? null : String.join("\n", comment), true, style, null, entryStart, List.of()));
            comment.clear();
            pos = next(s, end);
            if (pos == lineStart) {
                pos = n;
            }
        }
        return new ParsedDocument(source, values, List.of(), true);
    }

    /** Reads one escape at {@code k} (a backslash) into {@code out}; returns the offset after it. */
    private static int unescape(String s, int k, StringBuilder out) {
        char e = s.charAt(k + 1);
        switch (e) {
            case 't' -> out.append('\t');
            case 'n' -> out.append('\n');
            case 'r' -> out.append('\r');
            case 'f' -> out.append('\f');
            case 'u' -> {
                if (k + 6 <= s.length()) {
                    try {
                        out.append((char) Integer.parseInt(s.substring(k + 2, k + 6), 16));
                        return k + 6;
                    } catch (NumberFormatException ignored) {
                        // Java throws here; keep the text as is
                    }
                }
                out.append('u');
            }
            default -> out.append(e);
        }
        return k + 2;
    }

    private static int backslashesBefore(String s, int end, int from) {
        int count = 0;
        for (int k = end - 1; k >= from && s.charAt(k) == '\\'; k--) {
            count++;
        }
        return count;
    }

    /** After a trailing backslash at {@code lineEnd - 1}: start of the next line's content. */
    private static int skipContinuation(String s, int lineEnd) {
        int k = next(s, lineEnd);
        while (k < s.length() && isBlank(s.charAt(k))) {
            k++;
        }
        return k;
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t' || c == '\f';
    }

    private static int lineEnd(String s, int from) {
        int k = from;
        while (k < s.length() && s.charAt(k) != '\n' && s.charAt(k) != '\r') {
            k++;
        }
        return k;
    }

    private static int next(String s, int lineEnd) {
        if (lineEnd < s.length() && s.charAt(lineEnd) == '\r') {
            lineEnd++;
        }
        if (lineEnd < s.length() && s.charAt(lineEnd) == '\n') {
            lineEnd++;
        }
        return lineEnd;
    }
}

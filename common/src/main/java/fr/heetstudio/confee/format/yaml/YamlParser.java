package fr.heetstudio.confee.format.yaml;

import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ListStyle;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedTable;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.TextScalars;
import fr.heetstudio.confee.format.ValueKind;
import fr.heetstudio.confee.format.toml.TomlStyle;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * YAML parser limited to what config files use (spec 7.4): block mappings, scalars (plain, 'single', "double"),
 * lists of scalars (block {@code - a} or flow {@code [a, b]}), comments. Every value keeps its exact position.
 * Anything more advanced (block scalars {@code |} {@code >}, anchors, aliases, tags, flow mappings, lists of
 * mappings, multi-line scalars) is kept as written and shown read-only, rather than refusing the file.
 * <p>Internal: may change without notice.</p>
 */
public final class YamlParser {

    /** How a scalar was written. */
    public enum Quote { PLAIN, SINGLE, DOUBLE }

    /** Style of a scalar: quoting, and for booleans the case. */
    public record ScalarStyle(Quote quote, @Nullable TextScalars.BoolCase boolCase, @Nullable TomlStyle.IntegerStyle integer) {
        public static final ScalarStyle PLAIN = new ScalarStyle(Quote.PLAIN, null, null);
    }

    /** A block list ({@code - a} lines): column of the dashes and style of the first element. */
    public record BlockListStyle(int column, @Nullable ScalarStyle elementStyle) {
    }

    private record Line(int start, int end, int indent, String text) {
        boolean blank() {
            return text.isEmpty();
        }

        boolean comment() {
            return text.startsWith("#");
        }
    }

    private record Scalar(ValueKind kind, Object value, int start, int end, @Nullable Object style,
                          @Nullable ValueKind elementKind, boolean editable) {
    }

    private final String s;
    private final List<Line> lines = new ArrayList<>();
    private int i;
    private final List<ParsedValue> values = new ArrayList<>();
    private final List<ParsedTable> tables = new ArrayList<>();
    private final Set<List<String>> seen = new HashSet<>();
    private final List<String> pendingComment = new ArrayList<>();

    private YamlParser(String text) {
        this.s = text;
        int start = 0;
        while (start <= text.length()) {
            int lf = text.indexOf('\n', start);
            int end = lf < 0 ? text.length() : lf;
            int contentEnd = end > start && text.charAt(end - 1) == '\r' ? end - 1 : end;
            int indent = 0;
            while (start + indent < contentEnd && text.charAt(start + indent) == ' ') {
                indent++;
            }
            lines.add(new Line(start, contentEnd, indent, text.substring(start + indent, contentEnd).stripTrailing()));
            if (lf < 0) {
                break;
            }
            start = lf + 1;
        }
    }

    public static ParsedDocument parse(SourceText source) throws FormatException {
        YamlParser parser = new YamlParser(source.text());
        parser.parseDocument();
        return new ParsedDocument(source, parser.values, parser.tables, true);
    }

    // ------------------------------------------------------------------ structure

    private void parseDocument() throws FormatException {
        boolean content = false;
        for (Line line : lines) {
            if (line.indent == 0 && !line.blank() && line.text.startsWith("\t")) {
                throw errorAt("Tabs cannot indent YAML", line.start);
            }
        }
        // header: directives and the first document marker
        while (i < lines.size()) {
            Line line = lines.get(i);
            if (line.indent == 0 && (line.text.startsWith("%") || line.text.equals("---") || line.text.startsWith("--- "))) {
                if (line.text.startsWith("--- ") && !line.text.substring(4).trim().isEmpty()
                        && !line.text.substring(4).trim().startsWith("#")) {
                    throw errorAt("Content on the '---' line is not supported", line.start);
                }
                i++;
                continue;
            }
            if (line.blank() || line.comment()) {
                if (line.comment()) {
                    pendingComment.add(commentText(line.text));
                } else {
                    pendingComment.clear();
                }
                i++;
                continue;
            }
            content = true;
            break;
        }
        if (!content) {
            return;
        }
        Line first = lines.get(i);
        if (isSequenceItem(first.text)) {
            // a list at the root: shown read-only
            int start = first.start + first.indent;
            int end = consumeDeeperThan(first.indent - 1);
            values.add(new ParsedValue(List.of("value"), ValueKind.COMPLEX, s.substring(start, end).trim(), start, end,
                    takeComment(), false, null, null, -1, List.of()));
        } else {
            parseMapping(first.indent, List.of());
        }
        while (i < lines.size()) {
            Line line = lines.get(i);
            if (line.indent == 0 && (line.text.equals("---") || line.text.equals("..."))) {
                if (line.text.equals("---") && hasContentAfter(i + 1)) {
                    throw errorAt("Several YAML documents in one file are not supported", line.start);
                }
                i++;
                continue;
            }
            if (!line.blank() && !line.comment()) {
                throw errorAt("Unexpected indentation", line.start + line.indent);
            }
            i++;
        }
    }

    private boolean hasContentAfter(int from) {
        for (int k = from; k < lines.size(); k++) {
            if (!lines.get(k).blank() && !lines.get(k).comment()) {
                return true;
            }
        }
        return false;
    }

    private void parseMapping(int indent, List<String> path) throws FormatException {
        while (i < lines.size()) {
            Line line = lines.get(i);
            if (line.blank()) {
                pendingComment.clear();
                i++;
                continue;
            }
            if (line.comment()) {
                if (line.indent < indent && !pendingComment.isEmpty()) {
                    // a comment back at the parent level belongs to what follows
                }
                pendingComment.add(commentText(line.text));
                i++;
                continue;
            }
            if (line.indent < indent) {
                return;
            }
            if (line.indent > indent) {
                throw errorAt("Unexpected indentation", line.start + line.indent);
            }
            if (line.indent == 0 && (line.text.equals("---") || line.text.equals("..."))) {
                return;
            }
            parseEntry(line, indent, path);
        }
    }

    private void parseEntry(Line line, int indent, List<String> path) throws FormatException {
        int keyStart = line.start + indent;
        int p = keyStart;
        String key;
        char c = s.charAt(p);
        if (c == '"' || c == '\'') {
            int[] end = new int[1];
            key = quoted(p, line.end, end);
            if (key == null) {
                throw errorAt("Unterminated quoted key", p);
            }
            p = end[0];
            if (p >= line.end || s.charAt(p) != ':') {
                throw errorAt("Expected ':' after the key", p);
            }
        } else if (c == '?' || c == '-' && isSequenceItem(line.text) || c == '[' || c == '{') {
            throw errorAt("Unsupported YAML construct", p);
        } else {
            int colon = keyColon(p, line.end);
            if (colon < 0) {
                throw errorAt("Expected 'key: value'", p);
            }
            key = s.substring(p, colon).stripTrailing();
            p = colon;
        }
        p++; // ':'
        List<String> childPath = concat(path, key);
        if (!seen.add(childPath)) {
            throw errorAt("Duplicate key '" + key + "'", keyStart);
        }
        String comment = takeComment();
        while (p < line.end && s.charAt(p) == ' ') {
            p++;
        }
        i++;
        if (p >= line.end || s.charAt(p) == '#') {
            String inline = p < line.end ? commentText(s.substring(p, line.end)) : null;
            comment = join(comment, inline);
            int next = nextContent(i);
            if (next >= 0 && isSequenceItem(lines.get(next).text)
                    && (lines.get(next).indent > indent || lines.get(next).indent == indent)) {
                i = next;
                parseBlockList(lines.get(next).indent, childPath, path, keyStart, comment);
                return;
            }
            if (next >= 0 && lines.get(next).indent > indent && lines.get(next).text.startsWith("[")) {
                // value on the next line, e.g. an emptied list written as "[]"
                Line valueLine = lines.get(next);
                i = next + 1;
                Scalar scalar = inlineValue(valueLine.start + valueLine.indent, valueLine.end);
                String inlineNext = trailingComment(scalar.end, valueLine.end);
                values.add(new ParsedValue(childPath, scalar.kind, scalar.value, scalar.start, scalar.end,
                        join(comment, inlineNext), scalar.editable, scalar.style, scalar.elementKind, keyStart, path));
                return;
            }
            if (next >= 0 && lines.get(next).indent > indent) {
                tables.add(new ParsedTable(childPath, comment, false, false));
                parseMapping(lines.get(next).indent, childPath);
                return;
            }
            // no value: null, read-only
            values.add(new ParsedValue(childPath, ValueKind.COMPLEX, "", p, p, comment, false, null, null, keyStart, path));
            return;
        }
        Scalar scalar = inlineValue(p, line.end);
        int end = scalar.end;
        boolean editable = scalar.editable;
        // more indented lines after an inline value: multi-line scalar or block content, read-only
        int next = nextContent(i);
        if (next >= 0 && lines.get(next).indent > indent) {
            end = consumeDeeperThan(indent);
            editable = false;
        }
        String inline = trailingComment(scalar.end, line.end);
        Object value = editable ? scalar.value : s.substring(scalar.start, end).replaceAll("\\s+", " ").trim();
        ValueKind kind = editable ? scalar.kind : ValueKind.COMPLEX;
        values.add(new ParsedValue(childPath, kind, value, scalar.start, end, join(comment, inline), editable,
                scalar.style, editable ? scalar.elementKind : null, keyStart, path));
    }

    /** Offset of the ':' ending a plain key (followed by a space or the end of line), or -1. */
    private int keyColon(int from, int lineEnd) {
        for (int k = from; k < lineEnd; k++) {
            char c = s.charAt(k);
            if (c == ':' && (k + 1 >= lineEnd || s.charAt(k + 1) == ' ')) {
                return k;
            }
            if (c == '#' && k > from && s.charAt(k - 1) == ' ') {
                return -1;
            }
        }
        return -1;
    }

    private void parseBlockList(int column, List<String> path, List<String> tablePath, int keyStart, @Nullable String comment)
            throws FormatException {
        List<Object> items = new ArrayList<>();
        List<Scalar> scalars = new ArrayList<>();
        List<Object> objects = new ArrayList<>();
        boolean objectsOk = true;
        boolean simple = true;
        int start = -1;
        int end = -1;
        while (i < lines.size()) {
            Line line = lines.get(i);
            if (line.blank() || line.comment()) {
                if (nextContent(i) >= 0 && lines.get(nextContent(i)).indent == column
                        && isSequenceItem(lines.get(nextContent(i)).text)) {
                    i++;
                    simple = simple && line.blank();
                    continue;
                }
                break;
            }
            if (line.indent != column || !isSequenceItem(line.text)) {
                if (line.indent > column) {
                    // nested content under an item
                    simple = false;
                    end = consumeDeeperThan(column);
                    continue;
                }
                break;
            }
            int dash = line.start + column;
            if (start < 0) {
                start = dash;
            }
            int p = dash + 1;
            while (p < line.end && s.charAt(p) == ' ') {
                p++;
            }
            i++;
            if (p >= line.end || s.charAt(p) == '#') {
                simple = false;
                end = line.end;
                continue;
            }
            char c = s.charAt(p);
            if (c != '"' && c != '\'' && !(c == '-' && isSequenceItem(s.substring(p, line.end)))
                    && keyColon(p, line.end) >= 0) {
                // "- key: value" then "  key: value": an object (read-only for the raw editor, loaded by Confee)
                simple = false;
                java.util.Map<String, Object> object = mappingItem(line, p);
                if (object == null) {
                    objectsOk = false;
                    end = consumeDeeperThan(column);
                } else {
                    objects.add(object);
                    end = lastEnd;
                }
                continue;
            }
            if (c == '-' && isSequenceItem(s.substring(p, line.end))) {
                simple = false;
                objectsOk = false;
                end = line.end;
                continue;
            }
            objectsOk = false;
            Scalar scalar = inlineValue(p, line.end);
            if (!scalar.editable || !scalar.kind.isScalar()) {
                simple = false;
            }
            scalars.add(scalar);
            items.add(scalar.value);
            end = scalar.end;
            if (trailingComment(scalar.end, line.end) != null) {
                simple = false;
            }
        }
        ValueKind elementKind = scalars.isEmpty() ? null : scalars.get(0).kind;
        for (Scalar scalar : scalars) {
            if (scalar.kind != elementKind) {
                simple = false;
            }
        }
        if (start < 0) {
            start = end;
        }
        if (simple) {
            ScalarStyle elementStyle = scalars.isEmpty() ? null : (ScalarStyle) scalars.get(0).style;
            values.add(new ParsedValue(path, ValueKind.LIST, Collections.unmodifiableList(items), start, end, comment, true,
                    new BlockListStyle(column, elementStyle), elementKind, keyStart, tablePath));
        } else if (objectsOk && !objects.isEmpty() && scalars.isEmpty()) {
            values.add(new ParsedValue(path, ValueKind.COMPLEX, Collections.unmodifiableList(objects), start, end, comment,
                    false, null, null, keyStart, tablePath));
        } else {
            values.add(new ParsedValue(path, ValueKind.COMPLEX, s.substring(start, end).replaceAll("\\s+", " ").trim(),
                    start, end, comment, false, null, null, keyStart, tablePath));
        }
    }

    /** End offset of the last {@link #mappingItem}. */
    private int lastEnd;

    /**
     * One "- key: value" item with its other "key: value" lines aligned under the first key. Scalars only.
     *
     * @return the object, or null when the item holds something else (nested collections...)
     */
    @Nullable
    private java.util.Map<String, Object> mappingItem(Line line, int p) throws FormatException {
        java.util.Map<String, Object> object = new java.util.LinkedHashMap<>();
        int keyColumn = p - line.start;
        if (!mappingEntry(object, p, line.end)) {
            return null;
        }
        lastEnd = line.end;
        while (i < lines.size()) {
            Line next = lines.get(i);
            if (next.blank() || next.comment()) {
                int content = nextContent(i);
                if (content < 0 || lines.get(content).indent != keyColumn) {
                    break;
                }
                i++;
                continue;
            }
            if (next.indent != keyColumn || isSequenceItem(next.text)) {
                if (next.indent > keyColumn) {
                    return null;
                }
                break;
            }
            if (!mappingEntry(object, next.start + next.indent, next.end)) {
                return null;
            }
            lastEnd = next.end;
            i++;
        }
        return object;
    }

    /** {@code key: scalar} on one line into {@code object}; false when the value is not a simple scalar. */
    private boolean mappingEntry(java.util.Map<String, Object> object, int p, int lineEnd) throws FormatException {
        String key;
        int k;
        char c = s.charAt(p);
        if (c == '"' || c == '\'') {
            int[] end = new int[1];
            key = quoted(p, lineEnd, end);
            if (key == null || end[0] >= lineEnd || s.charAt(end[0]) != ':') {
                return false;
            }
            k = end[0] + 1;
        } else {
            int colon = keyColon(p, lineEnd);
            if (colon < 0) {
                return false;
            }
            key = s.substring(p, colon).stripTrailing();
            k = colon + 1;
        }
        while (k < lineEnd && s.charAt(k) == ' ') {
            k++;
        }
        if (k >= lineEnd || s.charAt(k) == '#') {
            return false;
        }
        Scalar scalar = inlineValue(k, lineEnd);
        if (!scalar.editable || !scalar.kind.isScalar()) {
            return false;
        }
        object.put(key, scalar.value);
        return true;
    }

    // ------------------------------------------------------------------ scalars

    /** A value after {@code key: } on one line. */
    private Scalar inlineValue(int p, int lineEnd) throws FormatException {
        char c = s.charAt(p);
        if (c == '"' || c == '\'') {
            int[] end = new int[1];
            String value = quoted(p, lineEnd, end);
            if (value == null) {
                // multi-line quoted scalar: read-only
                int e = consumeQuotedLines(c);
                return new Scalar(ValueKind.COMPLEX, "", p, e, null, null, false);
            }
            return new Scalar(ValueKind.STRING, value, p, end[0],
                    new ScalarStyle(c == '"' ? Quote.DOUBLE : Quote.SINGLE, null, null), null, true);
        }
        if (c == '[') {
            return flowList(p, lineEnd);
        }
        if (c == '{' || c == '|' || c == '>' || c == '&' || c == '*' || c == '!' || c == '@' || c == '`') {
            int e = plainEnd(p, lineEnd);
            return new Scalar(ValueKind.COMPLEX, s.substring(p, e), p, e, null, null, false);
        }
        int e = plainEnd(p, lineEnd);
        String text = s.substring(p, e);
        return plain(text, p, e);
    }

    private Scalar plain(String text, int start, int end) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.equals("null") || text.equals("~")) {
            return new Scalar(ValueKind.COMPLEX, text, start, end, null, null, false);
        }
        if (lower.equals(".inf") || lower.equals("+.inf") || lower.equals("-.inf") || lower.equals(".nan")) {
            return new Scalar(ValueKind.COMPLEX, text, start, end, null, null, false);
        }
        if ((lower.startsWith("0x") || lower.startsWith("-0x")) && text.length() > 2) {
            try {
                boolean negative = text.startsWith("-");
                String hex = text.substring(negative ? 3 : 2);
                long v = Long.parseLong(hex, 16);
                return new Scalar(ValueKind.INTEGER, negative ? -v : v, start, end,
                        new ScalarStyle(Quote.PLAIN, null, new TomlStyle.IntegerStyle(16, !hex.equals(hex.toLowerCase(Locale.ROOT)), false)),
                        null, !negative);
            } catch (NumberFormatException e) {
                return new Scalar(ValueKind.STRING, text, start, end, ScalarStyle.PLAIN, null, true);
            }
        }
        ValueKind kind = TextScalars.kindOf(text);
        Object value = TextScalars.valueOf(text, kind);
        ScalarStyle style = new ScalarStyle(Quote.PLAIN, TextScalars.boolCase(text),
                kind == ValueKind.INTEGER ? new TomlStyle.IntegerStyle(10, false, text.startsWith("+")) : null);
        return new Scalar(kind, value, start, end, style, null, true);
    }

    /** End of a plain scalar: before " #" or the end of line, trailing spaces excluded. */
    private int plainEnd(int p, int lineEnd) {
        int e = p;
        while (e < lineEnd) {
            if (s.charAt(e) == '#' && e > p && (s.charAt(e - 1) == ' ' || s.charAt(e - 1) == '\t')) {
                break;
            }
            e++;
        }
        while (e > p && (s.charAt(e - 1) == ' ' || s.charAt(e - 1) == '\t')) {
            e--;
        }
        return e;
    }

    private Scalar flowList(int open, int lineEnd) throws FormatException {
        int close = -1;
        boolean inQuote = false;
        char quote = 0;
        for (int k = open + 1; k < lineEnd; k++) {
            char c = s.charAt(k);
            if (inQuote) {
                if (c == '\\' && quote == '"') {
                    k++;
                } else if (c == quote) {
                    inQuote = false;
                }
            } else if (c == '"' || c == '\'') {
                inQuote = true;
                quote = c;
            } else if (c == '[' || c == '{') {
                // nested collections: read-only
                int e = plainEnd(open, lineEnd);
                return new Scalar(ValueKind.COMPLEX, s.substring(open, e), open, e, null, null, false);
            } else if (c == ']') {
                close = k;
                break;
            }
        }
        if (close < 0) {
            int e = plainEnd(open, lineEnd);
            return new Scalar(ValueKind.COMPLEX, s.substring(open, e), open, e, null, null, false);
        }
        List<Object> items = new ArrayList<>();
        List<Scalar> scalars = new ArrayList<>();
        int k = open + 1;
        boolean trailingComma = false;
        int firstElement = -1;
        while (true) {
            while (k < close && s.charAt(k) == ' ') {
                k++;
            }
            if (k >= close) {
                break;
            }
            if (firstElement < 0) {
                firstElement = k;
            }
            int itemEnd;
            Scalar item;
            char c = s.charAt(k);
            if (c == '"' || c == '\'') {
                int[] end = new int[1];
                String value = quoted(k, close, end);
                if (value == null) {
                    throw errorAt("Unterminated string", k);
                }
                item = new Scalar(ValueKind.STRING, value, k, end[0], new ScalarStyle(c == '"' ? Quote.DOUBLE : Quote.SINGLE, null, null), null, true);
                itemEnd = end[0];
            } else {
                int comma = s.indexOf(',', k);
                itemEnd = comma < 0 || comma > close ? close : comma;
                int e = itemEnd;
                while (e > k && s.charAt(e - 1) == ' ') {
                    e--;
                }
                item = plain(s.substring(k, e), k, e);
            }
            scalars.add(item);
            items.add(item.value);
            k = itemEnd;
            while (k < close && s.charAt(k) == ' ') {
                k++;
            }
            trailingComma = false;
            if (k < close && s.charAt(k) == ',') {
                k++;
                trailingComma = true;
            }
        }
        ValueKind elementKind = scalars.isEmpty() ? null : scalars.get(0).kind;
        boolean simple = true;
        for (Scalar scalar : scalars) {
            if (!scalar.editable || !scalar.kind.isScalar() || scalar.kind != elementKind) {
                simple = false;
            }
        }
        if (!simple) {
            return new Scalar(ValueKind.COMPLEX, s.substring(open, close + 1), open, close + 1, null, null, false);
        }
        ListStyle style = ListStyle.detect(s, open, close, firstElement, trailingComma,
                scalars.isEmpty() ? null : scalars.get(0).style);
        return new Scalar(ValueKind.LIST, Collections.unmodifiableList(items), open, close + 1, style, elementKind, true);
    }

    /** Quoted scalar on one line; null when not closed on the line. {@code end[0]} = offset after the closing quote. */
    @Nullable
    private String quoted(int p, int lineEnd, int[] end) throws FormatException {
        char quote = s.charAt(p);
        StringBuilder out = new StringBuilder();
        int k = p + 1;
        while (k < lineEnd) {
            char c = s.charAt(k);
            if (quote == '\'') {
                if (c == '\'') {
                    if (k + 1 < lineEnd && s.charAt(k + 1) == '\'') {
                        out.append('\'');
                        k += 2;
                        continue;
                    }
                    end[0] = k + 1;
                    return out.toString();
                }
                out.append(c);
                k++;
                continue;
            }
            if (c == '"') {
                end[0] = k + 1;
                return out.toString();
            }
            if (c == '\\') {
                if (k + 1 >= lineEnd) {
                    return null;
                }
                char e = s.charAt(k + 1);
                k += 2;
                switch (e) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case '0' -> out.append('\0');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'e' -> out.append('\u001B');
                    case ' ' -> out.append(' ');
                    case '"', '\\', '/' -> out.append(e);
                    case 'x', 'u', 'U' -> {
                        int len = e == 'x' ? 2 : e == 'u' ? 4 : 8;
                        if (k + len > lineEnd) {
                            throw errorAt("Invalid escape", k);
                        }
                        try {
                            out.appendCodePoint(Integer.parseInt(s.substring(k, k + len), 16));
                        } catch (IllegalArgumentException ex) {
                            throw errorAt("Invalid escape", k);
                        }
                        k += len;
                    }
                    default -> throw errorAt("Invalid escape '\\" + e + "'", k - 2);
                }
                continue;
            }
            out.append(c);
            k++;
        }
        return null;
    }

    @Nullable
    private String trailingComment(int from, int lineEnd) {
        int k = from;
        while (k < lineEnd && (s.charAt(k) == ' ' || s.charAt(k) == '\t')) {
            k++;
        }
        if (k < lineEnd && s.charAt(k) == '#') {
            return commentText(s.substring(k, lineEnd));
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isSequenceItem(String text) {
        return text.equals("-") || text.startsWith("- ");
    }

    /** Index of the next non-blank, non-comment line from {@code from}, or -1. */
    private int nextContent(int from) {
        for (int k = from; k < lines.size(); k++) {
            if (!lines.get(k).blank() && !lines.get(k).comment()) {
                return k;
            }
        }
        return -1;
    }

    /** Consumes the lines indented deeper than {@code indent} (and blank lines between them); returns the end offset. */
    private int consumeDeeperThan(int indent) {
        int end = i > 0 ? lines.get(i - 1).end : 0;
        while (i < lines.size()) {
            Line line = lines.get(i);
            if (line.blank()) {
                int next = nextContent(i);
                if (next < 0 || lines.get(next).indent <= indent) {
                    break;
                }
                i++;
                continue;
            }
            if (line.indent <= indent) {
                break;
            }
            end = line.start + line.indent + line.text.length();
            i++;
        }
        return end;
    }

    private int consumeQuotedLines(char quote) {
        while (i < lines.size()) {
            Line line = lines.get(i++);
            if (line.text.indexOf(quote) >= 0) {
                return line.start + line.indent + line.text.length();
            }
        }
        return s.length();
    }

    @Nullable
    private String takeComment() {
        if (pendingComment.isEmpty()) {
            return null;
        }
        String text = String.join("\n", pendingComment);
        pendingComment.clear();
        return text;
    }

    private static String commentText(String text) {
        String t = text.startsWith("#") ? text.substring(1) : text;
        if (t.startsWith(" ")) {
            t = t.substring(1);
        }
        return t.stripTrailing();
    }

    private FormatException errorAt(String message, int offset) {
        int line = 1;
        int col = 1;
        for (int k = 0; k < Math.min(offset, s.length()); k++) {
            if (s.charAt(k) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new FormatException(message, line, col);
    }

    private static List<String> concat(List<String> path, String key) {
        List<String> out = new ArrayList<>(path);
        out.add(key);
        return List.copyOf(out);
    }

    @Nullable
    private static String join(@Nullable String above, @Nullable String inline) {
        if (above == null) {
            return inline;
        }
        return inline == null ? above : above + "\n" + inline;
    }
}

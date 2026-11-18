package fr.heetstudio.confee.format.json;

import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ListStyle;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedTable;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.ValueKind;
import fr.heetstudio.confee.format.toml.TomlStyle;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON / JSONC / JSON5 parser that records the exact position of every value (spec 7.2).
 * Comments ({@code //}, {@code /* *&#47;}) are accepted in every dialect, as many mods write them in {@code .json}
 * files; they become the description of the next key (or of the key on the same line).
 * <p>Internal: may change without notice.</p>
 */
public final class JsonParser {

    /** How a JSON string was quoted. */
    public record StringStyle(char quote) {
        public static final StringStyle DOUBLE = new StringStyle('"');
    }

    private final String s;
    private final boolean json5;
    private int pos;
    private final List<ParsedValue> values = new ArrayList<>();
    private final List<ParsedTable> tables = new ArrayList<>();
    private final Set<List<String>> seen = new HashSet<>();
    /** Offset of a same-line comment already attached to the previous value. */
    private int consumedComment = -1;

    private JsonParser(String text, boolean json5) {
        this.s = text;
        this.json5 = json5;
    }

    /**
     * @param json5 accept JSON5 syntax (single quotes, unquoted keys, hexadecimal, Infinity/NaN, '+' signs, leading or
     *              trailing decimal point)
     */
    public static ParsedDocument parse(SourceText source, boolean json5) throws FormatException {
        JsonParser parser = new JsonParser(source.text(), json5);
        parser.parseDocument();
        return new ParsedDocument(source, parser.values, parser.tables);
    }

    private record Raw(ValueKind kind, Object value, int start, int end, @Nullable Object style,
                       @Nullable ValueKind elementKind) {
    }

    private void parseDocument() throws FormatException {
        String comment = skipIgnored();
        if (eof()) {
            return;
        }
        if (peek() == '{') {
            parseObject(List.of(), true);
        } else {
            // a root array or scalar: shown read-only
            int start = pos;
            Raw raw = parseValue(List.of(), false);
            values.add(new ParsedValue(List.of("value"), raw.kind == ValueKind.LIST ? ValueKind.COMPLEX : raw.kind,
                    raw.value, start, raw.end, comment, false, raw.style, null, -1, List.of()));
        }
        skipIgnored();
        if (!eof()) {
            throw error("Unexpected text after the end of the document");
        }
    }

    /** @param record whether leaves are recorded as values (false inside arrays) */
    private Map<String, Object> parseObject(List<String> path, boolean record) throws FormatException {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            String comment = skipIgnored();
            if (eof()) {
                throw error("Unterminated object");
            }
            if (peek() == '}') {
                pos++;
                return Collections.unmodifiableMap(map);
            }
            int keyStart = pos;
            String key = parseKey();
            skipIgnored();
            expect(':');
            skipIgnored();
            List<String> childPath = concat(path, key);
            if (record && !seen.add(childPath)) {
                throw errorAt("Duplicate key '" + key + "'", keyStart);
            }
            if (!eof() && peek() == '{') {
                int tableIndex = tables.size();
                if (record) {
                    tables.add(new ParsedTable(childPath, comment, false, false));
                }
                map.put(key, parseObject(childPath, record));
                if (record) {
                    String inline = sameLineComment();
                    if (inline != null) {
                        tables.set(tableIndex, new ParsedTable(childPath, join(comment, inline), false, false));
                    }
                }
            } else {
                Raw raw = parseValue(childPath, record);
                map.put(key, raw.value);
                if (record) {
                    String inline = sameLineComment();
                    boolean editable = raw.kind.isScalar() || raw.kind == ValueKind.LIST;
                    values.add(new ParsedValue(childPath, raw.kind, raw.value, raw.start, raw.end, join(comment, inline),
                            editable, raw.style, raw.elementKind, keyStart, path));
                }
            }
            skipIgnored();
            if (eof()) {
                throw error("Unterminated object");
            }
            if (peek() == ',') {
                pos++;
            } else if (peek() != '}') {
                throw error("Expected ',' or '}'");
            }
        }
    }

    private String parseKey() throws FormatException {
        char c = peek();
        if (c == '"' || (c == '\'' && json5)) {
            return parseString();
        }
        int start = pos;
        while (!eof() && (Character.isLetterOrDigit(peek()) || peek() == '_' || peek() == '$' || peek() == '-')) {
            pos++;
        }
        if (start == pos) {
            throw error("Expected a key");
        }
        if (!json5) {
            // lenient: unquoted keys appear in hand-written .json files read by lenient Gson
        }
        return s.substring(start, pos);
    }

    private Raw parseValue(List<String> path, boolean record) throws FormatException {
        if (eof()) {
            throw error("Expected a value");
        }
        int start = pos;
        char c = peek();
        if (c == '"' || (c == '\'' && json5)) {
            String value = parseString();
            return new Raw(ValueKind.STRING, value, start, pos, new StringStyle(c), null);
        }
        if (c == '{') {
            Map<String, Object> map = parseObject(path, false);
            return new Raw(ValueKind.COMPLEX, map, start, pos, null, null);
        }
        if (c == '[') {
            return parseArray();
        }
        if (s.startsWith("true", pos) && boundary(pos + 4)) {
            pos += 4;
            return new Raw(ValueKind.BOOLEAN, Boolean.TRUE, start, pos, null, null);
        }
        if (s.startsWith("false", pos) && boundary(pos + 5)) {
            pos += 5;
            return new Raw(ValueKind.BOOLEAN, Boolean.FALSE, start, pos, null, null);
        }
        if (s.startsWith("null", pos) && boundary(pos + 4)) {
            pos += 4;
            // unknown type: read-only
            return new Raw(ValueKind.COMPLEX, "null", start, pos, null, null);
        }
        return parseNumber();
    }

    private boolean boundary(int i) {
        return i >= s.length() || !(Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_');
    }

    private Raw parseArray() throws FormatException {
        int open = pos;
        pos++;
        List<Object> items = new ArrayList<>();
        List<Raw> raws = new ArrayList<>();
        boolean trailingComma = false;
        int firstElement = -1;
        while (true) {
            skipIgnored();
            if (eof()) {
                throw error("Unterminated array");
            }
            if (peek() == ']') {
                break;
            }
            if (firstElement < 0) {
                firstElement = pos;
            }
            Raw item = parseValue(List.of(), false);
            raws.add(item);
            items.add(item.value);
            trailingComma = false;
            skipIgnored();
            if (eof()) {
                throw error("Unterminated array");
            }
            if (peek() == ',') {
                pos++;
                trailingComma = true;
            } else if (peek() != ']') {
                throw error("Expected ',' or ']'");
            }
        }
        int close = pos;
        pos++;
        ValueKind elementKind = raws.isEmpty() ? null : raws.get(0).kind;
        boolean simple = true;
        for (Raw raw : raws) {
            if (!raw.kind.isScalar() || raw.kind != elementKind) {
                simple = false;
                break;
            }
        }
        Object elementStyle = simple && !raws.isEmpty() ? raws.get(0).style : null;
        ListStyle style = ListStyle.detect(s, open, close, firstElement, trailingComma, elementStyle);
        return new Raw(simple ? ValueKind.LIST : ValueKind.COMPLEX, Collections.unmodifiableList(items), open, pos, style,
                simple ? elementKind : null);
    }

    private Raw parseNumber() throws FormatException {
        int start = pos;
        while (!eof() && "+-0123456789.eExXabcdefABCDEFInfinityNaN".indexOf(peek()) >= 0) {
            pos++;
        }
        String token = s.substring(start, pos);
        if (token.isEmpty()) {
            throw error("Unexpected character '" + peek() + "'");
        }
        String unsigned = token.startsWith("-") || token.startsWith("+") ? token.substring(1) : token;
        boolean negative = token.startsWith("-");
        if (token.startsWith("+") && !json5) {
            throw errorAt("Invalid number '" + token + "'", start);
        }
        if (json5 && unsigned.equals("Infinity")) {
            return new Raw(ValueKind.FLOAT, negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY, start, pos, null, null);
        }
        if (json5 && unsigned.equals("NaN")) {
            return new Raw(ValueKind.FLOAT, Double.NaN, start, pos, null, null);
        }
        if (json5 && (unsigned.startsWith("0x") || unsigned.startsWith("0X")) && unsigned.length() > 2) {
            String hex = unsigned.substring(2);
            try {
                long v = Long.parseLong(hex, 16);
                return new Raw(ValueKind.INTEGER, negative ? -v : v, start, pos,
                        new TomlStyle.IntegerStyle(16, !hex.equals(hex.toLowerCase(java.util.Locale.ROOT)), false), null);
            } catch (NumberFormatException e) {
                throw errorAt("Invalid number '" + token + "'", start);
            }
        }
        String pattern = json5 ? "(0|[1-9][0-9]*)?(\\.[0-9]*)?([eE][+-]?[0-9]+)?" : "(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?";
        if (!unsigned.matches(pattern) || unsigned.equals(".") || unsigned.isEmpty()
                || (json5 && !unsigned.matches(".*[0-9].*"))) {
            throw errorAt("Invalid value '" + token + "'", start);
        }
        boolean isFloat = unsigned.contains(".") || unsigned.contains("e") || unsigned.contains("E");
        if (!isFloat) {
            try {
                long v = Long.parseLong(token.startsWith("+") ? token.substring(1) : token);
                return new Raw(ValueKind.INTEGER, v, start, pos,
                        new TomlStyle.IntegerStyle(10, false, token.startsWith("+")), null);
            } catch (NumberFormatException e) {
                // too large for a long: read-only, so its type never changes
                return new Raw(ValueKind.COMPLEX, token, start, pos, null, null);
            }
        }
        return new Raw(ValueKind.FLOAT, Double.parseDouble(token), start, pos, null, null);
    }

    private String parseString() throws FormatException {
        char quote = peek();
        pos++;
        StringBuilder out = new StringBuilder();
        while (true) {
            if (eof()) {
                throw error("Unterminated string");
            }
            char c = s.charAt(pos++);
            if (c == quote) {
                return out.toString();
            }
            if (c == '\n' || c == '\r') {
                throw errorAt("Unterminated string", pos - 1);
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (eof()) {
                throw error("Unterminated escape");
            }
            char e = s.charAt(pos++);
            switch (e) {
                case '"', '\\', '/', '\'' -> out.append(e);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'v' -> out.append('\u000B');
                case '0' -> out.append('\0');
                case 'u' -> {
                    if (pos + 4 > s.length()) {
                        throw error("Invalid unicode escape");
                    }
                    try {
                        out.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("Invalid unicode escape");
                    }
                    pos += 4;
                }
                case 'x' -> {
                    if (!json5 || pos + 2 > s.length()) {
                        throw error("Invalid escape");
                    }
                    out.append((char) Integer.parseInt(s.substring(pos, pos + 2), 16));
                    pos += 2;
                }
                case '\n' -> {
                    // JSON5 line continuation
                }
                case '\r' -> {
                    if (!eof() && peek() == '\n') {
                        pos++;
                    }
                }
                default -> {
                    if (!json5) {
                        throw errorAt("Invalid escape '\\" + e + "'", pos - 2);
                    }
                    out.append(e);
                }
            }
        }
    }

    // ------------------------------------------------------------------ whitespace and comments

    /** Skips whitespace and comments; returns the comments just before the next token (blank line resets). */
    @Nullable
    private String skipIgnored() throws FormatException {
        List<String> comments = new ArrayList<>();
        int newlines = 0;
        while (!eof()) {
            char c = peek();
            if (c == '\n') {
                newlines++;
                if (newlines >= 2) {
                    comments.clear();
                }
                pos++;
            } else if (Character.isWhitespace(c) || c == '﻿') {
                pos++;
            } else if (s.startsWith("//", pos)) {
                int end = lineEnd(pos);
                if (pos != consumedComment) {
                    comments.add(clean(s.substring(pos + 2, end)));
                    newlines = 0;
                }
                pos = end;
            } else if (s.startsWith("/*", pos)) {
                int end = s.indexOf("*/", pos + 2);
                if (end < 0) {
                    throw error("Unterminated comment");
                }
                for (String line : s.substring(pos + 2, end).split("\r?\n")) {
                    String t = clean(line.trim().startsWith("*") ? line.trim().substring(1) : line);
                    if (!t.isEmpty()) {
                        comments.add(t);
                    }
                }
                pos = end + 2;
                newlines = 0;
            } else {
                break;
            }
        }
        return comments.isEmpty() ? null : String.join("\n", comments);
    }

    /** A {@code //} comment on the same line, right after a value (consumed), else null. */
    @Nullable
    private String sameLineComment() {
        int i = pos;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t' || s.charAt(i) == ',')) {
            i++;
        }
        if (s.startsWith("//", i)) {
            int end = lineEnd(i);
            // the comma (if any) is left for the caller; the comment must not describe the next key too
            consumedComment = i;
            return clean(s.substring(i + 2, end));
        }
        return null;
    }

    private int lineEnd(int from) {
        int end = from;
        while (end < s.length() && s.charAt(end) != '\n' && s.charAt(end) != '\r') {
            end++;
        }
        return end;
    }

    private static String clean(String text) {
        String t = text.startsWith(" ") ? text.substring(1) : text;
        return t.stripTrailing();
    }

    // ------------------------------------------------------------------ helpers

    private boolean eof() {
        return pos >= s.length();
    }

    private char peek() {
        return s.charAt(pos);
    }

    private void expect(char c) throws FormatException {
        if (eof() || peek() != c) {
            throw error("Expected '" + c + "'");
        }
        pos++;
    }

    private FormatException error(String message) {
        return errorAt(message, pos);
    }

    private FormatException errorAt(String message, int offset) {
        int line = 1;
        int col = 1;
        for (int i = 0; i < Math.min(offset, s.length()); i++) {
            if (s.charAt(i) == '\n') {
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

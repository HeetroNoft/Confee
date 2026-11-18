package fr.heetstudio.confee.format.toml;

import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedTable;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.ValueKind;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TOML 1.0 parser that records the exact position of every value (spec 7.2).
 * <p>Lenient on purpose where it does not hurt (newlines inside inline tables, re-opened tables),
 * strict where a mistake would corrupt the file on save.</p>
 * <p>Internal: may change without notice.</p>
 */
public final class TomlParser {

    private static final Pattern DATE_PREFIX = Pattern.compile("\\d{4}-\\d{2}-\\d{2}.*");
    private static final Pattern TIME = Pattern.compile("\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?");

    private final String s;
    private int pos;
    private final List<ParsedValue> values = new ArrayList<>();
    private final List<ParsedTable> tables = new ArrayList<>();
    private final Set<List<String>> definedValues = new HashSet<>();
    private final Map<List<String>, Integer> arrayTableCounts = new HashMap<>();
    private List<String> currentTable = List.of();
    private boolean currentTableRepeated;

    private TomlParser(String text) {
        this.s = text;
    }

    public static ParsedDocument parse(SourceText source) throws FormatException {
        TomlParser parser = new TomlParser(source.text());
        parser.parseDocument();
        return new ParsedDocument(source, parser.values, parser.tables);
    }

    public static ParsedDocument parse(String text) throws FormatException {
        return parse(new SourceText(text, false, SourceText.detectLineSeparator(text)));
    }

    // ------------------------------------------------------------------ document level

    private void parseDocument() throws FormatException {
        List<String> pendingComment = new ArrayList<>();
        while (!eof()) {
            skipSpaces();
            if (eof()) {
                break;
            }
            char c = peek();
            if (c == '\n' || c == '\r') {
                consumeNewline();
                pendingComment.clear();
                continue;
            }
            if (c == '#') {
                pendingComment.add(readComment());
                consumeEndOfLine();
                continue;
            }
            String comment = pendingComment.isEmpty() ? null : String.join("\n", pendingComment);
            pendingComment.clear();
            if (c == '[') {
                parseTableHeader(comment);
            } else {
                parseKeyValueLine(comment);
            }
        }
    }

    private void parseTableHeader(@Nullable String comment) throws FormatException {
        boolean array = s.startsWith("[[", pos);
        pos += array ? 2 : 1;
        skipSpaces();
        List<String> key = parseKey();
        skipSpaces();
        expect(array ? "]]" : "]");
        int tableIndex = tables.size();
        tables.add(null);

        List<String> resolved = resolveHeader(key, array);
        currentTable = resolved;
        currentTableRepeated = array || isInsideArrayTable(resolved);
        tables.set(tableIndex, new ParsedTable(resolved, comment, currentTableRepeated, false));

        skipSpaces();
        if (!eof() && peek() == '#') {
            String inline = readComment();
            tables.set(tableIndex, new ParsedTable(resolved, join(comment, inline), currentTableRepeated, false));
        }
        consumeEndOfLine();
    }

    /** Inserts element indexes for array-of-tables segments: {@code [[a]] / [a.b]} → {@code a, [0], b}. */
    private List<String> resolveHeader(List<String> key, boolean array) {
        List<String> resolved = new ArrayList<>();
        for (int i = 0; i < key.size(); i++) {
            resolved.add(key.get(i));
            boolean last = i == key.size() - 1;
            List<String> asKey = List.copyOf(resolved);
            if (last && array) {
                int index = arrayTableCounts.merge(asKey, 1, Integer::sum) - 1;
                resolved.add("[" + index + "]");
            } else if (arrayTableCounts.containsKey(asKey)) {
                resolved.add("[" + (arrayTableCounts.get(asKey) - 1) + "]");
            }
        }
        return List.copyOf(resolved);
    }

    private boolean isInsideArrayTable(List<String> path) {
        for (String segment : path) {
            if (segment.startsWith("[")) {
                return true;
            }
        }
        return false;
    }

    private void parseKeyValueLine(@Nullable String comment) throws FormatException {
        int entryStart = pos;
        List<String> key = parseKey();
        skipSpaces();
        expect("=");
        skipSpaces();
        List<String> path = concat(currentTable, key);

        int tableIndex = -1;
        if (!eof() && peek() == '{') {
            tableIndex = tables.size();
            tables.add(new ParsedTable(path, comment, currentTableRepeated, true));
        }
        int firstNested = values.size();
        Raw raw = parseValue(path, true);

        skipSpaces();
        String inline = null;
        if (!eof() && peek() == '#') {
            inline = readComment();
        }
        consumeEndOfLine();

        if (tableIndex >= 0) {
            if (inline != null) {
                tables.set(tableIndex, new ParsedTable(path, join(comment, inline), currentTableRepeated, true));
            }
            return;
        }
        if (values.size() != firstNested) {
            throw error("Unexpected nested values");
        }
        record(path, raw, join(comment, inline), entryStart);
    }

    private void record(List<String> path, Raw raw, @Nullable String comment, int entryStart) throws FormatException {
        if (!definedValues.add(path)) {
            throw errorAt("Duplicate key '" + String.join(".", path) + "'", raw.start);
        }
        boolean editable = !currentTableRepeated && (raw.kind.isScalar() || raw.kind == ValueKind.LIST);
        values.add(new ParsedValue(path, raw.kind, raw.value, raw.start, raw.end, comment, editable,
                raw.style, raw.elementKind, entryStart, currentTable));
    }

    // ------------------------------------------------------------------ keys

    private List<String> parseKey() throws FormatException {
        List<String> parts = new ArrayList<>();
        while (true) {
            parts.add(parseSimpleKey());
            int save = pos;
            skipSpaces();
            if (!eof() && peek() == '.') {
                pos++;
                skipSpaces();
            } else {
                pos = save;
                return List.copyOf(parts);
            }
        }
    }

    private String parseSimpleKey() throws FormatException {
        if (eof()) {
            throw error("Expected a key");
        }
        char c = peek();
        if (c == '"') {
            if (s.startsWith("\"\"\"", pos)) {
                throw error("Multi-line strings cannot be keys");
            }
            return readBasicString();
        }
        if (c == '\'') {
            if (s.startsWith("'''", pos)) {
                throw error("Multi-line strings cannot be keys");
            }
            return readLiteralString();
        }
        int start = pos;
        while (!eof() && isBareKeyChar(peek())) {
            pos++;
        }
        if (start == pos) {
            throw error("Invalid character '" + printable(c) + "' in key");
        }
        return s.substring(start, pos);
    }

    private static boolean isBareKeyChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    // ------------------------------------------------------------------ values

    /** Parsed value before it is recorded. */
    private record Raw(ValueKind kind, Object value, int start, int end, @Nullable Object style,
                       @Nullable ValueKind elementKind) {
    }

    /**
     * @param path   path of this value
     * @param record whether nested inline-table leaves must be recorded as values (false inside arrays)
     */
    private Raw parseValue(List<String> path, boolean record) throws FormatException {
        if (eof()) {
            throw error("Expected a value");
        }
        int start = pos;
        char c = peek();
        switch (c) {
            case '"': {
                boolean multi = s.startsWith("\"\"\"", pos);
                TomlStyle.StringStyle style;
                String value;
                if (multi) {
                    pos += 3;
                    boolean openNewline = skipFirstNewline();
                    value = readMultilineBasic();
                    style = new TomlStyle.StringStyle(TomlStyle.Quote.ML_BASIC, openNewline);
                } else {
                    value = readBasicString();
                    style = new TomlStyle.StringStyle(TomlStyle.Quote.BASIC, false);
                }
                return new Raw(ValueKind.STRING, value, start, pos, style, null);
            }
            case '\'': {
                boolean multi = s.startsWith("'''", pos);
                TomlStyle.StringStyle style;
                String value;
                if (multi) {
                    pos += 3;
                    boolean openNewline = skipFirstNewline();
                    value = readMultilineLiteral();
                    style = new TomlStyle.StringStyle(TomlStyle.Quote.ML_LITERAL, openNewline);
                } else {
                    value = readLiteralString();
                    style = new TomlStyle.StringStyle(TomlStyle.Quote.LITERAL, false);
                }
                return new Raw(ValueKind.STRING, value, start, pos, style, null);
            }
            case '[':
                return parseArray(path);
            case '{':
                return parseInlineTable(path, record);
            default:
                return parseBareValue();
        }
    }

    private Raw parseArray(List<String> path) throws FormatException {
        int start = pos;
        pos++;
        List<Object> items = new ArrayList<>();
        List<Raw> raws = new ArrayList<>();
        boolean trailingComma = false;
        boolean spaceInside = !eof() && (peek() == ' ' || peek() == '\t');
        while (true) {
            skipWhitespaceCommentsNewlines();
            if (eof()) {
                throw error("Unterminated array");
            }
            if (peek() == ']') {
                pos++;
                break;
            }
            Raw item = parseValue(concat(path, List.of("[" + raws.size() + "]")), false);
            raws.add(item);
            items.add(item.value);
            trailingComma = false;
            skipWhitespaceCommentsNewlines();
            if (eof()) {
                throw error("Unterminated array");
            }
            if (peek() == ',') {
                pos++;
                trailingComma = true;
            } else if (peek() != ']') {
                throw error("Expected ',' or ']' in array");
            }
        }
        int end = pos;
        String body = s.substring(start, end);
        boolean multiline = body.indexOf('\n') >= 0;

        ValueKind elementKind = raws.isEmpty() ? null : raws.get(0).kind;
        boolean simple = true;
        for (Raw raw : raws) {
            if (!raw.kind.isScalar() || raw.kind != elementKind) {
                simple = false;
                break;
            }
        }
        TomlStyle.StringStyle elementStringStyle = null;
        if (simple && elementKind == ValueKind.STRING) {
            elementStringStyle = (TomlStyle.StringStyle) raws.get(0).style;
        }
        String elementIndent = "";
        String closingIndent = "";
        if (multiline) {
            elementIndent = raws.isEmpty() ? "    " : indentBefore(raws.get(0).start);
            closingIndent = indentBefore(end - 1);
        }
        TomlStyle.IntegerStyle elementIntegerStyle = null;
        if (simple && elementKind == ValueKind.INTEGER) {
            elementIntegerStyle = (TomlStyle.IntegerStyle) raws.get(0).style;
        }
        TomlStyle.ArrayStyle style = new TomlStyle.ArrayStyle(multiline, spaceInside, trailingComma,
                elementIndent, closingIndent, elementStringStyle, elementIntegerStyle);
        List<Object> value = Collections.unmodifiableList(items);
        return new Raw(simple ? ValueKind.LIST : ValueKind.COMPLEX, value, start, end, style,
                simple ? elementKind : null);
    }

    /** Whitespace between the previous line break and {@code offset}, or "" if other text precedes. */
    private String indentBefore(int offset) {
        int i = offset;
        while (i > 0 && (s.charAt(i - 1) == ' ' || s.charAt(i - 1) == '\t')) {
            i--;
        }
        if (i == 0 || s.charAt(i - 1) == '\n') {
            return s.substring(i, offset);
        }
        return "";
    }

    private Raw parseInlineTable(List<String> path, boolean record) throws FormatException {
        int start = pos;
        pos++;
        Map<String, Object> map = new LinkedHashMap<>();
        Set<List<String>> localKeys = new HashSet<>();
        skipWhitespaceCommentsNewlines();
        if (!eof() && peek() == '}') {
            pos++;
            return new Raw(ValueKind.COMPLEX, Collections.unmodifiableMap(map), start, pos, null, null);
        }
        while (true) {
            skipWhitespaceCommentsNewlines();
            int entryStart = pos;
            List<String> key = parseKey();
            if (!localKeys.add(key)) {
                throw errorAt("Duplicate key '" + String.join(".", key) + "' in inline table", entryStart);
            }
            skipSpaces();
            expect("=");
            skipSpaces();
            List<String> childPath = concat(path, key);
            boolean childIsTable = !eof() && peek() == '{';
            if (record && childIsTable) {
                tables.add(new ParsedTable(childPath, null, currentTableRepeated, true));
            }
            Raw child = parseValue(childPath, record);
            putNested(map, key, child.value);
            if (record && !childIsTable) {
                record(childPath, child, null, -1);
            }
            skipWhitespaceCommentsNewlines();
            if (eof()) {
                throw error("Unterminated inline table");
            }
            if (peek() == ',') {
                pos++;
            } else if (peek() == '}') {
                pos++;
                break;
            } else {
                throw error("Expected ',' or '}' in inline table");
            }
        }
        return new Raw(ValueKind.COMPLEX, Collections.unmodifiableMap(map), start, pos, null, null);
    }

    @SuppressWarnings("unchecked")
    private static void putNested(Map<String, Object> map, List<String> key, Object value) {
        Map<String, Object> target = map;
        for (int i = 0; i < key.size() - 1; i++) {
            target = (Map<String, Object>) target.computeIfAbsent(key.get(i), k -> new LinkedHashMap<>());
        }
        target.put(key.get(key.size() - 1), value);
    }

    /** Booleans, numbers, dates. */
    private Raw parseBareValue() throws FormatException {
        int start = pos;
        while (!eof() && !isValueDelimiter(peek())) {
            pos++;
        }
        String token = s.substring(start, pos);
        // A date-time may use a space instead of 'T' between date and time
        if (DATE_PREFIX.matcher(token).matches() && token.length() == 10
                && pos + 1 < s.length() && s.charAt(pos) == ' ' && Character.isDigit(s.charAt(pos + 1))) {
            int save = pos;
            pos++;
            while (!eof() && !isValueDelimiter(peek())) {
                pos++;
            }
            String time = s.substring(save + 1, pos);
            if (time.length() >= 5 && TIME.matcher(time.substring(0, Math.min(time.length(), 5))).matches()) {
                token = s.substring(start, pos);
            } else {
                pos = save;
            }
        }
        if (token.isEmpty()) {
            throw error("Expected a value");
        }
        if (token.equals("true") || token.equals("false")) {
            return new Raw(ValueKind.BOOLEAN, Boolean.valueOf(token), start, pos, null, null);
        }
        if (DATE_PREFIX.matcher(token).matches() || TIME.matcher(token).matches()) {
            return new Raw(ValueKind.DATETIME, token, start, pos, null, null);
        }
        return parseNumber(token, start);
    }

    private static boolean isValueDelimiter(char c) {
        return c == ' ' || c == '\t' || c == ',' || c == ']' || c == '}' || c == '#' || c == '\n' || c == '\r';
    }

    private Raw parseNumber(String token, int start) throws FormatException {
        String t = token;
        switch (t) {
            case "inf", "+inf" -> {
                return new Raw(ValueKind.FLOAT, Double.POSITIVE_INFINITY, start, pos, null, null);
            }
            case "-inf" -> {
                return new Raw(ValueKind.FLOAT, Double.NEGATIVE_INFINITY, start, pos, null, null);
            }
            case "nan", "+nan", "-nan" -> {
                return new Raw(ValueKind.FLOAT, Double.NaN, start, pos, null, null);
            }
            default -> {
            }
        }
        if (!validUnderscores(t)) {
            throw errorAt("Invalid number '" + token + "'", start);
        }
        String digits = t.replace("_", "");
        boolean plus = digits.startsWith("+");
        int radix = 10;
        boolean upper = false;
        if (digits.length() > 2 && digits.charAt(0) == '0' && "xob".indexOf(digits.charAt(1)) >= 0) {
            radix = switch (digits.charAt(1)) {
                case 'x' -> 16;
                case 'o' -> 8;
                default -> 2;
            };
            String body = digits.substring(2);
            upper = !body.equals(body.toLowerCase());
            try {
                long value = Long.parseUnsignedLong(body, radix);
                if (value < 0) {
                    throw errorAt("Integer out of range '" + token + "'", start);
                }
                return new Raw(ValueKind.INTEGER, value, start, pos,
                        new TomlStyle.IntegerStyle(radix, upper, false), null);
            } catch (NumberFormatException e) {
                throw errorAt("Invalid number '" + token + "'", start);
            }
        }
        boolean isFloat = digits.indexOf('.') >= 0 || digits.indexOf('e') >= 0 || digits.indexOf('E') >= 0;
        try {
            if (isFloat) {
                checkDecimalShape(digits, token, start, true);
                return new Raw(ValueKind.FLOAT, Double.parseDouble(digits), start, pos, null, null);
            }
            checkDecimalShape(digits, token, start, false);
            return new Raw(ValueKind.INTEGER, Long.parseLong(plus ? digits.substring(1) : digits), start, pos,
                    new TomlStyle.IntegerStyle(10, false, plus), null);
        } catch (NumberFormatException e) {
            throw errorAt("Invalid value '" + token + "'", start);
        }
    }

    /** Rejects what Java accepts but TOML does not (leading zeros, "1.", ".5", hex floats, "d"/"f" suffixes). */
    private void checkDecimalShape(String digits, String token, int start, boolean isFloat) throws FormatException {
        String unsigned = digits.startsWith("+") || digits.startsWith("-") ? digits.substring(1) : digits;
        String pattern = isFloat
                ? "(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"
                : "0|[1-9][0-9]*";
        if (!unsigned.matches(pattern)) {
            throw errorAt("Invalid value '" + token + "'", start);
        }
    }

    private static boolean validUnderscores(String t) {
        for (int i = 0; i < t.length(); i++) {
            if (t.charAt(i) == '_') {
                boolean before = i > 0 && Character.isLetterOrDigit(t.charAt(i - 1));
                boolean after = i + 1 < t.length() && Character.isLetterOrDigit(t.charAt(i + 1));
                if (!before || !after) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ strings

    private String readBasicString() throws FormatException {
        pos++; // opening quote
        StringBuilder out = new StringBuilder();
        while (true) {
            if (eof()) {
                throw error("Unterminated string");
            }
            char c = s.charAt(pos);
            if (c == '"') {
                pos++;
                return out.toString();
            }
            if (c == '\n' || c == '\r') {
                throw error("Unterminated string");
            }
            if (c == '\\') {
                readEscape(out);
            } else {
                out.append(c);
                pos++;
            }
        }
    }

    private String readLiteralString() throws FormatException {
        pos++;
        int start = pos;
        while (true) {
            if (eof() || s.charAt(pos) == '\n' || s.charAt(pos) == '\r') {
                throw error("Unterminated literal string");
            }
            if (s.charAt(pos) == '\'') {
                String value = s.substring(start, pos);
                pos++;
                return value;
            }
            pos++;
        }
    }

    /** Skips the newline that may directly follow an opening multi-line delimiter. */
    private boolean skipFirstNewline() {
        if (s.startsWith("\r\n", pos)) {
            pos += 2;
            return true;
        }
        if (s.startsWith("\n", pos)) {
            pos++;
            return true;
        }
        return false;
    }

    private String readMultilineBasic() throws FormatException {
        StringBuilder out = new StringBuilder();
        while (true) {
            if (eof()) {
                throw error("Unterminated multi-line string");
            }
            if (s.startsWith("\"\"\"", pos)) {
                int quotes = countRun('"');
                if (quotes > 5) {
                    throw error("Too many quotes closing a multi-line string");
                }
                out.append("\"".repeat(quotes - 3));
                pos += quotes;
                return out.toString();
            }
            char c = s.charAt(pos);
            if (c == '\\') {
                int save = pos;
                pos++;
                int ws = pos;
                while (!eof() && (peek() == ' ' || peek() == '\t')) {
                    pos++;
                }
                if (!eof() && (peek() == '\n' || s.startsWith("\r\n", pos))) {
                    // line-ending backslash: trim all whitespace and newlines that follow
                    while (!eof() && Character.isWhitespace(peek())) {
                        pos++;
                    }
                    continue;
                }
                pos = save;
                if (ws != save + 1) {
                    throw error("Invalid escape");
                }
                readEscape(out);
            } else {
                out.append(c);
                pos++;
            }
        }
    }

    private String readMultilineLiteral() throws FormatException {
        int start = pos;
        while (true) {
            if (eof()) {
                throw error("Unterminated multi-line literal string");
            }
            if (s.startsWith("'''", pos)) {
                int quotes = countRun('\'');
                if (quotes > 5) {
                    throw error("Too many quotes closing a multi-line literal string");
                }
                String value = s.substring(start, pos) + "'".repeat(quotes - 3);
                pos += quotes;
                return value;
            }
            pos++;
        }
    }

    private int countRun(char c) {
        int i = pos;
        while (i < s.length() && s.charAt(i) == c) {
            i++;
        }
        return i - pos;
    }

    private void readEscape(StringBuilder out) throws FormatException {
        pos++; // backslash
        if (eof()) {
            throw error("Unterminated escape");
        }
        char e = s.charAt(pos++);
        switch (e) {
            case 'b' -> out.append('\b');
            case 't' -> out.append('\t');
            case 'n' -> out.append('\n');
            case 'f' -> out.append('\f');
            case 'r' -> out.append('\r');
            case 'e' -> out.append('\u001B');
            case '"' -> out.append('"');
            case '\\' -> out.append('\\');
            case 'u' -> out.appendCodePoint(readHex(4));
            case 'U' -> out.appendCodePoint(readHex(8));
            default -> throw errorAt("Invalid escape '\\" + e + "'", pos - 2);
        }
    }

    private int readHex(int length) throws FormatException {
        if (pos + length > s.length()) {
            throw error("Invalid unicode escape");
        }
        try {
            int cp = Integer.parseUnsignedInt(s.substring(pos, pos + length), 16);
            if (!Character.isValidCodePoint(cp) || (cp >= 0xD800 && cp <= 0xDFFF)) {
                throw error("Invalid unicode code point");
            }
            pos += length;
            return cp;
        } catch (NumberFormatException e) {
            throw error("Invalid unicode escape");
        }
    }

    // ------------------------------------------------------------------ low level

    private boolean eof() {
        return pos >= s.length();
    }

    private char peek() {
        return s.charAt(pos);
    }

    private void skipSpaces() {
        while (!eof() && (peek() == ' ' || peek() == '\t')) {
            pos++;
        }
    }

    private void skipWhitespaceCommentsNewlines() throws FormatException {
        while (!eof()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\n') {
                pos++;
            } else if (c == '\r') {
                consumeNewline();
            } else if (c == '#') {
                readComment();
            } else {
                return;
            }
        }
    }

    /** Reads a comment starting at '#', up to (not including) the line break. Returns its text. */
    private String readComment() {
        pos++;
        int start = pos;
        while (!eof() && peek() != '\n' && !s.startsWith("\r\n", pos)) {
            pos++;
        }
        String text = s.substring(start, pos);
        if (text.startsWith(" ")) {
            text = text.substring(1);
        }
        return text.stripTrailing();
    }

    private void consumeNewline() throws FormatException {
        if (s.startsWith("\r\n", pos)) {
            pos += 2;
        } else if (s.startsWith("\n", pos)) {
            pos++;
        } else {
            throw error("Expected a line break");
        }
    }

    private void consumeEndOfLine() throws FormatException {
        skipSpaces();
        if (eof()) {
            return;
        }
        if (peek() == '#') {
            readComment();
        }
        if (!eof()) {
            if (peek() != '\n' && peek() != '\r') {
                throw error("Unexpected '" + printable(peek()) + "' after value");
            }
            consumeNewline();
        }
    }

    private void expect(String token) throws FormatException {
        if (!s.startsWith(token, pos)) {
            throw error("Expected '" + token + "'");
        }
        pos += token.length();
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

    private static String printable(char c) {
        return c < 0x20 ? String.format("\\u%04X", (int) c) : String.valueOf(c);
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
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

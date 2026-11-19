package fr.heetstudio.confee.format.hocon;

import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ListStyle;
import fr.heetstudio.confee.format.Nested;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedList;
import fr.heetstudio.confee.format.ParsedTable;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.ValueKind;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * HOCON parser (spec 7.1, V1.1: the whole specification) that records the exact position of every value.
 * <ul>
 *   <li>Root with or without braces, {@code =} / {@code :} / nothing before {@code {}, commas or line breaks,
 *       {@code #} and {@code //} comments, quoted / unquoted / {@code """} strings, path keys ({@code a.b.c = 1}).</li>
 *   <li>Duplicate keys: objects are merged, any other value replaces the previous one; only the value that counts is
 *       kept, so editing it changes what the mod reads.</li>
 *   <li>Value concatenation: strings ({@code foo bar}) and arrays ({@code [1] [2]}) without substitution are edited as
 *       one value; with a substitution, the value is read-only.</li>
 *   <li>Substitutions ({@code ${a.b}}, {@code ${?ENV}}) and {@code +=}: read-only, shown with the value they resolve
 *       to (the referenced value stays editable where it is written).</li>
 *   <li>{@code include "file"} / {@code file(...)} / {@code required(...)}: the included file is read too, and its
 *       values are edited in that file. {@code url(...)} and {@code classpath(...)} are ignored.</li>
 * </ul>
 * <p>Internal: may change without notice.</p>
 */
public final class HoconParser {

    private static final Pattern NUMBER = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
    private static final String FORBIDDEN = "$\"{}[]:=,+#`^?!@*&\\";
    private static final int MAX_INCLUDE_DEPTH = 16;

    /** Finds the text of an included file. */
    @FunctionalInterface
    public interface IncludeResolver {
        /**
         * @param from the including file, or null for a text without a file
         * @param name the name written in the include ({@code "other.conf"}, {@code "other"})
         * @return the file and its text, or null when it does not exist
         */
        @Nullable
        Source resolve(@Nullable Path from, String name) throws FormatException;
    }

    public record Source(Path file, SourceText text) {
    }

    /**
     * One file of the result: the main file first, then the included files in the order they are read.
     *
     * @param file     the file, or null for the main text when it has no file
     * @param document its values that count (positions in this file)
     * @param prefix   path of the object it is included in (empty for the main file)
     */
    public record Member(@Nullable Path file, ParsedDocument document, List<String> prefix) {
    }

    /** Includes are ignored. */
    public static final IncludeResolver NO_INCLUDES = (from, name) -> null;

    // ------------------------------------------------------------------ shared by the files of one config

    /** Something defined somewhere, in reading order; {@code dead} once a later definition replaces it. */
    private static final class Entry {
        final int file;
        final Object item;
        final List<String> path;
        final boolean nested;
        final boolean object;
        boolean dead;

        Entry(int file, Object item, List<String> path, boolean nested, boolean object) {
            this.file = file;
            this.item = item;
            this.path = path;
            this.nested = nested;
            this.object = object;
        }
    }

    /** A substitution to show with its resolved value once everything is read. */
    private record Pending(Entry entry, String raw, List<Subst> substs, List<String> base) {
    }

    private record Subst(List<String> path, boolean optional, int start, int end) {
    }

    private static final class Shared {
        final IncludeResolver resolver;
        final List<Entry> entries = new ArrayList<>();
        final List<SourceText> sources = new ArrayList<>();
        final List<Path> files = new ArrayList<>();
        final List<List<String>> prefixes = new ArrayList<>();
        final Set<Path> active = new HashSet<>();
        final List<Pending> pending = new ArrayList<>();

        Shared(IncludeResolver resolver) {
            this.resolver = resolver;
        }
    }

    private final Shared shared;
    private final int file;
    private final String s;
    private final List<String> prefix;
    private final int depth;
    private int pos;
    /** True while reading the elements of a list of objects / lists: values go to the nested part. */
    private boolean inElement;

    private HoconParser(Shared shared, int file, String text, List<String> prefix, int depth) {
        this.shared = shared;
        this.file = file;
        this.s = text;
        this.prefix = prefix;
        this.depth = depth;
    }

    /** Reads a single text (includes ignored). */
    public static ParsedDocument parse(SourceText source) throws FormatException {
        return parse(source, List.of());
    }

    /** Reads a single text (includes ignored) whose keys are under {@code prefix}, like an included file. */
    public static ParsedDocument parse(SourceText source, List<String> prefix) throws FormatException {
        return parse(null, source, NO_INCLUDES, prefix).get(0).document();
    }

    /**
     * Reads a config and the files it includes.
     *
     * @return the main file first, then each included file
     */
    public static List<Member> parse(@Nullable Path file, SourceText source, IncludeResolver resolver) throws FormatException {
        return parse(file, source, resolver, List.of());
    }

    private static List<Member> parse(@Nullable Path file, SourceText source, IncludeResolver resolver, List<String> prefix)
            throws FormatException {
        Shared shared = new Shared(resolver);
        shared.sources.add(source);
        shared.files.add(file);
        shared.prefixes.add(List.copyOf(prefix));
        if (file != null) {
            shared.active.add(file.toAbsolutePath().normalize());
        }
        new HoconParser(shared, 0, source.text(), List.copyOf(prefix), 0).parseDocument();
        resolveSubstitutions(shared);
        List<Member> members = new ArrayList<>();
        for (int f = 0; f < shared.sources.size(); f++) {
            List<ParsedValue> values = new ArrayList<>();
            List<ParsedTable> tables = new ArrayList<>();
            Nested nested = new Nested();
            for (Entry entry : shared.entries) {
                if (entry.dead || entry.file != f) {
                    continue;
                }
                if (entry.item instanceof ParsedValue value) {
                    (entry.nested ? nested.values() : values).add(value);
                } else if (entry.item instanceof ParsedTable table) {
                    (entry.nested ? nested.tables() : tables).add(table);
                } else if (entry.item instanceof ParsedList list) {
                    nested.lists().add(list);
                }
            }
            members.add(new Member(shared.files.get(f), new ParsedDocument(shared.sources.get(f), values, tables, false, nested),
                    shared.prefixes.get(f)));
        }
        return members;
    }

    // ------------------------------------------------------------------ document and objects

    private void parseDocument() throws FormatException {
        String comment = skipIgnored(true);
        if (!eof() && peek() == '{') {
            pos++;
            parseFields(prefix, true, skipIgnored(true));
        } else {
            parseFields(prefix, false, comment);
        }
        skipIgnored(true);
        if (!eof()) {
            throw error("Unexpected text after the end of the document");
        }
    }

    /** Fields of an object, after its '{' when {@code braced}. */
    private void parseFields(List<String> path, boolean braced, @Nullable String firstComment) throws FormatException {
        String comment = firstComment;
        boolean first = true;
        while (true) {
            if (!first) {
                comment = skipIgnored(true);
            }
            first = false;
            if (eof()) {
                if (braced) {
                    throw error("Unterminated object");
                }
                return;
            }
            char c = peek();
            if (c == '}') {
                if (!braced) {
                    throw error("Unexpected '}'");
                }
                pos++;
                return;
            }
            if (c == ',') {
                pos++;
                continue;
            }
            if (isInclude()) {
                parseInclude(path);
            } else {
                parseField(path, comment);
            }
            skipInlineSpaces();
            if (!eof() && peek() == ',') {
                pos++;
            }
        }
    }

    private void parseField(List<String> path, @Nullable String comment) throws FormatException {
        int keyStart = pos;
        List<String> key = parseKey();
        skipInlineSpaces();
        List<String> fullPath = concat(path, key);
        // a path key ("a.b.c") makes objects of its first segments
        for (int i = 1; i < key.size(); i++) {
            List<String> objectPath = concat(path, key.subList(0, i));
            replaced(objectPath, shared.entries.size(), true);
        }
        int mark = shared.entries.size();
        if (eof()) {
            throw error("Expected a value after the key");
        }
        if (peek() == '{') {
            parseObjectValue(fullPath, comment, mark);
            return;
        }
        boolean append = false;
        if (s.startsWith("+=", pos)) {
            append = true;
            pos += 2;
        } else if (peek() == '=' || peek() == ':') {
            pos++;
        } else {
            throw error("Expected '=', ':' or '{' after the key");
        }
        skipIgnored(false);
        if (eof()) {
            throw error("Expected a value");
        }
        if (peek() == '{' && !append) {
            parseObjectValue(fullPath, comment, mark);
            return;
        }
        Value value = parseConcatenation(fullPath, path);
        String inline = sameLineComment();
        String description = join(comment, inline);
        if (append) {
            // "a += x" means "a = ${?a} [x]": shown read-only with what it adds
            dropSince(mark);
            replaced(fullPath, mark, false);
            add(new ParsedValue(fullPath, ValueKind.COMPLEX, "+= " + text(value.start, value.end), value.start,
                    value.end, description, false, null, null, keyStart, path), fullPath, false);
            return;
        }
        recordValue(fullPath, path, value, description, keyStart, mark);
    }

    private void parseObjectValue(List<String> fullPath, @Nullable String comment, int mark) throws FormatException {
        replaced(fullPath, mark, true);
        add(new ParsedTable(fullPath, comment, false, false), fullPath, true);
        int tableEntry = shared.entries.size() - 1;
        pos++;
        parseFields(fullPath, true, skipIgnored(true));
        // object concatenation: "a { x = 1 } { y = 2 }" merges like two definitions
        while (true) {
            int save = pos;
            skipInlineSpaces();
            if (!eof() && peek() == '{') {
                pos++;
                parseFields(fullPath, true, skipIgnored(true));
            } else {
                pos = save;
                break;
            }
        }
        String inline = sameLineComment();
        if (inline != null) {
            Entry entry = shared.entries.get(tableEntry);
            shared.entries.set(tableEntry, new Entry(entry.file, new ParsedTable(fullPath, join(comment, inline), false, false),
                    fullPath, entry.nested, true));
        }
    }

    private void recordValue(List<String> fullPath, List<String> parentPath, Value value, @Nullable String comment,
                             int keyStart, int mark) {
        replaced(fullPath, mark, false);
        if (value.structure) {
            // a list of objects / lists: its elements are already recorded
            return;
        }
        ParsedValue parsed = new ParsedValue(fullPath, value.kind, value.value, value.start, value.end, comment,
                value.editable, value.style, value.elementKind, keyStart, parentPath);
        Entry entry = add(parsed, fullPath, false);
        if (!value.substs.isEmpty()) {
            shared.pending.add(new Pending(entry, text(value.start, value.end), value.substs, prefix));
        }
    }

    /**
     * A definition at {@code path} replaces what was defined there before {@code mark}: everything for a value, the
     * non-object value (and list) for an object (objects merge).
     */
    private void replaced(List<String> path, int mark, boolean object) {
        for (int i = 0; i < mark; i++) {
            Entry old = shared.entries.get(i);
            if (old.dead || !startsWith(old.path, path)) {
                continue;
            }
            if (!object) {
                old.dead = true;
            } else if (old.path.equals(path) ? !old.object : old.nested && !isUnderObjectAt(old, path)) {
                old.dead = true;
            }
        }
    }

    /** A nested entry under {@code path} that belongs to a list at {@code path} (not to an object there). */
    private static boolean isUnderObjectAt(Entry entry, List<String> path) {
        return entry.path.size() > path.size() && ParsedList.indexOf(entry.path.get(path.size())) < 0;
    }

    private Entry add(Object item, List<String> path, boolean object) {
        Entry entry = new Entry(file, item, path, inElement, object);
        shared.entries.add(entry);
        return entry;
    }

    // ------------------------------------------------------------------ include

    private boolean isInclude() {
        if (!s.startsWith("include", pos)) {
            return false;
        }
        int k = pos + 7;
        if (k >= s.length() || (s.charAt(k) != ' ' && s.charAt(k) != '\t')) {
            return false;
        }
        while (k < s.length() && (s.charAt(k) == ' ' || s.charAt(k) == '\t')) {
            k++;
        }
        return k < s.length() && (s.charAt(k) == '"' || s.startsWith("file(", k) || s.startsWith("url(", k)
                || s.startsWith("classpath(", k) || s.startsWith("required(", k));
    }

    private void parseInclude(List<String> path) throws FormatException {
        int start = pos;
        pos += 7;
        skipInlineSpaces();
        boolean required = false;
        int closing = 0;
        if (s.startsWith("required(", pos)) {
            required = true;
            closing++;
            pos += 9;
            skipInlineSpaces();
        }
        String kind = "file";
        for (String k : List.of("file(", "url(", "classpath(")) {
            if (s.startsWith(k, pos)) {
                kind = k.substring(0, k.length() - 1);
                closing++;
                pos += k.length();
                skipInlineSpaces();
                break;
            }
        }
        if (eof() || peek() != '"') {
            throw error("Expected a quoted file name after 'include'");
        }
        String name = quoted();
        for (int i = 0; i < closing; i++) {
            skipInlineSpaces();
            if (eof() || peek() != ')') {
                throw error("Expected ')'");
            }
            pos++;
        }
        if (!kind.equals("file")) {
            // url(...) and classpath(...) cannot be read from the config folder: ignored
            return;
        }
        if (depth >= MAX_INCLUDE_DEPTH) {
            throw errorAt("Too many nested includes", start);
        }
        Source included = shared.resolver.resolve(shared.files.get(file), name);
        if (included == null) {
            if (required) {
                throw errorAt("Included file not found: " + name, start);
            }
            return;
        }
        Path id = included.file().toAbsolutePath().normalize();
        if (!shared.active.add(id)) {
            throw errorAt("File included in itself: " + name, start);
        }
        try {
            int index = shared.sources.size();
            shared.sources.add(included.text());
            shared.files.add(included.file());
            shared.prefixes.add(List.copyOf(path));
            HoconParser child = new HoconParser(shared, index, included.text().text(), path, depth + 1);
            child.inElement = inElement;
            child.parseDocument();
        } catch (FormatException e) {
            throw new FormatException(name + ": " + e.getMessage(), e.line(), e.column());
        } finally {
            shared.active.remove(id);
        }
    }

    // ------------------------------------------------------------------ keys

    private List<String> parseKey() throws FormatException {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean any = false;
        while (!eof()) {
            char c = peek();
            if (c == '"') {
                current.append(quoted());
                any = true;
            } else if (c == '.') {
                if (!any) {
                    throw error("Empty key segment");
                }
                parts.add(current.toString());
                current.setLength(0);
                any = false;
                pos++;
            } else if (c == ' ' || c == '\t') {
                // spaces inside a key ("a b = 1") belong to it; before the separator they do not
                int k = pos;
                while (k < s.length() && (s.charAt(k) == ' ' || s.charAt(k) == '\t')) {
                    k++;
                }
                if (k < s.length() && isKeyChar(s.charAt(k)) && any) {
                    current.append(s, pos, k);
                    pos = k;
                } else {
                    break;
                }
            } else if (isKeyChar(c) && !s.startsWith("//", pos)) {
                current.append(c);
                any = true;
                pos++;
            } else {
                break;
            }
        }
        if (!any) {
            throw error("Expected a key");
        }
        parts.add(current.toString());
        return List.copyOf(parts);
    }

    private static boolean isKeyChar(char c) {
        return c == '"' || (!Character.isWhitespace(c) && FORBIDDEN.indexOf(c) < 0 && c != '.');
    }

    // ------------------------------------------------------------------ values

    /** A value as read: kind, Java value, text range and style, or a list whose elements were recorded. */
    private static final class Value {
        ValueKind kind;
        Object value;
        int start;
        int end;
        boolean editable;
        @Nullable
        Object style;
        @Nullable
        ValueKind elementKind;
        boolean structure;
        List<Subst> substs = new ArrayList<>();
    }

    private enum PartType { QUOTED, TRIPLE, UNQUOTED, SUBST, ARRAY, OBJECT, SPACE }

    private record Part(PartType type, int start, int end, Object value, @Nullable ArrayResult array) {
    }

    private record ArrayResult(boolean simple, List<Object> items, @Nullable ValueKind elementKind,
                               ListStyle layout, boolean commas, List<Subst> substs) {
    }

    /** One value, made of one or several parts on the same line (value concatenation). */
    private Value parseConcatenation(List<String> path, List<String> parentPath) throws FormatException {
        int mark = shared.entries.size();
        List<Part> parts = new ArrayList<>();
        List<Subst> substs = new ArrayList<>();
        while (!eof()) {
            char c = peek();
            if (c == '\n' || c == '\r' || c == ',' || c == '}' || c == ']' || c == '#' || s.startsWith("//", pos)) {
                break;
            }
            int start = pos;
            if (c == ' ' || c == '\t') {
                skipInlineSpaces();
                parts.add(new Part(PartType.SPACE, start, pos, s.substring(start, pos), null));
            } else if (s.startsWith("\"\"\"", pos)) {
                String text = tripleQuoted();
                parts.add(new Part(PartType.TRIPLE, start, pos, text, null));
            } else if (c == '"') {
                String text = quoted();
                parts.add(new Part(PartType.QUOTED, start, pos, text, null));
            } else if (s.startsWith("${", pos)) {
                Subst subst = substitution();
                substs.add(subst);
                parts.add(new Part(PartType.SUBST, start, pos, s.substring(start, pos), null));
            } else if (c == '[') {
                ArrayResult array = parseArray(path);
                substs.addAll(array.substs());
                parts.add(new Part(PartType.ARRAY, start, pos, array.items(), array));
            } else if (c == '{') {
                // an object after other parts ("a = ${x} { b = 1 }"): read, but the whole value is read-only
                pos++;
                parseFields(path, true, skipIgnored(true));
                parts.add(new Part(PartType.OBJECT, start, pos, null, null));
            } else if (FORBIDDEN.indexOf(c) >= 0) {
                throw error("Unexpected character '" + c + "'");
            } else {
                while (!eof() && !Character.isWhitespace(peek()) && FORBIDDEN.indexOf(peek()) < 0 && !s.startsWith("//", pos)) {
                    pos++;
                }
                parts.add(new Part(PartType.UNQUOTED, start, pos, s.substring(start, pos), null));
            }
        }
        // trailing spaces are not part of the value
        while (!parts.isEmpty() && parts.get(parts.size() - 1).type == PartType.SPACE) {
            parts.remove(parts.size() - 1);
        }
        while (!parts.isEmpty() && parts.get(0).type == PartType.SPACE) {
            parts.remove(0);
        }
        if (parts.isEmpty()) {
            throw error("Expected a value");
        }
        Value v = new Value();
        v.start = parts.get(0).start;
        v.end = parts.get(parts.size() - 1).end;
        v.substs = substs;
        if (parts.size() == 1) {
            single(parts.get(0), v);
            return v;
        }
        boolean hasSubst = !substs.isEmpty();
        boolean arrays = parts.stream().allMatch(p -> p.type == PartType.ARRAY || p.type == PartType.SPACE);
        boolean hasStructure = parts.stream().anyMatch(p -> p.type == PartType.OBJECT
                || p.type == PartType.ARRAY && !p.array().simple());
        if (hasStructure || hasSubst) {
            // read-only: what the parts recorded is dropped
            dropSince(mark);
            readOnly(v);
            return v;
        }
        if (arrays) {
            List<Object> items = new ArrayList<>();
            ValueKind kind = null;
            boolean same = true;
            ArrayResult first = null;
            for (Part part : parts) {
                if (part.type != PartType.ARRAY) {
                    continue;
                }
                ArrayResult array = part.array();
                first = first == null ? array : first;
                items.addAll(array.items());
                if (array.elementKind() != null) {
                    same &= kind == null || kind == array.elementKind();
                    kind = kind == null ? array.elementKind() : kind;
                }
            }
            if (!same) {
                readOnly(v);
                return v;
            }
            v.kind = ValueKind.LIST;
            v.value = Collections.unmodifiableList(items);
            v.editable = true;
            v.elementKind = kind;
            v.style = new HoconStyle.ArrayStyle(first.layout(), first.commas(), true);
            return v;
        }
        if (parts.stream().anyMatch(p -> p.type == PartType.ARRAY)) {
            readOnly(v);
            return v;
        }
        // string concatenation: the spaces between parts are kept
        StringBuilder text = new StringBuilder();
        boolean unquoted = true;
        for (Part part : parts) {
            text.append((String) part.value);
            unquoted &= part.type == PartType.UNQUOTED || part.type == PartType.SPACE;
        }
        v.kind = ValueKind.STRING;
        v.value = text.toString();
        v.editable = true;
        // "a = hello world" stays without quotes when the new text allows it
        v.style = new HoconStyle.StringStyle(unquoted ? HoconStyle.Quote.UNQUOTED : HoconStyle.Quote.CONCATENATED);
        return v;
    }

    private void single(Part part, Value v) {
        switch (part.type) {
            case QUOTED, TRIPLE -> {
                v.kind = ValueKind.STRING;
                v.value = part.value;
                v.editable = true;
                v.style = new HoconStyle.StringStyle(part.type == PartType.QUOTED ? HoconStyle.Quote.QUOTED : HoconStyle.Quote.TRIPLE);
            }
            case UNQUOTED -> {
                String text = (String) part.value;
                v.editable = true;
                if (text.equals("true") || text.equals("false")) {
                    v.kind = ValueKind.BOOLEAN;
                    v.value = Boolean.valueOf(text);
                } else if (text.equals("null")) {
                    v.kind = ValueKind.COMPLEX;
                    v.value = "null";
                    v.editable = false;
                } else if (NUMBER.matcher(text).matches()) {
                    boolean fractional = text.contains(".") || text.contains("e") || text.contains("E");
                    try {
                        v.kind = fractional ? ValueKind.FLOAT : ValueKind.INTEGER;
                        v.value = fractional ? (Object) Double.parseDouble(text) : (Object) Long.parseLong(text);
                    } catch (NumberFormatException e) {
                        // too large for a long: kept as written
                        v.kind = ValueKind.COMPLEX;
                        v.value = text;
                        v.editable = false;
                    }
                } else {
                    v.kind = ValueKind.STRING;
                    v.value = text;
                    v.style = new HoconStyle.StringStyle(HoconStyle.Quote.UNQUOTED);
                }
            }
            case SUBST -> readOnly(v);
            case ARRAY -> {
                ArrayResult array = part.array();
                if (array.simple()) {
                    v.kind = ValueKind.LIST;
                    v.value = Collections.unmodifiableList(array.items());
                    v.editable = true;
                    v.elementKind = array.elementKind();
                    v.style = new HoconStyle.ArrayStyle(array.layout(), array.commas(), false);
                } else if (!array.substs().isEmpty()) {
                    readOnly(v);
                } else {
                    v.structure = true;
                }
            }
            default -> readOnly(v);
        }
    }

    private void readOnly(Value v) {
        v.kind = ValueKind.COMPLEX;
        v.value = text(v.start, v.end).replaceAll("\\s+", " ").trim();
        v.editable = false;
        v.style = null;
        v.elementKind = null;
    }

    private void dropSince(int mark) {
        shared.entries.subList(mark, shared.entries.size()).clear();
    }

    /**
     * An array. Simple (scalars of one kind, no substitution): its items. Otherwise its elements are recorded one by
     * one with their positions (spec 8.2 bis) unless a substitution makes it read-only.
     */
    private ArrayResult parseArray(List<String> path) throws FormatException {
        int open = pos;
        pos++;
        int mark = shared.entries.size();
        boolean wasInElement = inElement;
        inElement = true;
        List<ParsedList.Item> ranges = new ArrayList<>();
        List<Value> elements = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        List<Subst> substs = new ArrayList<>();
        boolean commas = false;
        boolean trailingComma = false;
        int firstElement = -1;
        String lead = null;
        try {
            while (true) {
                int before = pos;
                String comment = skipIgnored(true);
                if (lead == null) {
                    lead = s.substring(before, pos);
                }
                if (eof()) {
                    throw error("Unterminated array");
                }
                if (peek() == ']') {
                    break;
                }
                if (peek() == ',') {
                    throw error("Unexpected ','");
                }
                List<String> itemPath = concat(path, List.of(ParsedList.segment(elements.size())));
                int start = pos;
                if (firstElement < 0) {
                    firstElement = start;
                }
                int itemStart = leadingStart(before, start, !elements.isEmpty());
                Value element;
                if (peek() == '{') {
                    add(new ParsedTable(itemPath, comment, false, false), itemPath, true);
                    pos++;
                    parseFields(itemPath, true, skipIgnored(true));
                    element = new Value();
                    element.kind = ValueKind.COMPLEX;
                    element.structure = true;
                    element.start = start;
                    element.end = pos;
                } else {
                    element = parseConcatenation(itemPath, path);
                }
                substs.addAll(element.substs);
                elements.add(element);
                comments.add(join(comment, sameLineComment()));
                ranges.add(new ParsedList.Item(itemStart, start, element.end > 0 ? element.end : pos));
                trailingComma = false;
                skipInlineSpaces();
                if (!eof() && peek() == ',') {
                    pos++;
                    commas = true;
                    trailingComma = true;
                }
            }
        } finally {
            inElement = wasInElement;
        }
        int close = pos;
        pos++;
        ValueKind kind = elements.isEmpty() ? null : elements.get(0).kind;
        boolean simple = substs.isEmpty();
        for (Value e : elements) {
            if (e.structure || !e.kind.isScalar() || e.kind != kind || !e.editable
                    || e.style instanceof HoconStyle.StringStyle ss && ss.quote() == HoconStyle.Quote.CONCATENATED) {
                simple = false;
                break;
            }
        }
        Object elementStyle = simple && !elements.isEmpty() ? elements.get(0).style : null;
        ListStyle layout = ListStyle.detect(s, open, close, firstElement, trailingComma, elementStyle);
        List<Object> items = new ArrayList<>();
        for (Value e : elements) {
            items.add(e.value);
        }
        if (simple || !substs.isEmpty()) {
            // nothing recorded element by element
            dropSince(mark);
            return new ArrayResult(simple, items, simple ? kind : null, layout, commas || elements.size() < 2, substs);
        }
        // a list of objects / lists / mixed values: each element is recorded (objects and inner lists already are)
        boolean saved = inElement;
        inElement = true;
        for (int i = 0; i < elements.size(); i++) {
            Value e = elements.get(i);
            if (e.structure) {
                continue;
            }
            List<String> itemPath = concat(path, List.of(ParsedList.segment(i)));
            add(new ParsedValue(itemPath, e.kind, e.value, e.start, e.end, comments.get(i), e.editable, e.style,
                    e.elementKind, -1, path), itemPath, false);
        }
        String indent = lead == null || lead.isEmpty() || !lead.isBlank() ? " " : lead;
        add(new ParsedList(path, open, pos, ranges, commas || elements.size() < 2 ? "," + indent : indent, "[]"), path, false);
        inElement = saved;
        return new ArrayResult(false, items, null, layout, commas, substs);
    }

    /** Start of the comment lines before an element (a comment on the previous element's line stays with it). */
    private int leadingStart(int from, int element, boolean afterElement) {
        int k = from;
        if (afterElement) {
            int c = from;
            while (c < element && (s.charAt(c) == ' ' || s.charAt(c) == '\t' || s.charAt(c) == ',')) {
                c++;
            }
            if (c < element && (s.charAt(c) == '#' || s.startsWith("//", c))) {
                while (k < element && s.charAt(k) != '\n') {
                    k++;
                }
            }
        }
        while (k < element && (Character.isWhitespace(s.charAt(k)) || s.charAt(k) == ',')) {
            k++;
        }
        return k;
    }

    private Subst substitution() throws FormatException {
        int start = pos;
        pos += 2;
        boolean optional = false;
        if (!eof() && peek() == '?') {
            optional = true;
            pos++;
        }
        skipInlineSpaces();
        List<String> path = parseKey();
        skipInlineSpaces();
        if (eof() || peek() != '}') {
            throw error("Expected '}' to close the substitution");
        }
        pos++;
        return new Subst(path, optional, start, pos);
    }

    // ------------------------------------------------------------------ substitutions

    /** Shows each value with substitutions as "raw → resolved" (resolved from the values that count). */
    private static void resolveSubstitutions(Shared shared) {
        Map<List<String>, Entry> effective = new LinkedHashMap<>();
        for (Entry entry : shared.entries) {
            if (!entry.dead && entry.item instanceof ParsedValue) {
                effective.put(entry.path, entry);
            }
        }
        for (Pending pending : shared.pending) {
            if (pending.entry.dead) {
                continue;
            }
            ParsedValue value = (ParsedValue) pending.entry.item;
            String resolved = resolve(shared, effective, pending, 0);
            ParsedValue shown = new ParsedValue(value.path(), ValueKind.COMPLEX,
                    pending.raw.replaceAll("\\s+", " ").trim() + " → " + resolved, value.start(), value.end(),
                    value.comment(), false, null, null, value.entryStart(), value.tablePath());
            int index = shared.entries.indexOf(pending.entry);
            Entry replacement = new Entry(pending.entry.file, shown, pending.entry.path, pending.entry.nested, false);
            shared.entries.set(index, replacement);
            effective.put(replacement.path, replacement);
        }
    }

    private static String resolve(Shared shared, Map<List<String>, Entry> effective, Pending pending, int depth) {
        String raw = pending.raw;
        int rawStart = ((ParsedValue) pending.entry.item).start();
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (Subst subst : pending.substs) {
            int from = subst.start - rawStart;
            int to = subst.end - rawStart;
            if (from < cursor || to > raw.length()) {
                continue;
            }
            out.append(unquote(raw.substring(cursor, from)));
            out.append(lookup(shared, effective, pending.base, subst, depth));
            cursor = to;
        }
        out.append(unquote(raw.substring(Math.min(cursor, raw.length()))));
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private static final Pattern QUOTED = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** The text a quoted part stands for ({@code "a:"} → {@code a:}), as the mod reads it. */
    private static String unquote(String text) {
        return QUOTED.matcher(text).replaceAll(m -> java.util.regex.Matcher.quoteReplacement(
                m.group(1).replace("\\\"", "\"").replace("\\\\", "\\")));
    }

    private static String lookup(Shared shared, Map<List<String>, Entry> effective, List<String> base, Subst subst, int depth) {
        if (depth > 8) {
            return "…";
        }
        List<List<String>> candidates = new ArrayList<>();
        if (!base.isEmpty()) {
            candidates.add(concat(base, subst.path));
        }
        candidates.add(subst.path);
        for (List<String> candidate : candidates) {
            Entry entry = effective.get(candidate);
            if (entry != null && entry.item instanceof ParsedValue value) {
                if (value.kind() == ValueKind.COMPLEX) {
                    for (Pending other : shared.pending) {
                        if (other.entry == entry) {
                            return resolve(shared, effective, other, depth + 1);
                        }
                    }
                    String text = String.valueOf(value.value());
                    int arrow = text.lastIndexOf(" → ");
                    return arrow >= 0 ? text.substring(arrow + 3) : text;
                }
                return String.valueOf(value.value());
            }
            for (Entry any : effective.values()) {
                if (startsWith(any.path, candidate) && any.path.size() > candidate.size()) {
                    return "{…}";
                }
            }
        }
        String env = System.getenv(String.join(".", subst.path));
        if (env != null) {
            return env;
        }
        return subst.optional ? "" : "?";
    }

    // ------------------------------------------------------------------ strings

    private String quoted() throws FormatException {
        pos++;
        StringBuilder out = new StringBuilder();
        while (true) {
            if (eof()) {
                throw error("Unterminated string");
            }
            char c = s.charAt(pos++);
            if (c == '"') {
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
                case '"', '\\', '/' -> out.append(e);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
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
                default -> throw errorAt("Invalid escape '\\" + e + "'", pos - 2);
            }
        }
    }

    private String tripleQuoted() throws FormatException {
        int open = pos;
        pos += 3;
        int close = s.indexOf("\"\"\"", pos);
        if (close < 0) {
            throw errorAt("Unterminated \"\"\" string", open);
        }
        // extra quotes just before the closing ones belong to the text
        while (close + 3 < s.length() && s.charAt(close + 3) == '"') {
            close++;
        }
        String text = s.substring(pos, close);
        pos = close + 3;
        return text;
    }

    // ------------------------------------------------------------------ whitespace and comments

    /**
     * Skips whitespace (line breaks too when {@code newlines}) and comments; returns the comment lines just before the
     * next token (a blank line resets them).
     */
    @Nullable
    private String skipIgnored(boolean newlines) {
        List<String> comments = new ArrayList<>();
        int breaks = 0;
        while (!eof()) {
            char c = peek();
            if (c == '\n') {
                if (!newlines) {
                    break;
                }
                breaks++;
                if (breaks >= 2) {
                    comments.clear();
                }
                pos++;
            } else if (c == ' ' || c == '\t' || c == '\r' || c == '﻿') {
                pos++;
            } else if (c == '#' || s.startsWith("//", pos)) {
                if (!newlines) {
                    break;
                }
                int start = pos + (c == '#' ? 1 : 2);
                int end = lineEnd(pos);
                comments.add(clean(s.substring(start, end)));
                breaks = 0;
                pos = end;
            } else {
                break;
            }
        }
        return comments.isEmpty() ? null : String.join("\n", comments);
    }

    /** A comment on the same line as what was just read (left in place for the next skip). */
    @Nullable
    private String sameLineComment() {
        int k = pos;
        while (k < s.length() && (s.charAt(k) == ' ' || s.charAt(k) == '\t' || s.charAt(k) == ',')) {
            k++;
        }
        if (k < s.length() && (s.charAt(k) == '#' || s.startsWith("//", k))) {
            int start = k + (s.charAt(k) == '#' ? 1 : 2);
            String text = clean(s.substring(start, lineEnd(k)));
            pos = lineEnd(k);
            return text;
        }
        return null;
    }

    private void skipInlineSpaces() {
        while (!eof() && (peek() == ' ' || peek() == '\t')) {
            pos++;
        }
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

    private String text(int start, int end) {
        return s.substring(start, end);
    }

    private boolean eof() {
        return pos >= s.length();
    }

    private char peek() {
        return s.charAt(pos);
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

    private static List<String> concat(List<String> path, List<String> more) {
        List<String> out = new ArrayList<>(path);
        out.addAll(more);
        return List.copyOf(out);
    }

    private static boolean startsWith(List<String> path, List<String> prefix) {
        return path.size() >= prefix.size() && path.subList(0, prefix.size()).equals(prefix);
    }

    @Nullable
    private static String join(@Nullable String above, @Nullable String inline) {
        if (above == null) {
            return inline;
        }
        return inline == null ? above : above + "\n" + inline;
    }
}

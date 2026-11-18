package fr.heetstudio.confee.format.cfg;

import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedTable;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.ValueKind;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Forge legacy {@code .cfg} parser (1.12 and older, still used by some ports), with positions:
 * <pre>
 * general {
 *     # Max particles
 *     # Min: 0
 *     # Max: 500
 *     I:maxParticles=250
 *     S:blocked &lt;
 *         minecraft:tnt
 *      &gt;
 * }
 * </pre>
 * Types come from the prefix ({@code B} boolean, {@code I} integer, {@code D} decimal, {@code S} text, spec 8.3);
 * {@code # Min: / Max:} and {@code [range: a ~ b]} comments give the bounds.
 * <p>Internal: may change without notice.</p>
 */
public final class ForgeCfgParser {

    private static final Pattern MIN = Pattern.compile("(?i)min(?:imum)?\\s*:\\s*([-+]?[0-9.eE]+)");
    private static final Pattern MAX = Pattern.compile("(?i)max(?:imum)?\\s*:\\s*([-+]?[0-9.eE]+)");
    private static final Pattern RANGE = Pattern.compile("(?i)\\[range:\\s*([-+]?[0-9.eE]+)\\s*~\\s*([-+]?[0-9.eE]+)");
    private static final Pattern ENTRY = Pattern.compile("([BIDS]):(\"[^\"]*\"|[^=<\\s]+)\\s*(=|<)");

    /** Element type for lists, from the prefix. */
    public record Style(char prefix, String itemIndent) {
    }

    private record Line(int start, int end, int indent, String text) {
    }

    private ForgeCfgParser() {
    }

    /** @throws FormatException when the file is not in Forge's format (it may still be INI) */
    public static ParsedDocument parse(SourceText source) throws FormatException {
        String s = source.text();
        List<Line> lines = new ArrayList<>();
        int pos = 0;
        while (pos <= s.length()) {
            int lf = s.indexOf('\n', pos);
            int end = lf < 0 ? s.length() : lf;
            int contentEnd = end > pos && s.charAt(end - 1) == '\r' ? end - 1 : end;
            int indent = 0;
            while (pos + indent < contentEnd && (s.charAt(pos + indent) == ' ' || s.charAt(pos + indent) == '\t')) {
                indent++;
            }
            lines.add(new Line(pos, contentEnd, indent, s.substring(pos + indent, contentEnd).stripTrailing()));
            if (lf < 0) {
                break;
            }
            pos = lf + 1;
        }

        List<ParsedValue> values = new ArrayList<>();
        List<ParsedTable> tables = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        List<String> comment = new ArrayList<>();
        List<String> path = new ArrayList<>();
        boolean sawEntry = false;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            String t = line.text;
            if (t.isEmpty()) {
                comment.clear();
                continue;
            }
            if (t.startsWith("#")) {
                comment.add(t.substring(1).trim());
                continue;
            }
            if (t.startsWith("~")) {
                // ~CONFIG_VERSION: 1
                continue;
            }
            if (t.equals("}")) {
                if (path.isEmpty()) {
                    throw error("Unexpected '}'", i);
                }
                path.remove(path.size() - 1);
                comment.clear();
                continue;
            }
            if (t.endsWith("{") && !t.contains("=")) {
                String name = t.substring(0, t.length() - 1).trim();
                if (name.startsWith("\"") && name.endsWith("\"") && name.length() >= 2) {
                    name = name.substring(1, name.length() - 1);
                }
                path.add(name);
                tables.add(new ParsedTable(List.copyOf(path), joined(comment), false, false));
                comment.clear();
                continue;
            }
            Matcher m = ENTRY.matcher(t);
            if (!m.lookingAt()) {
                throw error("Not a Forge config line", i);
            }
            sawEntry = true;
            char prefix = m.group(1).charAt(0);
            String key = m.group(2);
            if (key.startsWith("\"")) {
                key = key.substring(1, key.length() - 1);
            }
            List<String> keyPath = new ArrayList<>(path);
            keyPath.add(key);
            List<String> fullPath = List.copyOf(keyPath);
            if (!seen.add(fullPath)) {
                throw error("Duplicate key '" + key + "'", i);
            }
            Number[] bounds = bounds(comment);
            String desc = joined(comment);
            comment.clear();
            int entryStart = line.start + line.indent;
            if (m.group(3).equals("=")) {
                int valueStart = entryStart + m.end();
                String text = s.substring(valueStart, line.end);
                ValueKind kind = kindOf(prefix);
                Object value = text;
                boolean editable = true;
                try {
                    value = convert(text, kind);
                } catch (NumberFormatException e) {
                    kind = ValueKind.COMPLEX;
                    editable = false;
                }
                values.add(new ParsedValue(fullPath, kind, value, valueStart, line.end, desc, editable,
                        new Style(prefix, ""), null, entryStart, List.copyOf(path), bounds[0], bounds[1]));
            } else {
                // list: items on the next lines until '>'
                List<Object> items = new ArrayList<>();
                int start = -1;
                int end = -1;
                String itemIndent = "";
                boolean ok = true;
                int j = i + 1;
                for (; j < lines.size(); j++) {
                    Line item = lines.get(j);
                    if (item.text.equals(">")) {
                        break;
                    }
                    if (start < 0) {
                        start = item.start + item.indent;
                        itemIndent = s.substring(item.start, item.start + item.indent);
                    }
                    end = item.start + item.indent + item.text.length();
                    try {
                        items.add(convert(item.text, kindOf(prefix)));
                    } catch (NumberFormatException e) {
                        ok = false;
                    }
                }
                if (j >= lines.size()) {
                    throw error("Unterminated list", i);
                }
                i = j;
                // an empty list has no place to write elements: read-only
                boolean editable = ok && start >= 0;
                if (start < 0) {
                    start = end = lines.get(j).start + lines.get(j).indent;
                }
                values.add(new ParsedValue(fullPath, editable ? ValueKind.LIST : ValueKind.COMPLEX,
                        editable ? Collections.unmodifiableList(items) : String.join(", ", items.stream().map(String::valueOf).toList()),
                        start, end, desc, editable, new Style(prefix, itemIndent), editable ? kindOf(prefix) : null,
                        entryStart, List.copyOf(path), bounds[0], bounds[1]));
            }
        }
        if (!path.isEmpty()) {
            throw error("Missing '}'", lines.size() - 1);
        }
        if (!sawEntry) {
            throw new FormatException("Not a Forge config file", 1, 1);
        }
        return new ParsedDocument(source, values, tables);
    }

    static ValueKind kindOf(char prefix) {
        return switch (prefix) {
            case 'B' -> ValueKind.BOOLEAN;
            case 'I' -> ValueKind.INTEGER;
            case 'D' -> ValueKind.FLOAT;
            default -> ValueKind.STRING;
        };
    }

    private static Object convert(String text, ValueKind kind) {
        return switch (kind) {
            case BOOLEAN -> {
                if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                    throw new NumberFormatException(text);
                }
                yield Boolean.parseBoolean(text);
            }
            case INTEGER -> Long.parseLong(text.trim());
            case FLOAT -> Double.parseDouble(text.trim());
            default -> text;
        };
    }

    private static Number[] bounds(List<String> comment) {
        Number min = null;
        Number max = null;
        for (String line : comment) {
            Matcher range = RANGE.matcher(line);
            if (range.find()) {
                min = number(range.group(1));
                max = number(range.group(2));
                continue;
            }
            Matcher mn = MIN.matcher(line);
            if (mn.find() && line.trim().toLowerCase(java.util.Locale.ROOT).startsWith("min")) {
                min = number(mn.group(1));
            }
            Matcher mx = MAX.matcher(line);
            if (mx.find() && line.trim().toLowerCase(java.util.Locale.ROOT).startsWith("max")) {
                max = number(mx.group(1));
            }
        }
        return new Number[]{min, max};
    }

    @Nullable
    private static Number number(String text) {
        try {
            return text.contains(".") || text.contains("e") || text.contains("E") ? (Number) Double.parseDouble(text)
                    : (Number) Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static String joined(List<String> comment) {
        return comment.isEmpty() ? null : String.join("\n", comment);
    }

    private static FormatException error(String message, int lineIndex) {
        return new FormatException(message, lineIndex + 1, 1);
    }
}

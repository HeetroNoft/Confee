package fr.heetstudio.confee.format.toml;

import fr.heetstudio.confee.core.ConfigSchema;
import fr.heetstudio.confee.core.Node;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.OptionType;
import fr.heetstudio.confee.core.Section;
import fr.heetstudio.confee.format.CommentMarker;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedValue;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Writes the whole file of a config owned by Confee (spec 7.3): declaration order, {@code @Comment} above each key,
 * range / allowed values hints, one table per section. Unknown keys of the previous file are kept (spec 5.3).
 * <p>Internal: may change without notice.</p>
 */
public final class TomlConfigWriter {

    private final String nl;
    private final StringBuilder out = new StringBuilder();
    private final Map<String, List<ParsedValue>> unknownByTable = new LinkedHashMap<>();
    @Nullable
    private final ParsedDocument previous;
    /** {@code Default: x} line under each hint ({@code @Config(writeDefaults = true)}). */
    private final boolean writeDefaults;

    private TomlConfigWriter(String lineSeparator, @Nullable ParsedDocument previous, boolean writeDefaults) {
        this.nl = lineSeparator;
        this.previous = previous;
        this.writeDefaults = writeDefaults;
    }

    /**
     * @param previous the current file content, if it exists and could be parsed: its unknown keys are kept
     */
    public static String write(ConfigSchema schema, String lineSeparator, @Nullable ParsedDocument previous) {
        TomlConfigWriter writer = new TomlConfigWriter(lineSeparator, previous, schema.writeDefaults());
        writer.collectUnknown(schema.root());
        writer.writeDocument(schema.root());
        return writer.out.toString();
    }

    // ------------------------------------------------------------------ unknown keys

    private void collectUnknown(Section root) {
        if (previous == null) {
            return;
        }
        Set<String> known = new HashSet<>();
        Set<String> sections = new HashSet<>();
        collectPaths(root, known, sections);
        for (ParsedValue value : previous.values()) {
            if (value.entryStart() < 0 || isRepeated(value.tablePath())) {
                continue; // nested in an inline table or in an array of tables: kept only through its parent
            }
            String path = value.joinedPath();
            if (known.contains(path) || collides(path, known)) {
                continue;
            }
            unknownByTable.computeIfAbsent(String.join(".", value.tablePath()), k -> new ArrayList<>()).add(value);
        }
    }

    private static void collectPaths(Section section, Set<String> options, Set<String> sections) {
        for (Node child : section.children()) {
            if (child instanceof Option<?> option) {
                options.add(option.path());
            } else if (child instanceof Section sub) {
                sections.add(sub.path());
                collectPaths(sub, options, sections);
            }
        }
    }

    /** An old key that is now a prefix of an option (or under an option) cannot be kept without breaking the file. */
    private static boolean collides(String path, Set<String> known) {
        for (String option : known) {
            if (option.startsWith(path + ".") || path.startsWith(option + ".")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRepeated(List<String> tablePath) {
        for (String segment : tablePath) {
            if (segment.startsWith("[")) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ document

    private void writeDocument(Section root) {
        if (root.comment() != null) {
            comment(root.comment());
            out.append(nl);
        }
        boolean first = writeOptions(root, true);
        writeUnknown("", first);
        writeSubSections(root);
        // tables that only contain unknown keys
        for (Map.Entry<String, List<ParsedValue>> entry : unknownByTable.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            separate();
            out.append('[').append(TomlValues.dottedKey(entry.getValue().get(0).tablePath())).append(']').append(nl);
            writeUnknown(entry.getKey(), true);
        }
    }

    private void writeSubSections(Section section) {
        for (Node child : section.children()) {
            if (child instanceof Section sub) {
                separate();
                if (sub.comment() != null) {
                    comment(sub.comment());
                }
                out.append('[').append(TomlValues.dottedKey(List.of(sub.path().split("\\.")))).append(']').append(nl);
                boolean first = writeOptions(sub, true);
                writeUnknown(sub.path(), first);
                writeSubSections(sub);
            }
        }
    }

    /** @return whether nothing was written (so the next entry is the first of its table) */
    private boolean writeOptions(Section section, boolean first) {
        for (Node child : section.children()) {
            if (child instanceof Option<?> option && option.get() != null) {
                if (!first) {
                    out.append(nl);
                }
                writeOption(option);
                first = false;
            }
        }
        return first;
    }

    private void writeOption(Option<?> option) {
        if (option.comment() != null) {
            comment(option.comment());
        }
        String hint = hint(option);
        if (hint != null) {
            comment(hint);
        }
        String defaultLine = writeDefaults ? fr.heetstudio.confee.format.ConfigTree.defaultHint(option) : null;
        if (defaultLine != null) {
            comment(defaultLine);
        }
        out.append(TomlValues.key(option.key())).append(" = ").append(value(option, option.get(), nl)).append(nl);
    }

    private void writeUnknown(String tablePath, boolean first) {
        List<ParsedValue> unknown = unknownByTable.get(tablePath);
        if (unknown == null || previous == null) {
            return;
        }
        String text = previous.source().text();
        for (ParsedValue value : unknown) {
            if (!first) {
                out.append(nl);
            }
            if (value.comment() != null) {
                carriedComment(value.comment());
            }
            out.append(text, value.entryStart(), value.end()).append(nl);
            first = false;
        }
        unknown.clear();
    }

    private void separate() {
        if (out.length() > 0) {
            out.append(nl);
        }
    }

    private void comment(String text) {
        for (String line : text.split("\r?\n", -1)) {
            out.append(CommentMarker.generated("#", line)).append(nl);
        }
    }

    private void carriedComment(String text) {
        for (String line : text.split("\r?\n", -1)) {
            out.append(CommentMarker.carried("#", line)).append(nl);
        }
    }

    // ------------------------------------------------------------------ values

    /** Range / allowed values hint (spec 7.3), or null. */
    @Nullable
    static String hint(Option<?> option) {
        boolean eachValue = option.elementMin() != null || option.elementMax() != null;
        Number min = eachValue ? option.elementMin() : option.min();
        Number max = eachValue ? option.elementMax() : option.max();
        OptionType numberType = eachValue && option.elementType() != null ? option.elementType() : option.type();
        String suffix = eachValue ? " (each value)" : "";
        if (min != null && max != null) {
            return "Range: " + number(min, numberType) + " ~ " + number(max, numberType) + suffix;
        }
        if (min != null) {
            return "Range: >= " + number(min, numberType) + suffix;
        }
        if (max != null) {
            return "Range: <= " + number(max, numberType) + suffix;
        }
        List<?> allowed = option.allowedValues();
        if (allowed == null && option.type() == OptionType.ENUM && option.enumClass() != null) {
            allowed = List.of(option.enumClass().getEnumConstants());
        }
        if (allowed != null) {
            StringJoiner joiner = new StringJoiner(", ", "Allowed: ", "");
            for (Object value : allowed) {
                joiner.add(value instanceof Enum<?> e ? e.name() : String.valueOf(value));
            }
            return joiner.toString();
        }
        return null;
    }

    private static String number(Number n, OptionType type) {
        return switch (type) {
            case INT, LONG -> Long.toString(n.longValue());
            case FLOAT -> Float.toString(n.floatValue());
            default -> Double.toString(n.doubleValue());
        };
    }

    static String value(Option<?> option, Object value, String nl) {
        if (option.type() == OptionType.MAP) {
            StringBuilder b = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                b.append(first ? " " : ", ").append(TomlValues.key(String.valueOf(entry.getKey()))).append(" = ")
                        .append(scalar(option.elementType(), entry.getValue(), false, null));
                first = false;
            }
            return b.append(first ? "}" : " }").toString();
        }
        if (option.type() == OptionType.OBJECT_LIST) {
            List<?> elements = (List<?>) value;
            if (elements.isEmpty()) {
                return "[]";
            }
            // one inline table per line (valid TOML 1.0), so the list never moves other sections
            StringBuilder b = new StringBuilder("[");
            for (Object element : elements) {
                b.append(nl).append("    {");
                boolean first = true;
                for (fr.heetstudio.confee.core.ObjectField field : option.objectFields()) {
                    b.append(first ? " " : ", ").append(TomlValues.key(field.key())).append(" = ")
                            .append(scalar(field.type(), ((Map<?, ?>) element).get(field.key()), false, null));
                    first = false;
                }
                b.append(" },");
            }
            return b.append(nl).append(']').toString();
        }
        return scalar(option.type(), value, option.colorAlpha(), option.elementType());
    }

    private static String scalar(OptionType type, Object value, boolean alpha, @Nullable OptionType elementType) {
        return switch (type) {
            case BOOLEAN -> TomlValues.bool((Boolean) value);
            case INT, LONG -> TomlValues.integer(((Number) value).longValue(), TomlStyle.IntegerStyle.DEFAULT);
            case FLOAT, DOUBLE -> TomlValues.floating((Number) value);
            case STRING, IDENTIFIER -> TomlValues.basic((String) value);
            case ENUM -> TomlValues.basic(((Enum<?>) value).name());
            case COLOR -> TomlValues.basic(color((Integer) value, alpha));
            case LIST -> {
                List<?> items = (List<?>) value;
                String[] parts = new String[items.size()];
                for (int i = 0; i < items.size(); i++) {
                    parts[i] = scalar(elementType, items.get(i), alpha, null);
                }
                yield TomlValues.array(parts, new TomlStyle.ArrayStyle(false, false, false, "", "", null, null), "\n");
            }
            default -> throw new IllegalArgumentException("not a scalar: " + type);
        };
    }

    /** {@code #RRGGBB}, or {@code #AARRGGBB} when the alpha channel is edited. */
    static String color(int argb, boolean alpha) {
        return alpha ? String.format("#%08X", argb) : String.format("#%06X", argb & 0xFFFFFF);
    }
}

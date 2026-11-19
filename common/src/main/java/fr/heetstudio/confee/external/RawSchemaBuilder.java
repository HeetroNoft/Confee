package fr.heetstudio.confee.external;

import fr.heetstudio.confee.core.Node;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.OptionType;
import fr.heetstudio.confee.core.Section;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedTable;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.ValueKind;
import fr.heetstudio.confee.ui.ValueText;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a parsed file of another mod into a config tree (spec 8.3): tables become sections, comments become
 * descriptions, types are inferred from the values. Complex values (dates, mixed lists, arrays of tables) are shown
 * read-only. "Default" values are the original values of the file.
 * <p>Internal: may change without notice.</p>
 */
final class RawSchemaBuilder {

    private final ParsedDocument document;
    @Nullable
    private final ParsedDocument original;
    private final Section root = Section.root(null);
    private final Map<List<String>, Section> sections = new HashMap<>();
    private final Map<List<String>, String> tableComments = new HashMap<>();
    private final Map<Option<?>, ParsedValue> bindings = new LinkedHashMap<>();

    private RawSchemaBuilder(ParsedDocument document, @Nullable ParsedDocument original) {
        this.document = document;
        this.original = original;
        sections.put(List.of(), root);
        for (ParsedTable table : document.tables()) {
            if (table.comment() != null) {
                tableComments.putIfAbsent(table.path(), table.comment());
            }
        }
    }

    record Result(Section root, Map<Option<?>, ParsedValue> bindings) {
    }

    /**
     * @param original the file before Confee first changed it (its values are the "original values"), or null to use
     *                 the current values
     */
    static Result build(ParsedDocument document, @Nullable ParsedDocument original) {
        RawSchemaBuilder builder = new RawSchemaBuilder(document, original);
        // values in file order create their sections on the way, so a sub-table comes after its parent's keys
        for (ParsedValue value : document.values()) {
            builder.add(value);
        }
        // tables without any value still show up
        for (ParsedTable table : document.tables()) {
            builder.section(table.path());
        }
        return new Result(builder.root, builder.bindings);
    }

    private Section section(List<String> path) {
        Section existing = sections.get(path);
        if (existing != null) {
            return existing;
        }
        Section parent = section(path.subList(0, path.size() - 1));
        String segment = path.get(path.size() - 1);
        String display = segment.startsWith("[") && segment.endsWith("]")
                ? "#" + (Integer.parseInt(segment.substring(1, segment.length() - 1)) + 1)
                : ValueText.humanize(segment);
        Section section = new Section(uniqueKey(parent, segment), display, tableComments.get(path));
        parent.add(section);
        sections.put(List.copyOf(path), section);
        return section;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void add(ParsedValue value) {
        List<String> path = value.path();
        Section parent = section(path.subList(0, path.size() - 1));
        String key = uniqueKey(parent, value.key());
        Option.Builder builder;
        Object current = value.value();
        Object originalValue = originalValue(value);
        boolean editable = value.editable();
        switch (value.kind()) {
            case BOOLEAN -> builder = Option.builder(key, OptionType.BOOLEAN);
            case INTEGER -> builder = Option.builder(key, OptionType.LONG);
            case FLOAT -> builder = Option.builder(key, OptionType.DOUBLE);
            case STRING -> builder = Option.builder(key, OptionType.STRING);
            // files of other mods: their mod decides whether duplicates are allowed, so they are kept
            case LIST -> builder = Option.builder(key, OptionType.LIST).elementType(elementType(value.elementKind()))
                    .allowDuplicates(true);
            default -> {
                builder = Option.builder(key, OptionType.STRING);
                editable = false;
            }
        }
        if (!editable) {
            // read-only: show the text as written in the file (spec 8.3: complex values)
            builder = Option.builder(key, OptionType.STRING);
            current = rawText(value);
            originalValue = current;
        }
        if (editable && (value.min() != null || value.max() != null)
                && (value.kind() == ValueKind.INTEGER || value.kind() == ValueKind.FLOAT)) {
            // Forge legacy "# Min: / # Max:" (spec 8.3)
            builder.range(value.min(), value.max());
        }
        builder.displayName(ValueText.humanize(value.key()))
                .comment(value.comment())
                .readOnly(!editable)
                .defaultValue(originalValue)
                .store(fr.heetstudio.confee.core.ValueStore.memory(current));
        Option<?> option;
        try {
            option = builder.build();
        } catch (IllegalStateException e) {
            // original value of another type (file edited by hand since the backup): no original value
            option = builder.defaultValue(null).build();
        }
        parent.add(option);
        if (editable) {
            bindings.put(option, value);
        }
    }

    /** The value at the same path in the original file, when it has the same kind. */
    @Nullable
    private Object originalValue(ParsedValue value) {
        if (original == null) {
            return value.value();
        }
        return original.find(value.path())
                .filter(o -> o.kind() == value.kind() && (o.kind() != ValueKind.LIST || o.elementKind() == null
                        || value.elementKind() == null || o.elementKind() == value.elementKind()))
                .map(ParsedValue::value)
                .orElse(null);
    }

    /** Empty lists have no known element type: edited as text (the most common case). */
    private static OptionType elementType(@Nullable ValueKind kind) {
        if (kind == null) {
            return OptionType.STRING;
        }
        return switch (kind) {
            case BOOLEAN -> OptionType.BOOLEAN;
            case INTEGER -> OptionType.LONG;
            case FLOAT -> OptionType.DOUBLE;
            default -> OptionType.STRING;
        };
    }

    private String rawText(ParsedValue value) {
        String text = document.source().text().substring(value.start(), value.end());
        return text.replaceAll("\\s+", " ").trim();
    }

    /** Model keys cannot contain '.', and must be unique in their section. */
    private static String uniqueKey(Section parent, String segment) {
        String base = segment.isEmpty() ? "_" : segment.replace('.', '_');
        String key = base;
        int n = 2;
        while (parent.find(key).isPresent()) {
            key = base + "_" + n++;
        }
        return key;
    }

    /** Whether a node is a value the player can change. */
    static boolean isEditable(Node node) {
        return node instanceof Option<?> option && !option.readOnly();
    }
}

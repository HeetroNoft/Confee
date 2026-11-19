package fr.heetstudio.confee.external;

import fr.heetstudio.confee.core.ListActions;
import fr.heetstudio.confee.core.Node;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.OptionType;
import fr.heetstudio.confee.core.Section;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedList;
import fr.heetstudio.confee.format.ParsedTable;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.ValueKind;
import fr.heetstudio.confee.ui.ValueText;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Turns a parsed file of another mod into a config tree (spec 8.3): tables become sections, comments become
 * descriptions, types are inferred from the values. Lists of objects, lists of lists and mixed lists become a section
 * whose children are their elements, one sub-section each (spec 8.2 bis). Other complex values (dates, null...) are
 * shown read-only. "Default" values are the original values of the file.
 * <p>Internal: may change without notice.</p>
 */
final class RawSchemaBuilder {

    /** The file being added (a config can be several files: HOCON includes). */
    private ParsedDocument document;
    @Nullable
    private ParsedDocument original;
    private int fileIndex;
    private java.util.Set<List<String>> emptyLists = java.util.Set.of();
    private final Section root = Section.root(null);
    private final Map<List<String>, Section> sections = new HashMap<>();
    private final Map<List<String>, String> tableComments = new HashMap<>();
    private final Map<Option<?>, Binding> bindings = new LinkedHashMap<>();

    private RawSchemaBuilder(List<ParsedDocument> documents) {
        this.document = documents.get(0);
        sections.put(List.of(), root);
        for (ParsedDocument doc : documents) {
            for (List<ParsedTable> list : List.of(doc.tables(), doc.nestedTables())) {
                for (ParsedTable table : list) {
                    if (table.comment() != null) {
                        tableComments.putIfAbsent(table.path(), table.comment());
                    }
                }
            }
        }
    }

    /**
     * Where an editable option is written.
     *
     * @param file  index of its file in the list given to {@link #build}
     * @param value its value in that file
     */
    record Binding(int file, ParsedValue value) {
    }

    record Result(Section root, Map<Option<?>, Binding> bindings) {
    }

    /**
     * @param documents the config file, then the files it includes (HOCON)
     * @param originals for each file, the file before Confee first changed it (its values are the "original values"),
     *                  or null to use the current values
     */
    static Result build(List<ParsedDocument> documents, List<ParsedDocument> originals,
                        java.util.Set<List<String>> emptyLists, Function<List<String>, ListActions> listActions) {
        RawSchemaBuilder builder = new RawSchemaBuilder(documents);
        builder.emptyLists = emptyLists;
        for (int f = 0; f < documents.size(); f++) {
            ParsedDocument document = documents.get(f);
            builder.document = document;
            builder.original = f < originals.size() ? originals.get(f) : null;
            builder.fileIndex = f;
            List<ParsedValue> all = new ArrayList<>();
            for (ParsedValue value : document.values()) {
                // a list edited element by element replaces the read-only text of the whole list
                if (value.kind() != ValueKind.COMPLEX || document.list(value.path()).isEmpty()) {
                    all.add(value);
                }
            }
            all.addAll(document.nestedValues());
            all.sort(Comparator.comparingInt(ParsedValue::start));
            // values in file order create their sections on the way, so a sub-table comes after its parent's keys
            for (ParsedValue value : all) {
                builder.add(value);
            }
        }
        // tables without any value still show up
        for (ParsedDocument document : documents) {
            for (List<ParsedTable> tables : List.of(document.tables(), document.nestedTables())) {
                for (ParsedTable table : tables) {
                    builder.section(table.path());
                }
            }
        }
        for (ParsedDocument document : documents) {
            for (ParsedList list : document.lists()) {
                Section section = builder.section(list.path());
                section.setListActions(listActions.apply(list.path()));
                builder.nameElements(section);
            }
        }
        // emptied lists of objects / lists: only "+ Add" (an element of the original file is the model)
        for (List<String> path : emptyLists) {
            builder.section(path).setListActions(listActions.apply(path));
        }
        return new Result(builder.root, builder.bindings);
    }

    /** Keys whose text names an element best ("#1 · stone"). */
    private static final List<String> NAME_KEYS = List.of("name", "id", "key", "type", "item", "block", "entity", "mob");

    /** Names each element of a list after its content: "#2 · minecraft:stone". */
    private void nameElements(Section list) {
        for (Node child : list.children()) {
            if (!(child instanceof Section element) || ParsedList.indexOf(element.key()) < 0) {
                continue;
            }
            String label = null;
            int best = Integer.MAX_VALUE;
            for (Node node : element.children()) {
                if (node instanceof Option<?> option && option.get() instanceof String text && !text.isBlank()) {
                    int rank = NAME_KEYS.indexOf(option.key().toLowerCase(Locale.ROOT));
                    rank = rank < 0 ? NAME_KEYS.size() : rank;
                    if (rank < best) {
                        best = rank;
                        label = text;
                    }
                }
            }
            if (label == null && element.children().size() == 1 && element.children().get(0) instanceof Option<?> only
                    && only.get() instanceof List<?> items && !items.isEmpty()) {
                // an element that is a list: its first values
                label = items.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
            }
            if (label == null && element.children().size() == 1 && element.children().get(0) instanceof Option<?> only
                    && only.get() != null && !(only.get() instanceof List<?>)) {
                label = String.valueOf(only.get());
            }
            if (label != null) {
                String shown = label.length() > 40 ? label.substring(0, 39) + "…" : label;
                element.setDisplayName(element.displayName() + " · " + shown);
            }
        }
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
        if (emptyLists.contains(path)) {
            // shown as a list section with "+ Add", see build()
            section(path);
            return;
        }
        Section parent = section(path.subList(0, path.size() - 1));
        String key = uniqueKey(parent, value.key());
        String displayName = ValueText.humanize(value.key());
        int index = ParsedList.indexOf(value.key());
        if (index >= 0 && document.list(path.subList(0, path.size() - 1)).isPresent()) {
            // a scalar or a list element of a list: in its own sub-section like the other elements
            parent = section(path);
            key = "value";
            displayName = "#" + (index + 1);
        }
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
            // a text prepared by the parser (a HOCON substitution and what it resolves to), else the file's text
            current = value.kind() == ValueKind.COMPLEX && value.value() instanceof String text && !text.isEmpty()
                    ? text : rawText(value);
            originalValue = current;
        }
        if (editable && (value.min() != null || value.max() != null)
                && (value.kind() == ValueKind.INTEGER || value.kind() == ValueKind.FLOAT)) {
            // Forge legacy "# Min: / # Max:" (spec 8.3)
            builder.range(value.min(), value.max());
        }
        builder.displayName(displayName)
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
            bindings.put(option, new Binding(fileIndex, value));
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

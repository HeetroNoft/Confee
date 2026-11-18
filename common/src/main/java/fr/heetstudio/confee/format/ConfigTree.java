package fr.heetstudio.confee.format;

import fr.heetstudio.confee.core.Node;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.OptionType;
import fr.heetstudio.confee.core.Section;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/**
 * What a Confee config file contains, in order (spec 7.3): the schema's options and sections, plus the unknown keys of
 * the previous file kept as written (spec 5.3). Shared by the nested formats (JSON, JSON5, YAML).
 * <p>Internal: may change without notice.</p>
 */
public final class ConfigTree {

    private ConfigTree() {
    }

    public sealed interface Item permits Leaf, Raw, Group {
    }

    /** An option of the schema. */
    public record Leaf(Option<?> option) implements Item {
    }

    /** An unknown entry of the previous file, as written there ({@code key: value}). */
    public record Raw(String text, @Nullable String comment) implements Item {
    }

    /** A section (or an object of the previous file that only holds unknown keys). */
    public static final class Group implements Item {
        public final String key;
        @Nullable
        public final String comment;
        public final List<Item> items = new ArrayList<>();

        Group(String key, @Nullable String comment) {
            this.key = key;
            this.comment = comment;
        }

        Group child(String childKey) {
            for (Item item : items) {
                if (item instanceof Group group && group.key.equals(childKey)) {
                    return group;
                }
            }
            Group group = new Group(childKey, null);
            items.add(group);
            return group;
        }
    }

    /**
     * @param multiLineRaw whether unknown entries written on several lines can be kept (false for YAML, where their
     *                     indentation would break)
     */
    public static Group build(Section root, @Nullable ParsedDocument previous, boolean multiLineRaw) {
        Group top = new Group("", root.comment());
        fill(top, root);
        if (previous == null) {
            return top;
        }
        Set<String> options = new HashSet<>();
        root.forEachOption(o -> options.add(o.path()));
        String text = previous.source().text();
        for (ParsedValue value : previous.values()) {
            if (value.entryStart() < 0 || value.path().stream().anyMatch(s -> s.startsWith("["))) {
                continue;
            }
            String path = value.joinedPath();
            if (options.contains(path) || collides(path, options)) {
                continue;
            }
            String raw = text.substring(value.entryStart(), value.end());
            if (!multiLineRaw && raw.indexOf('\n') >= 0) {
                continue;
            }
            Group group = top;
            for (String segment : value.path().subList(0, value.path().size() - 1)) {
                group = group.child(segment);
            }
            group.items.add(new Raw(raw, value.comment()));
        }
        return top;
    }

    private static void fill(Group group, Section section) {
        for (Node child : section.children()) {
            if (child instanceof Option<?> option && option.get() != null) {
                group.items.add(new Leaf(option));
            } else if (child instanceof Section sub) {
                Group g = new Group(sub.key(), sub.comment());
                group.items.add(g);
                fill(g, sub);
            }
        }
    }

    private static boolean collides(String path, Set<String> known) {
        for (String option : known) {
            if (option.startsWith(path + ".") || path.startsWith(option + ".")) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ shared helpers for writers

    /** Range / allowed values hint (spec 7.3), or null. */
    @Nullable
    public static String hint(Option<?> option) {
        Number min = option.min();
        Number max = option.max();
        if (min != null && max != null) {
            return "Range: " + number(min, option.type()) + " ~ " + number(max, option.type());
        }
        if (min != null) {
            return "Range: >= " + number(min, option.type());
        }
        if (max != null) {
            return "Range: <= " + number(max, option.type());
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

    /** Comment + hint of an option, or null. */
    @Nullable
    public static String commentOf(Option<?> option) {
        String hint = hint(option);
        if (option.comment() == null) {
            return hint;
        }
        return hint == null ? option.comment() : option.comment() + "\n" + hint;
    }

    /**
     * Value ready for a writer: enums become their name, colors {@code #RRGGBB} text, at any depth (lists, maps,
     * lists of objects keep their order).
     */
    public static Object portable(Option<?> option, Object value) {
        if (option.type() == OptionType.COLOR) {
            return option.colorAlpha() ? String.format("#%08X", (Integer) value)
                    : String.format("#%06X", ((Integer) value) & 0xFFFFFF);
        }
        return deep(value);
    }

    private static Object deep(Object value) {
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object element : list) {
                out.add(deep(element));
            }
            return out;
        }
        if (value instanceof java.util.Map<?, ?> map) {
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), deep(v)));
            return out;
        }
        return value;
    }
}

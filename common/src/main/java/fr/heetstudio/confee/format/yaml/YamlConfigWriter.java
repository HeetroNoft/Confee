package fr.heetstudio.confee.format.yaml;

import fr.heetstudio.confee.core.ConfigSchema;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.format.CommentMarker;
import fr.heetstudio.confee.format.ConfigTree;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.TextScalars;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Writes a Confee config as YAML (spec 7.3): {@code #} comments with the range / allowed values hints, 2-space
 * indentation, block lists, declaration order. Unknown single-line keys of the previous file are kept (spec 5.3).
 * <p>Internal: may change without notice.</p>
 */
public final class YamlConfigWriter {

    private static final String INDENT = "  ";

    private final StringBuilder out = new StringBuilder();
    private final String nl;

    private YamlConfigWriter(String lineSeparator) {
        this.nl = lineSeparator;
    }

    public static String write(ConfigSchema schema, String lineSeparator, @Nullable ParsedDocument previous) {
        ConfigTree.Group tree = ConfigTree.build(schema.root(), previous, false);
        YamlConfigWriter writer = new YamlConfigWriter(lineSeparator);
        if (tree.comment != null) {
            writer.comment(tree.comment, "");
            writer.out.append(lineSeparator);
        }
        writer.items(tree, "");
        return writer.out.toString();
    }

    private void items(ConfigTree.Group group, String indent) {
        boolean first = true;
        for (ConfigTree.Item item : group.items) {
            if (!first && indent.isEmpty()) {
                out.append(nl);
            }
            first = false;
            if (item instanceof ConfigTree.Leaf leaf) {
                Option<?> option = leaf.option();
                comment(ConfigTree.commentOf(option), indent);
                out.append(indent).append(key(option.key())).append(':');
                value(ConfigTree.portable(option, option.get()), indent);
            } else if (item instanceof ConfigTree.Raw raw) {
                comment(raw.comment(), indent, true);
                out.append(indent).append(raw.text()).append(nl);
            } else if (item instanceof ConfigTree.Group sub) {
                comment(sub.comment, indent);
                out.append(indent).append(key(sub.key)).append(':');
                if (sub.items.isEmpty()) {
                    out.append(" {}").append(nl);
                } else {
                    out.append(nl);
                    items(sub, indent + INDENT);
                }
            }
        }
    }

    private void value(Object value, String indent) {
        if (value instanceof java.util.Map<?, ?> map) {
            if (map.isEmpty()) {
                out.append(" {}").append(nl);
                return;
            }
            out.append(nl);
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                out.append(indent).append(INDENT).append(key(String.valueOf(entry.getKey()))).append(": ")
                        .append(scalar(entry.getValue())).append(nl);
            }
            return;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                out.append(" []").append(nl);
                return;
            }
            out.append(nl);
            for (Object element : list) {
                if (element instanceof java.util.Map<?, ?> object) {
                    // list of objects: "- key: value" then the other keys aligned under the first one
                    boolean first = true;
                    for (java.util.Map.Entry<?, ?> entry : object.entrySet()) {
                        out.append(indent).append(INDENT).append(first ? "- " : "  ")
                                .append(key(String.valueOf(entry.getKey()))).append(": ").append(scalar(entry.getValue()))
                                .append(nl);
                        first = false;
                    }
                } else {
                    out.append(indent).append(INDENT).append("- ").append(scalar(element)).append(nl);
                }
            }
            return;
        }
        out.append(' ').append(scalar(value)).append(nl);
    }

    private static String scalar(Object value) {
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (value instanceof Long || value instanceof Integer) {
            return Long.toString(((Number) value).longValue());
        }
        if (value instanceof Double || value instanceof Float) {
            return YamlValues.floating((Number) value);
        }
        return YamlValues.string(String.valueOf(value), YamlParser.Quote.PLAIN);
    }

    private static String key(String key) {
        return YamlValues.safePlain(key) && !key.contains(":") ? key : YamlValues.string(key, YamlParser.Quote.DOUBLE);
    }

    private void comment(@Nullable String text, String indent) {
        comment(text, indent, false);
    }

    /** @param carried a comment of an unknown key, written again as it was (spec 5.3) */
    private void comment(@Nullable String text, String indent, boolean carried) {
        if (text == null) {
            return;
        }
        for (String line : text.split("\r?\n", -1)) {
            out.append(indent).append(carried ? CommentMarker.carried("#", line) : CommentMarker.generated("#", line))
                    .append(nl);
        }
    }

    static String bool(boolean value) {
        return TextScalars.bool(value, TextScalars.BoolCase.LOWER);
    }
}

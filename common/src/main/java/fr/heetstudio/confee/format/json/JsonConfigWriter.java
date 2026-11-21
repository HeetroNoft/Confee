package fr.heetstudio.confee.format.json;

import fr.heetstudio.confee.core.ConfigSchema;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.format.CommentMarker;
import fr.heetstudio.confee.format.ConfigTree;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.TextScalars;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Writes a Confee config as JSON (no comments: JSON has none) or JSON5 ({@code //} comments with the range / allowed
 * values hints), 2-space indentation, declaration order. Unknown keys of the previous file are kept (spec 5.3).
 * <p>Internal: may change without notice.</p>
 */
public final class JsonConfigWriter {

    private static final String INDENT = "  ";

    private final StringBuilder out = new StringBuilder();
    private final String nl;
    private final boolean json5;
    private final boolean writeDefaults;

    private JsonConfigWriter(String lineSeparator, boolean json5, boolean writeDefaults) {
        this.nl = lineSeparator;
        this.json5 = json5;
        this.writeDefaults = writeDefaults;
    }

    public static String write(ConfigSchema schema, String lineSeparator, @Nullable ParsedDocument previous, boolean json5) {
        ConfigTree.Group tree = ConfigTree.build(schema.root(), previous, true);
        JsonConfigWriter writer = new JsonConfigWriter(lineSeparator, json5, schema.writeDefaults());
        if (json5) {
            writer.comment(tree.comment, "");
        }
        writer.group(tree, "");
        writer.out.append(lineSeparator);
        return writer.out.toString();
    }

    private void group(ConfigTree.Group group, String indent) {
        if (group.items.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{').append(nl);
        String inner = indent + INDENT;
        for (int i = 0; i < group.items.size(); i++) {
            ConfigTree.Item item = group.items.get(i);
            if (item instanceof ConfigTree.Leaf leaf) {
                comment(ConfigTree.commentOf(leaf.option(), writeDefaults), inner);
                out.append(inner).append(JsonValues.string(leaf.option().key(), '"')).append(": ")
                        .append(value(leaf.option()));
            } else if (item instanceof ConfigTree.Raw raw) {
                comment(raw.comment(), inner, true);
                out.append(inner).append(raw.text());
            } else if (item instanceof ConfigTree.Group sub) {
                comment(sub.comment, inner);
                out.append(inner).append(JsonValues.string(sub.key, '"')).append(": ");
                group(sub, inner);
            }
            if (i < group.items.size() - 1) {
                out.append(',');
            }
            out.append(nl);
        }
        out.append(indent).append('}');
    }

    private void comment(@Nullable String text, String indent) {
        comment(text, indent, false);
    }

    /** @param carried a comment of an unknown key, written again as it was (spec 5.3) */
    private void comment(@Nullable String text, String indent, boolean carried) {
        if (!json5 || text == null) {
            return;
        }
        for (String line : text.split("\r?\n", -1)) {
            out.append(indent).append(carried ? CommentMarker.carried("//", line) : CommentMarker.generated("//", line))
                    .append(nl);
        }
    }

    private String value(Option<?> option) {
        return scalar(ConfigTree.portable(option, option.get()));
    }

    private String scalar(Object value) {
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (value instanceof Long || value instanceof Integer) {
            return Long.toString(((Number) value).longValue());
        }
        if (value instanceof Double || value instanceof Float) {
            return TextScalars.floating((Number) value);
        }
        if (value instanceof java.util.Map<?, ?> map) {
            StringBuilder b = new StringBuilder("{");
            boolean first = true;
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                b.append(first ? "" : ", ").append(JsonValues.string(String.valueOf(entry.getKey()), '"')).append(": ")
                        .append(scalar(entry.getValue()));
                first = false;
            }
            return b.append('}').toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder b = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    b.append(", ");
                }
                b.append(scalar(list.get(i)));
            }
            return b.append(']').toString();
        }
        return JsonValues.string(String.valueOf(value), '"');
    }
}

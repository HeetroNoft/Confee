package fr.heetstudio.confee.format.properties;

import fr.heetstudio.confee.core.ConfigSchema;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.format.CommentMarker;
import fr.heetstudio.confee.format.ConfigTree;
import fr.heetstudio.confee.format.FormatAdapter;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.TextScalars;
import fr.heetstudio.confee.format.ValueWriter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Java {@code .properties}. A Confee config in this format uses flat dotted keys ({@code dash.distance=4.0}) and
 * comma-separated lists ({@code a,b}; a comma inside an element is written {@code \,}).
 * <p>Internal: may change without notice.</p>
 */
public final class PropertiesAdapter implements FormatAdapter {

    @Override
    public String name() {
        return "Properties";
    }

    @Override
    public ParsedDocument parse(SourceText source) {
        return PropertiesParser.parse(source, true);
    }

    @Override
    public ParsedDocument parseForSchema(SourceText source) {
        return PropertiesParser.parse(source, false);
    }

    @Override
    public ValueWriter values() {
        return PropertiesValues.INSTANCE;
    }

    @Override
    public String commentPrefix() {
        return "#";
    }

    @Override
    public ConfigWriter configWriter() {
        return PropertiesAdapter::writeConfig;
    }

    /** Spec 7.3 for properties: comments and hints above each key, sections as dotted prefixes. */
    static String writeConfig(ConfigSchema schema, String nl, @Nullable ParsedDocument previous) {
        StringBuilder out = new StringBuilder();
        if (schema.root().comment() != null) {
            comment(out, schema.root().comment(), nl);
            out.append(nl);
        }
        Set<String> known = new HashSet<>();
        List<Option<?>> options = new ArrayList<>();
        schema.root().forEachOption(o -> {
            known.add(o.path());
            options.add(o);
        });
        boolean first = true;
        for (Option<?> option : options) {
            if (option.get() == null) {
                continue;
            }
            if (!first) {
                out.append(nl);
            }
            first = false;
            String text = ConfigTree.commentOf(option);
            if (text != null) {
                comment(out, text, nl);
            }
            Object value = ConfigTree.portable(option, option.get());
            if (option.type() == fr.heetstudio.confee.core.OptionType.MAP) {
                // one flat key per entry: path.key=value
                for (java.util.Map.Entry<?, ?> entry : ((java.util.Map<?, ?>) value).entrySet()) {
                    out.append(PropertiesValues.escapeKey(option.path() + "." + entry.getKey())).append('=')
                            .append(PropertiesValues.escape(text(entry.getValue()))).append(nl);
                }
                continue;
            }
            if (option.type() == fr.heetstudio.confee.core.OptionType.OBJECT_LIST) {
                List<?> elements = (List<?>) value;
                if (elements.isEmpty()) {
                    // marks an empty list, so the defaults do not come back
                    out.append(PropertiesValues.escapeKey(option.path())).append('=').append(nl);
                }
                for (int i = 0; i < elements.size(); i++) {
                    for (java.util.Map.Entry<?, ?> entry : ((java.util.Map<?, ?>) elements.get(i)).entrySet()) {
                        out.append(PropertiesValues.escapeKey(option.path() + "." + i + "." + entry.getKey())).append('=')
                                .append(PropertiesValues.escape(text(entry.getValue()))).append(nl);
                    }
                }
                continue;
            }
            out.append(PropertiesValues.escapeKey(option.path())).append('=')
                    .append(PropertiesValues.escape(text(value))).append(nl);
        }
        if (previous != null) {
            // unknown keys of the previous file, as written (spec 5.3)
            String source = previous.source().text();
            for (ParsedValue value : previous.values()) {
                if (!value.editable() || known.contains(value.key())
                        || known.stream().anyMatch(k -> value.key().startsWith(k + "."))) {
                    continue;
                }
                out.append(nl);
                if (value.comment() != null) {
                    for (String line : value.comment().split("\r?\n", -1)) {
                        out.append(CommentMarker.carried("#", line)).append(nl);
                    }
                }
                out.append(source, value.entryStart(), value.end()).append(nl);
            }
        }
        return out.toString();
    }

    /** Value as text; lists are comma-separated with {@code \,} for commas inside elements. */
    static String text(Object value) {
        if (value instanceof List<?> list) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    b.append(',');
                }
                b.append(text(list.get(i)).replace("\\", "\\\\").replace(",", "\\,"));
            }
            return b.toString();
        }
        if (value instanceof Double || value instanceof Float) {
            return TextScalars.floating((Number) value);
        }
        return String.valueOf(value);
    }

    /** Splits a comma-separated list written by {@link #text}. */
    public static List<String> splitList(String text) {
        List<String> out = new ArrayList<>();
        if (text.isEmpty()) {
            return out;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                current.append(text.charAt(++i));
            } else if (c == ',') {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    private static void comment(StringBuilder out, String text, String nl) {
        for (String line : text.split("\r?\n", -1)) {
            out.append(CommentMarker.generated("#", line)).append(nl);
        }
    }
}

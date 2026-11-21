package fr.heetstudio.confee.format;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.Section;
import fr.heetstudio.confee.core.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the values of a parsed file to a config tree, with validation and correction (spec 5.3).
 * Never throws because of the file content.
 * <p>Internal: may change without notice.</p>
 */
public final class SchemaBinder {

    private SchemaBinder() {
    }

    /**
     * @param missing   options absent from the file (they keep their current value)
     * @param corrected options whose value was clamped
     * @param replaced  options whose value was unreadable and replaced by the default
     */
    public record Report(List<String> missing, List<String> corrected, List<String> replaced) {

        /** The file should be written again (to add missing keys or fix values). */
        public boolean needsRewrite() {
            return !missing.isEmpty() || !corrected.isEmpty() || !replaced.isEmpty();
        }
    }

    /**
     * @param fileName used in log messages only
     */
    public static Report load(Section root, ParsedDocument document, String fileName) {
        List<String> missing = new ArrayList<>();
        List<String> corrected = new ArrayList<>();
        List<String> replaced = new ArrayList<>();
        root.forEachOption(option -> {
            List<String> path = List.of(option.path().split("\\."));
            // nested formats split the path; flat ones (properties) keep "cat.key" as one key
            ParsedValue parsed = document.find(path).or(() -> document.find(List.of(option.path()))).orElse(null);
            Object raw;
            if (option.type() == fr.heetstudio.confee.core.OptionType.MAP) {
                raw = gatherMap(option, path, document);
            } else if (option.type() == fr.heetstudio.confee.core.OptionType.OBJECT_LIST
                    && (parsed == null || parsed.value() instanceof String)) {
                raw = gatherObjects(option, document);
            } else {
                raw = parsed == null ? null : rawValue(option, parsed, document);
            }
            if (raw == null) {
                missing.add(option.path());
                return;
            }
            if (option.type() == fr.heetstudio.confee.core.OptionType.OBJECT_LIST && document.looselyTyped()) {
                raw = textFields(option, raw);
            }
            apply(option, raw, fileName, corrected, replaced);
        });
        return new Report(List.copyOf(missing), List.copyOf(corrected), List.copyOf(replaced));
    }

    /**
     * Entries of a map option: a sub-table / object ({@code path.key}), or flat keys ({@code "path.key"} in
     * properties). Null when the file has no trace of the map.
     */
    @org.jetbrains.annotations.Nullable
    private static Object gatherMap(fr.heetstudio.confee.core.Option<?> option, List<String> path, ParsedDocument document) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        boolean present = document.tables().stream().anyMatch(t -> t.path().equals(path));
        String flatPrefix = option.path() + ".";
        for (ParsedValue value : document.values()) {
            List<String> p = value.path();
            if (p.size() == path.size() + 1 && p.subList(0, path.size()).equals(path)) {
                map.put(p.get(p.size() - 1), elementValue(option, value, document));
                present = true;
            } else if (p.size() == 1 && p.get(0).startsWith(flatPrefix) && p.get(0).length() > flatPrefix.length()) {
                map.put(p.get(0).substring(flatPrefix.length()), elementValue(option, value, document));
                present = true;
            } else if (p.equals(path) && value.value() instanceof java.util.Map<?, ?> m) {
                return m;
            }
        }
        return present ? map : null;
    }

    private static Object elementValue(fr.heetstudio.confee.core.Option<?> option, ParsedValue value, ParsedDocument document) {
        boolean text = option.elementType() == fr.heetstudio.confee.core.OptionType.STRING;
        if (text && !(value.value() instanceof String) && document.looselyTyped() && value.kind().isScalar()) {
            return document.rawText(value);
        }
        return value.value();
    }

    /** Properties: {@code path.<index>.<field>=value} lines, or {@code path=} for an empty list. */
    @org.jetbrains.annotations.Nullable
    private static Object gatherObjects(fr.heetstudio.confee.core.Option<?> option, ParsedDocument document) {
        java.util.TreeMap<Integer, java.util.Map<String, Object>> elements = new java.util.TreeMap<>();
        boolean present = false;
        String prefix = option.path() + ".";
        for (ParsedValue value : document.values()) {
            String key = value.path().size() == 1 ? value.path().get(0) : value.joinedPath();
            if (key.equals(option.path())) {
                present = true;
                continue;
            }
            if (!key.startsWith(prefix)) {
                continue;
            }
            String rest = key.substring(prefix.length());
            int dot = rest.indexOf('.');
            if (dot <= 0) {
                continue;
            }
            try {
                int index = Integer.parseInt(rest.substring(0, dot));
                elements.computeIfAbsent(index, k -> new java.util.LinkedHashMap<>()).put(rest.substring(dot + 1), value.value());
                present = true;
            } catch (NumberFormatException ignored) {
                // not an element line
            }
        }
        return present ? new ArrayList<>(elements.values()) : null;
    }

    /** In loosely typed formats, a text field written {@code 123} is meant as text. */
    private static Object textFields(fr.heetstudio.confee.core.Option<?> option, Object raw) {
        if (!(raw instanceof List<?> list)) {
            return raw;
        }
        List<Object> out = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof java.util.Map<?, ?> map)) {
                out.add(element);
                continue;
            }
            java.util.Map<String, Object> copy = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            for (fr.heetstudio.confee.core.ObjectField field : option.objectFields()) {
                Object v = copy.get(field.key());
                if (field.type() == fr.heetstudio.confee.core.OptionType.STRING && v != null && !(v instanceof String)) {
                    copy.put(field.key(), String.valueOf(v));
                }
            }
            out.add(copy);
        }
        return out;
    }

    /**
     * Adapts what the file holds to the option: lists of a properties file are comma-separated text; in YAML a plain
     * {@code 123} for a text option is meant as text.
     */
    private static Object rawValue(fr.heetstudio.confee.core.Option<?> option, ParsedValue parsed, ParsedDocument document) {
        Object value = parsed.value();
        if (option.type() == fr.heetstudio.confee.core.OptionType.LIST && value instanceof String text) {
            return fr.heetstudio.confee.format.properties.PropertiesAdapter.splitList(text);
        }
        boolean textOption = option.type() == fr.heetstudio.confee.core.OptionType.STRING
                || option.type() == fr.heetstudio.confee.core.OptionType.IDENTIFIER;
        if (textOption && !(value instanceof String) && document.looselyTyped() && parsed.kind().isScalar()) {
            return document.rawText(parsed);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static <T> void apply(Option<T> option, Object raw, String fileName,
                                  List<String> corrected, List<String> replaced) {
        Validated<T> result = option.validate(raw);
        switch (result.status()) {
            case CORRECTED -> {
                corrected.add(option.path());
                ConfeeLog.warn("{}: '{}' {}", fileName, option.path(), result.message());
            }
            case REPLACED_BY_DEFAULT -> {
                replaced.add(option.path());
                ConfeeLog.warn("{}: '{}' {}, using the default value", fileName, option.path(), result.message());
            }
            case KEPT -> ConfeeLog.warn("{}: '{}' {}; kept as written, the mod skips it", fileName, option.path(), result.message());
            default -> {
            }
        }
        T value = result.value();
        if (value != null) {
            option.apply(value);
        }
    }
}

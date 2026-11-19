package fr.heetstudio.confee.core;

import fr.heetstudio.confee.ConfeeLog;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Development environment only: after a config is registered, logs what Confee understood from the declaration
 * (file, options, widget chosen for each one, constraints) and what is missing (translations). It reads only the
 * schema and the mod's language file, so it is safe on a dedicated server.
 * <p>Internal: may change without notice.</p>
 */
public final class DevReport {

    private static final int MAX_LISTED = 12;
    private static final java.util.regex.Pattern JSON_KEY = java.util.regex.Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:");

    private DevReport() {
    }

    /** Logs the report; never throws (a broken report must not stop the mod). */
    public static void log(ConfigSchema schema, @Nullable ClassLoader loader) {
        try {
            ConfeeLog.info("{}", build(schema, loader));
        } catch (RuntimeException e) {
            ConfeeLog.warn("Dev report of {} failed: {}", schema.id(), e.toString());
        }
    }

    static String build(ConfigSchema schema, @Nullable ClassLoader loader) {
        List<Option<?>> options = new ArrayList<>();
        schema.root().forEachOption(options::add);
        int categories = 0;
        for (Node child : schema.root().children()) {
            if (child instanceof Section) {
                categories++;
            }
        }
        StringBuilder out = new StringBuilder();
        out.append("Dev report for ").append(schema.id()).append(" (shown in development only)\n");
        out.append("  file: ").append(schema.filePath()).append(" (").append(schema.format()).append(", scope ")
                .append(schema.scope()).append(")\n");
        out.append("  ").append(options.size()).append(" option(s), ").append(categories).append(" section(s)\n");
        int width = 8;
        for (Option<?> option : options) {
            width = Math.max(width, option.path().length());
        }
        for (Option<?> option : options) {
            out.append("  - ").append(pad(option.path(), width)).append("  ").append(widget(option));
            String details = constraints(option);
            if (!details.isEmpty()) {
                out.append("  [").append(details).append(']');
            }
            out.append('\n');
        }
        translations(schema, options, loader, out);
        return out.toString().stripTrailing();
    }

    // ------------------------------------------------------------------ widgets (same choice as the config screen)

    static String widget(Option<?> option) {
        if (option.widgetFactory() != null) {
            return "your widget (@Widget)";
        }
        if (option.hidden()) {
            return "hidden (file only)";
        }
        return switch (option.type()) {
            case BOOLEAN -> "toggle";
            case ENUM -> {
                int count = option.allowedValues() != null ? option.allowedValues().size()
                        : option.enumClass().getEnumConstants().length;
                yield count >= 2 && count <= 4 && option.widgetHint() != WidgetHint.DROPDOWN
                        ? "segmented buttons (" + count + " values)" : "dropdown (" + count + " values)";
            }
            case LIST -> option.widgetHint() == WidgetHint.CHECKBOXES ? "check boxes"
                    : "list of " + lower(option.elementType()) + (option.registry() != null ? " IDs" : "");
            case MAP -> "key -> value editor (" + lower(option.elementType()) + " values)";
            case OBJECT_LIST -> "list of objects (" + option.objectFields().size() + " fields)";
            case STRING -> option.widgetHint() == WidgetHint.MULTILINE ? "text area"
                    : option.widgetHint() == WidgetHint.KEYBIND ? "key binding"
                    : option.registry() != null ? "ID field" : "text field";
            case IDENTIFIER -> "ID field";
            case COLOR -> "color field + picker";
            default -> option.widgetHint() == WidgetHint.SLIDER && option.min() != null && option.max() != null
                    ? "slider" : option.widgetHint() == WidgetHint.STEPPER ? "stepper (- / +)" : "number field";
        };
    }

    static String constraints(Option<?> option) {
        List<String> parts = new ArrayList<>();
        if (option.min() != null || option.max() != null) {
            parts.add("range " + bound(option.min()) + " ~ " + bound(option.max()));
        }
        if (option.elementMin() != null || option.elementMax() != null) {
            parts.add("each value " + bound(option.elementMin()) + " ~ " + bound(option.elementMax()));
        }
        if (option.step() != null) {
            parts.add("step " + option.step());
        }
        if (option.pattern() != null) {
            parts.add("pattern " + option.pattern().pattern());
        }
        if (option.elementPattern() != null) {
            parts.add("each value matches " + option.elementPattern().pattern());
        }
        if (option.maxLength() > 0) {
            parts.add("max " + option.maxLength() + " chars");
        }
        if (option.elementMaxLength() > 0) {
            parts.add("each value max " + option.elementMaxLength() + " chars");
        }
        if (option.type() == OptionType.LIST && option.widgetHint() != WidgetHint.CHECKBOXES) {
            if (option.minSize() > 0 || option.maxSize() != Integer.MAX_VALUE) {
                parts.add("size " + option.minSize() + " ~ " + (option.maxSize() == Integer.MAX_VALUE ? "any" : option.maxSize()));
            }
            parts.add(option.allowDuplicates() ? "duplicates allowed" : option.ignoreCase() ? "no duplicates, any case" : "no duplicates");
            if (option.sorted()) {
                parts.add("sorted");
            }
            if (option.reorderable()) {
                parts.add("reorderable");
            }
        }
        if (option.registry() != null && PseudoRegistries.isModList(option.registry())) {
            parts.add("installed mods (logos, mod picker)");
        } else if (option.registry() != null) {
            parts.add("registry " + option.registry() + (option.allowTags() ? " + #tags" : ""));
        }
        if (!option.filters().isEmpty()) {
            List<String> names = new ArrayList<>();
            option.filters().forEach(f -> names.add(f.name()));
            parts.add("only " + String.join(" or ", names));
        }
        if (Pickers.hasPicker(option.registry())) {
            String def = Pickers.defaultStyle(option, option.registry()).name().toLowerCase(Locale.ROOT);
            List<String> others = new ArrayList<>();
            for (fr.heetstudio.confee.api.PickerStyle s : Pickers.allowed(option)) {
                others.add(s.name().toLowerCase(Locale.ROOT));
            }
            parts.add("picker " + def + (others.size() > 1 ? " (also " + String.join(", ", others) + ")" : " only"));
        }
        if (option.unit() != null) {
            parts.add("unit " + option.unit());
        }
        if (option.enabledIf() != null) {
            parts.add("enabled if " + option.enabledIf());
        }
        if (option.visibleIf() != null) {
            parts.add("visible if " + option.visibleIf());
        }
        if (option.requiresRestart()) {
            parts.add("needs restart");
        }
        if (option.preview() != null) {
            parts.add("preview");
        }
        return String.join(", ", parts);
    }

    // ------------------------------------------------------------------ translations

    private static void translations(ConfigSchema schema, List<Option<?>> options, @Nullable ClassLoader loader,
                                     StringBuilder out) {
        String path = "assets/" + schema.modId() + "/lang/en_us.json";
        Set<String> keys = langKeys(path, loader);
        String prefix = "config." + schema.modId() + "." + (schema.name() == null ? "" : schema.name() + ".");
        if (keys == null) {
            out.append("  names: no ").append(path).append(", so names come from @Name or the field name")
                    .append(" (e.g. \"").append(options.isEmpty() ? "showHud" : options.get(0).key()).append("\" -> \"")
                    .append(options.isEmpty() ? "Show Hud" : humanize(options.get(0).key()))
                    .append("\"). To translate: \"").append(prefix).append("<option>\" in your lang files\n");
            return;
        }
        List<String> missing = new ArrayList<>();
        for (Option<?> option : options) {
            if (!option.hidden() && option.displayName() == null && !keys.contains(prefix + option.path())) {
                missing.add(prefix + option.path());
            }
        }
        if (missing.isEmpty()) {
            out.append("  names: every visible option is translated or has @Name\n");
            return;
        }
        out.append("  names: ").append(missing.size()).append(" option(s) without translation nor @Name in ")
                .append(path).append(" (the field name is shown instead):\n");
        for (int i = 0; i < Math.min(MAX_LISTED, missing.size()); i++) {
            out.append("      \"").append(missing.get(i)).append("\": \"...\",\n");
        }
        if (missing.size() > MAX_LISTED) {
            out.append("      ... and ").append(missing.size() - MAX_LISTED).append(" more\n");
        }
    }

    @Nullable
    private static Set<String> langKeys(String path, @Nullable ClassLoader loader) {
        List<ClassLoader> loaders = new ArrayList<>();
        if (loader != null) {
            loaders.add(loader);
        }
        if (Thread.currentThread().getContextClassLoader() != null) {
            loaders.add(Thread.currentThread().getContextClassLoader());
        }
        loaders.add(DevReport.class.getClassLoader());
        for (ClassLoader l : loaders) {
            try (InputStream in = l.getResourceAsStream(path)) {
                if (in == null) {
                    continue;
                }
                Set<String> keys = new HashSet<>();
                Matcher m = JSON_KEY.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                while (m.find()) {
                    keys.add(m.group(1));
                }
                return keys;
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ text helpers

    private static String humanize(String key) {
        return fr.heetstudio.confee.ui.ValueText.humanize(key); // same names as the screen
    }

    private static String lower(@Nullable OptionType type) {
        return type == null ? "?" : type.name().toLowerCase(Locale.ROOT);
    }

    private static String bound(@Nullable Number n) {
        return n == null ? "any" : n.toString();
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s : s + " ".repeat(width - s.length());
    }
}

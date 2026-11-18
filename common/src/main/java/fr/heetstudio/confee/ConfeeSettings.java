package fr.heetstudio.confee;

import fr.heetstudio.confee.api.ConfeeConfig;
import fr.heetstudio.confee.api.ConfigScope;
import fr.heetstudio.confee.api.ConfigValue;
import fr.heetstudio.confee.api.builder.CategoryBuilder;
import fr.heetstudio.confee.api.builder.ConfigBuilder;
import fr.heetstudio.confee.api.Confee;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Confee's own config, {@code config/confee.toml} (spec 12), declared with the builder API (dogfooding).
 * CLIENT scope: the universal editor is a client feature.
 * <p>Internal: may change without notice.</p>
 */
public final class ConfeeSettings {

    private static ConfigValue<Boolean> injectConfigButtons;
    private static ConfigValue<Boolean> backupExternalFiles;
    private static ConfigValue<List<String>> fileMappings;
    private static ConfigValue<List<String>> ignoredMods;

    private ConfeeSettings() {
    }

    static ConfeeConfig<Void> register() {
        ConfigBuilder b = Confee.builder(ConfeeMod.MOD_ID).scope(ConfigScope.CLIENT);
        CategoryBuilder general = b.category("general");
        injectConfigButtons = general.bool("injectConfigButtons", true)
                .comment("Add a Config button to mods that have none (universal editor)").requiresRestart().build();
        backupExternalFiles = general.bool("backupExternalFiles", true)
                .comment("Keep a copy of the original file before Confee changes it for the first time").build();
        CategoryBuilder external = b.category("external");
        fileMappings = external.list("fileMappings", String.class, List.of())
                .comment("Manual mod -> file mappings, one per entry: \"modid=path\" (path relative to config/)").build();
        ignoredMods = external.list("ignoredMods", String.class, List.of())
                .comment("Mods ignored by the universal editor").requiresRestart().build();
        return b.build();
    }

    public static boolean injectConfigButtons() {
        return injectConfigButtons == null || injectConfigButtons.get();
    }

    public static boolean backupExternalFiles() {
        return backupExternalFiles == null || backupExternalFiles.get();
    }

    /** {@code "modid=path"} entries grouped by mod. Malformed entries are skipped with a warning. */
    public static Map<String, List<String>> fileMappings() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (fileMappings == null) {
            return out;
        }
        for (String entry : fileMappings.get()) {
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                ConfeeLog.warn("confee.toml: ignoring file mapping '{}' (expected modid=path)", entry);
                continue;
            }
            out.computeIfAbsent(entry.substring(0, eq).trim(), k -> new ArrayList<>()).add(entry.substring(eq + 1).trim());
        }
        return out;
    }

    public static Set<String> ignoredMods() {
        return ignoredMods == null ? Set.of() : new LinkedHashSet<>(ignoredMods.get());
    }
}

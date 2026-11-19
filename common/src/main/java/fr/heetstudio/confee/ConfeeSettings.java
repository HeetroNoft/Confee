package fr.heetstudio.confee;

import fr.heetstudio.confee.api.ConfeeConfig;
import fr.heetstudio.confee.api.ConfigScope;
import fr.heetstudio.confee.api.ConfigValue;
import fr.heetstudio.confee.api.Theme;
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

    /**
     * Coffee palette of Confee's own screen only (other screens keep {@link Theme#DEFAULT}).
     * Coffee-brown accent (white text 5.9:1) over barely warm grays. Warning and error keep their default colors.
     */
    private static final Theme COFFEE = Theme.builder()
            .accent(0xFF8F5628)
            .panelBg(0xFF33302E).panelBgDeep(0xFF201E1D)
            .buttonFace(0xFFD6D2CE).buttonFaceHover(0xFFEAE6E2).buttonShadow(0xFF5C5754)
            .outline(0xFF1F1D1C)
            .textOnDark(0xFFFBF8F5).textOnLight(0xFF221F1D).textDisabled(0xFF75706C)
            .toggleOffTrack(0xFF8F8A86)
            .titleBarBg(0xFFE9E6E3).titleBarText(0xFF221F1D)
            .build();

    private static ConfigValue<Boolean> injectConfigButtons;
    private static ConfigValue<Boolean> backupExternalFiles;
    private static ConfigValue<List<String>> fileMappings;
    private static ConfigValue<List<String>> ignoredMods;
    private static ConfigValue<List<String>> forceConfeeEditor;
    private static ConfigValue<List<String>> pickerStyles;
    @org.jetbrains.annotations.Nullable
    private static ConfeeConfig<Void> config;

    private ConfeeSettings() {
    }

    static ConfeeConfig<Void> register() {
        ConfigBuilder b = Confee.builder(ConfeeMod.MOD_ID).scope(ConfigScope.CLIENT).theme(COFFEE);
        CategoryBuilder general = b.category("general");
        injectConfigButtons = general.bool("injectConfigButtons", true)
                .comment("Add a Config button to mods that have none (universal editor)").requiresRestart().build();
        backupExternalFiles = general.bool("backupExternalFiles", true)
                .comment("Keep a copy of the original file before Confee changes it for the first time").build();
        CategoryBuilder external = b.category("external");
        fileMappings = external.list("fileMappings", String.class, List.of())
                .comment("Manual mod -> file mappings, one per entry: \"modid=path\" (path relative to config/)").build();
        ignoredMods = external.list("ignoredMods", String.class, List.of())
                .comment("Mods ignored by the universal editor").requiresRestart()
                .registry(fr.heetstudio.confee.core.PseudoRegistries.EDITABLE_MOD).build();
        // read each time a Config button is clicked: no restart needed
        forceConfeeEditor = external.list("forceConfeeEditor", String.class, List.of())
                .comment("Mods whose own config screen is replaced by the Confee editor (their config files must be found)")
                .registry(fr.heetstudio.confee.core.PseudoRegistries.REPLACEABLE_MOD).build();
        // suggestions, logos and the mod picker list only the mods whose screen can be replaced
        fr.heetstudio.confee.core.PseudoRegistries.register(fr.heetstudio.confee.core.PseudoRegistries.REPLACEABLE_MOD,
                ConfeeSettings::forceableMods);
        fr.heetstudio.confee.core.PseudoRegistries.register(fr.heetstudio.confee.core.PseudoRegistries.EDITABLE_MOD,
                ConfeeSettings::editableMods);
        // last picker style chosen by the player, per kind of entry: "registry=STYLE"
        pickerStyles = general.list("pickerStyles", String.class, List.of()).hidden()
                .comment("Last picker style (GRID, LIST, DETAILED) chosen for each kind of ID, \"registry=STYLE\"").build();
        config = b.build();
        return config;
    }

    /** The picker style the player last chose for this kind of entry, or null. */
    @org.jetbrains.annotations.Nullable
    public static fr.heetstudio.confee.api.PickerStyle rememberedPickerStyle(String registry) {
        if (pickerStyles == null) {
            return null;
        }
        for (String entry : pickerStyles.get()) {
            if (entry.startsWith(registry + "=")) {
                try {
                    fr.heetstudio.confee.api.PickerStyle style =
                            fr.heetstudio.confee.api.PickerStyle.valueOf(entry.substring(registry.length() + 1).trim());
                    return style == fr.heetstudio.confee.api.PickerStyle.AUTO ? null : style;
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Remembers the picker style chosen by the player for this kind of entry and saves confee.toml. */
    public static void rememberPickerStyle(String registry, fr.heetstudio.confee.api.PickerStyle style) {
        if (pickerStyles == null || style == rememberedPickerStyle(registry)) {
            return;
        }
        List<String> entries = new ArrayList<>();
        for (String entry : pickerStyles.get()) {
            if (!entry.startsWith(registry + "=")) {
                entries.add(entry);
            }
        }
        entries.add(registry + "=" + style.name());
        pickerStyles.set(List.copyOf(entries));
        if (config != null) {
            config.save();
        }
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

    /**
     * {@code true} when the player asked Confee to replace the mod's own screen. {@code ignoredMods} wins over it,
     * {@code injectConfigButtons} does not.
     */
    public static boolean isConfeeEditorForced(String modId) {
        return forceConfeeEditor != null && !modId.equals(ConfeeMod.MOD_ID)
                && forceConfeeEditor.get().contains(modId) && !ignoredMods().contains(modId);
    }

    /**
     * Suggestions of {@code ignoredMods}: mods the universal editor handles (config files found, or Forge/NeoForge
     * configs), plus the installed mods already ignored (the discovery skips them).
     */
    private static java.util.Collection<String> editableMods() {
        Set<String> out = new java.util.TreeSet<>(fr.heetstudio.confee.external.ExternalFiles.discoverAll().keySet());
        out.addAll(fr.heetstudio.confee.platform.Services.PLATFORM.getLoaderConfigModIds());
        for (String modId : ignoredMods()) {
            if (fr.heetstudio.confee.platform.Services.PLATFORM.isModLoaded(modId)) {
                out.add(modId);
            }
        }
        out.remove(ConfeeMod.MOD_ID);
        return out;
    }

    /** Suggestions of {@code forceConfeeEditor}: mods with their own screen whose config files Confee can edit. */
    private static java.util.Collection<String> forceableMods() {
        Set<String> own = fr.heetstudio.confee.platform.Services.PLATFORM.getModsWithOwnConfigScreen();
        Set<String> out = new java.util.TreeSet<>();
        Set<String> withFiles = fr.heetstudio.confee.external.ExternalFiles.discoverAll().keySet();
        Set<String> ignored = ignoredMods();
        for (String modId : own) {
            if (modId.equals(ConfeeMod.MOD_ID) || ignored.contains(modId)) {
                continue;
            }
            if (withFiles.contains(modId)
                    || !fr.heetstudio.confee.platform.Services.PLATFORM.getLoaderConfigs(modId).isEmpty()) {
                out.add(modId);
            }
        }
        return out;
    }

    /** The {@code fileMappings} value (test scripts). */
    public static ConfigValue<List<String>> fileMappingsValue() {
        return fileMappings;
    }

    /** The {@code forceConfeeEditor} value (test scripts). */
    public static ConfigValue<List<String>> forceConfeeEditorValue() {
        return forceConfeeEditor;
    }

    /** The {@code pickerStyles} value (test scripts). */
    public static ConfigValue<List<String>> pickerStylesValue() {
        return pickerStyles;
    }

    public static Set<String> ignoredMods() {
        return ignoredMods == null ? Set.of() : new LinkedHashSet<>(ignoredMods.get());
    }
}

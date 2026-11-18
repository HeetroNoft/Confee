package fr.heetstudio.confee.ui;

import fr.heetstudio.confee.ConfeeSettings;
import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.core.ClientBridge;
import fr.heetstudio.confee.core.ConfeeRegistry;
import fr.heetstudio.confee.core.ConfigHandle;
import fr.heetstudio.confee.external.ExternalConfig;
import fr.heetstudio.confee.external.ExternalFiles;
import fr.heetstudio.confee.ui.screen.ConfigScreen;
import fr.heetstudio.confee.ui.screen.FileSelectScreen;
import fr.heetstudio.confee.ui.screen.UnreadableFileScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Opens Confee screens. Client side only.
 * <p>Internal: may change without notice.</p>
 */
public final class ConfeeScreens {

    private ConfeeScreens() {
    }

    /** Called once on the client by the loader entrypoint. */
    public static void initClient() {
        ClientBridge.setScreenFactory((handle, parent) -> forConfig((Screen) parent, handle));
    }

    /**
     * Mods that get a "Config" button from Confee: mods with a Confee config, plus (universal editor, spec 8.1) mods
     * with config files found in {@code config/}. The loader integrations still skip mods that have their own screen.
     */
    public static Set<String> modsWithScreens() {
        Set<String> mods = new LinkedHashSet<>();
        for (ConfigHandle<?> handle : ConfeeRegistry.all()) {
            if (handle.isActive()) {
                mods.add(handle.schema().modId());
            }
        }
        if (ConfeeSettings.injectConfigButtons()) {
            mods.addAll(fr.heetstudio.confee.platform.Services.PLATFORM.getLoaderConfigModIds());
            mods.addAll(ExternalFiles.discoverAll().keySet());
        }
        return mods;
    }

    /**
     * The mod's only config screen, or the file selection when it has several (spec 10.4 A).
     *
     * @return null when the mod has nothing to edit (no button, spec 8.1)
     */
    @Nullable
    public static Screen forMod(Screen parent, String modId) {
        List<FileSelectScreen.Entry> entries = new ArrayList<>();
        Theme theme = Theme.DEFAULT;
        for (ConfigHandle<?> handle : configs(modId)) {
            if (entries.isEmpty()) {
                theme = handle.schema().theme();
            }
            entries.add(new FileSelectScreen.Entry(Labels.configName(handle.schema()),
                    handle.schema().filePath().getFileName().toString(),
                    Component.translatable("confee.file.source.confee"),
                    p -> new ConfigScreen(p, handle)));
        }
        if (ConfeeSettings.injectConfigButtons()) {
            // spec 8.2 item 2: configs of the loader's own system (Forge/NeoForge specs)
            for (fr.heetstudio.confee.platform.LoaderConfig config :
                    fr.heetstudio.confee.platform.Services.PLATFORM.getLoaderConfigs(modId)) {
                Component reason = config.loaded() ? null : Component.translatable("confee.file.server_not_loaded");
                entries.add(new FileSelectScreen.Entry(Component.literal(ValueText.humanize(config.type())),
                        config.fileName(), Component.translatable("confee.file.source.forge_spec"),
                        p -> {
                            fr.heetstudio.confee.core.EditableConfig editable = config.open();
                            return editable == null ? p : new ConfigScreen(p, editable);
                        }, reason));
            }
            Path configDir = fr.heetstudio.confee.platform.Services.PLATFORM.getConfigDir();
            List<Path> files = new ArrayList<>(ExternalFiles.forMod(modId));
            // the mod's main file first, then the others by path
            files.sort(java.util.Comparator.comparing((Path f) -> ExternalConfig.readableName(modId, f, configDir) != null)
                    .thenComparing(Path::toString));
            for (Path file : files) {
                String readable = ExternalConfig.readableName(modId, file, configDir);
                entries.add(new FileSelectScreen.Entry(
                        readable == null ? Component.translatable("confee.file.main") : Component.literal(ValueText.humanize(readable)),
                        ExternalConfig.relativePath(file, configDir),
                        Component.translatable("confee.file.source.raw_file"),
                        p -> openExternal(p, modId, file)));
            }
        }
        if (entries.isEmpty()) {
            return null;
        }
        if (entries.size() == 1 && entries.get(0).disabledReason() == null) {
            return entries.get(0).open().apply(parent);
        }
        return new FileSelectScreen(parent, modId, theme, entries);
    }

    /** Universal editor on one file, or the "cannot read" screen (spec 10.4 D). */
    public static Screen openExternal(Screen parent, String modId, Path file) {
        ExternalConfig config = ExternalFiles.open(modId, file);
        return config.status() == ExternalConfig.Status.OK ? new ConfigScreen(parent, config)
                : new UnreadableFileScreen(parent, config);
    }

    public static Screen forConfig(Screen parent, ConfigHandle<?> handle) {
        return new ConfigScreen(parent, handle);
    }

    private static List<ConfigHandle<?>> configs(String modId) {
        return ConfeeRegistry.forMod(modId).stream().filter(ConfigHandle::isActive).toList();
    }
}

package fr.heetstudio.confee.external;

import fr.heetstudio.confee.ConfeeSettings;
import fr.heetstudio.confee.core.ConfeeRegistry;
import fr.heetstudio.confee.core.ConfigHandle;
import fr.heetstudio.confee.platform.Services;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entry point of the universal editor's file lookup (spec 8.2): Confee configs and loader configs are excluded,
 * then the heuristic, the manual mappings of {@code confee.toml} and the files declared through the API are used.
 * <p>Internal: may change without notice.</p>
 */
public final class ExternalFiles {

    private static final Map<String, List<String>> API_FILES = new LinkedHashMap<>();

    private ExternalFiles() {
    }

    /** Files declared by {@code Confee.registerExternalFile} (spec 8.2 item 5). */
    public static synchronized void register(String modId, Path path) {
        API_FILES.computeIfAbsent(modId, k -> new ArrayList<>()).add(path.toString());
    }

    /** Raw files of every loaded mod (an empty map when the universal editor is turned off). */
    public static Map<String, List<Path>> discoverAll() {
        Path configDir = Services.PLATFORM.getConfigDir();
        List<ConfigDiscovery.ModInfo> mods = new ArrayList<>();
        for (String id : Services.PLATFORM.getModIds()) {
            mods.add(new ConfigDiscovery.ModInfo(id, Services.PLATFORM.getModDisplayName(id).orElse(id)));
        }
        Set<Path> excluded = new HashSet<>(Services.PLATFORM.getLoaderManagedConfigFiles());
        for (ConfigHandle<?> handle : ConfeeRegistry.all()) {
            excluded.add(handle.schema().filePath().toAbsolutePath().normalize());
        }
        Map<String, List<String>> manual = new LinkedHashMap<>();
        synchronized (ExternalFiles.class) {
            API_FILES.forEach((mod, paths) -> manual.computeIfAbsent(mod, k -> new ArrayList<>()).addAll(paths));
        }
        ConfeeSettings.fileMappings().forEach((mod, paths) -> manual.computeIfAbsent(mod, k -> new ArrayList<>()).addAll(paths));
        return ConfigDiscovery.discover(configDir, mods, excluded, manual, ConfeeSettings.ignoredMods());
    }

    public static List<Path> forMod(String modId) {
        return discoverAll().getOrDefault(modId, List.of());
    }

    public static ExternalConfig open(String modId, Path file) {
        return ExternalConfig.open(modId, file, Services.PLATFORM.getConfigDir(), ConfeeSettings.backupExternalFiles());
    }
}

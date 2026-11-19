package fr.heetstudio.confee.external;

import fr.heetstudio.confee.ConfeeLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Finds the config files of mods that do not use Confee (spec 8.2, heuristic part). Pure logic over a directory.
 * <ul>
 *   <li>{@code config/<modid>.<ext>}, {@code config/<modid>-<suffix>.<ext>}, {@code config/<modid>_<suffix>.<ext>}</li>
 *   <li>every file of {@code config/<modid>/}, sub-folders included (any depth)</li>
 *   <li>case-insensitive; '-', '_' and no separator are equivalent; the mod's display name is tried too</li>
 *   <li>skips excluded files (Confee / loader configs), backups ({@code .bak}, {@code .old}, {@code .broken-*}),
 *       files over 1 MB and binary files</li>
 * </ul>
 * When several mods match a file, the longest mod ID wins (so {@code mymod-addon.toml} goes to {@code mymod-addon}).
 * <p>Internal: may change without notice.</p>
 */
public final class ConfigDiscovery {

    static final long MAX_SIZE = 1024 * 1024;
    /** Confee's own folder (backups of external files) is never scanned. */
    static final String CONFEE_DIR = "confee";

    /** A loaded mod. */
    public record ModInfo(String id, String displayName) {
    }

    private ConfigDiscovery() {
    }

    /**
     * @param excluded files already covered by Confee configs or the loader's config system (absolute, normalized)
     * @param manual   extra files per mod from {@code confee.toml} / the API, relative to the config dir or absolute
     * @param ignored  mod IDs the universal editor must skip
     * @return files per mod ID, sorted by path, only for mods that have at least one file
     */
    public static Map<String, List<Path>> discover(Path configDir, Collection<ModInfo> mods, Set<Path> excluded,
                                                   Map<String, List<String>> manual, Set<String> ignored) {
        Map<String, List<Path>> result = new LinkedHashMap<>();
        if (!Files.isDirectory(configDir)) {
            return result;
        }
        Path root = configDir.toAbsolutePath().normalize();
        for (Path file : candidates(root, mods)) {
            if (excluded.contains(file) || !isEditableCandidate(file)) {
                continue;
            }
            Path relative = root.relativize(file);
            String key = relative.getNameCount() == 1 ? baseName(relative.getFileName().toString())
                    : relative.getName(0).toString();
            String owner = owner(key, relative.getNameCount() == 1, mods);
            if (owner != null && !ignored.contains(owner)) {
                result.computeIfAbsent(owner, k -> new ArrayList<>()).add(file);
            }
        }
        manual.forEach((modId, paths) -> {
            if (ignored.contains(modId)) {
                return;
            }
            for (String path : paths) {
                Path file = root.resolve(path).toAbsolutePath().normalize();
                List<Path> files = result.computeIfAbsent(modId, k -> new ArrayList<>());
                if (Files.isRegularFile(file) && !files.contains(file) && !excluded.contains(file)) {
                    files.add(file);
                }
            }
        });
        result.values().removeIf(List::isEmpty);
        result.values().forEach(list -> list.sort(null));
        return result;
    }

    /**
     * Files directly in config/, and every file inside the sub-folders named after a loaded mod, at any depth.
     * Folders of no mod are not walked (they can be large). Symbolic links are not followed (no cycles).
     */
    private static List<Path> candidates(Path root, Collection<ModInfo> mods) {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> top = Files.list(root)) {
            for (Path entry : (Iterable<Path>) top::iterator) {
                if (Files.isRegularFile(entry)) {
                    out.add(entry.toAbsolutePath().normalize());
                } else if (Files.isDirectory(entry) && !entry.getFileName().toString().equalsIgnoreCase(CONFEE_DIR)
                        && owner(entry.getFileName().toString(), false, mods) != null) {
                    try (Stream<Path> inner = Files.walk(entry)) {
                        inner.filter(Files::isRegularFile).map(p -> p.toAbsolutePath().normalize()).forEach(out::add);
                    } catch (IOException | java.io.UncheckedIOException e) {
                        ConfeeLog.warn("Cannot list the config folder {}: {}", entry.getFileName(), e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            ConfeeLog.warn("Cannot list the config folder: {}", e.getMessage());
        }
        return out;
    }

    static boolean isEditableCandidate(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".bak") || name.endsWith(".old") || name.contains(".broken-") || name.endsWith(".confee-tmp")
                || name.endsWith(".tmp") || name.endsWith(".lock") || name.startsWith(".")) {
            return false;
        }
        try {
            if (Files.size(file) > MAX_SIZE) {
                return false;
            }
            return !isBinary(file);
        } catch (IOException e) {
            return false;
        }
    }

    /** A NUL byte in the first 8 KB means a binary file. */
    private static boolean isBinary(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(8192);
            for (byte b : head) {
                if (b == 0) {
                    return true;
                }
            }
            return false;
        }
    }

    static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /**
     * @param prefixAllowed for files directly in config/: {@code <modid>-<suffix>} also matches.
     *                      For folders, the folder name must match the mod exactly (modulo separators).
     */
    static String owner(String key, boolean prefixAllowed, Collection<ModInfo> mods) {
        String lower = key.toLowerCase(Locale.ROOT);
        String compact = compact(lower);
        String best = null;
        int bestLength = -1;
        for (ModInfo mod : mods) {
            String id = mod.id().toLowerCase(Locale.ROOT);
            boolean match = compact.equals(compact(id)) || compact.equals(compact(mod.displayName().toLowerCase(Locale.ROOT)));
            if (!match && prefixAllowed) {
                for (String variant : List.of(id, id.replace('_', '-'), id.replace('-', '_'))) {
                    if (lower.startsWith(variant + "-") || lower.startsWith(variant + "_")) {
                        match = true;
                        break;
                    }
                }
            }
            if (match && id.length() > bestLength) {
                best = mod.id();
                bestLength = id.length();
            }
        }
        return best;
    }

    /** Lower-case letters and digits only: {@code Xaero's-Minimap} → {@code xaerosminimap}. */
    static String compact(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }
}

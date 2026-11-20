package fr.heetstudio.confee.neoforge.platform;

import fr.heetstudio.confee.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public final class NeoForgePlatform implements Platform {

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.getDist().isClient();
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.isProduction();
    }

    @Override
    public java.util.List<Path> getModRootPaths() {
        java.util.List<Path> roots = new java.util.ArrayList<>();
        for (net.neoforged.neoforgespi.language.IModFileInfo file : ModList.get().getModFiles()) {
            try {
                roots.addAll(contentRoots(file.getFile()));
            } catch (RuntimeException e) {
                // a mod file without readable contents: skipped
            }
        }
        return roots;
    }

    @Override
    public Collection<String> getModIds() {
        return ModList.get().getMods().stream()
                .map(IModInfo::getModId)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Optional<String> getModDisplayName(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName());
    }

    @Override
    public Optional<String> getModDescription(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDescription())
                .map(String::strip)
                .filter(text -> !text.isEmpty());
    }

    @Override
    public Optional<Path> getModIcon(String modId) {
        Optional<String> logo = ModList.get().getModContainerById(modId)
                .flatMap(container -> container.getModInfo().getLogoFile());
        net.neoforged.neoforgespi.language.IModFileInfo file = ModList.get().getModFileById(modId);
        if (logo.isEmpty() || file == null) {
            return Optional.empty();
        }
        try {
            for (Path root : contentRoots(file.getFile())) {
                Path icon = root.resolve(logo.get());
                if (java.nio.file.Files.isRegularFile(icon)) {
                    return Optional.of(icon);
                }
            }
        } catch (RuntimeException e) {
            // unreadable mod file: no icon
        }
        return Optional.empty();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public java.util.List<fr.heetstudio.confee.platform.LoaderConfig> getLoaderConfigs(String modId) {
        return fr.heetstudio.confee.neoforge.config.NeoForgeLoaderConfig.forMod(modId);
    }

    @Override
    public java.util.Set<String> getLoaderConfigModIds() {
        return fr.heetstudio.confee.neoforge.config.NeoForgeLoaderConfig.modIds();
    }

    @Override
    public java.util.Set<Path> getLoaderManagedConfigFiles() {
        Path dir = getConfigDir();
        return net.neoforged.fml.config.ModConfigs.getFileMap().keySet().stream()
                .map(name -> dir.resolve(name).toAbsolutePath().normalize())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static final java.util.Set<String> OWN_CONFIG_SCREENS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Recorded by the client setup when a mod registered its own screen factory before Confee. */
    public static void markOwnConfigScreen(String modId) {
        OWN_CONFIG_SCREENS.add(modId);
    }

    @Override
    public java.util.Set<String> getModsWithOwnConfigScreen() {
        return java.util.Set.copyOf(OWN_CONFIG_SCREENS);
    }

    private static final java.util.Map<Path, java.nio.file.FileSystem> JAR_FILE_SYSTEMS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Folders holding the files of a mod. Since NeoForge 21.9 a mod's contents are given as their folders (development)
     * or jar files: a jar is opened once as a zip file system, kept open while the game runs.
     */
    private static java.util.List<Path> contentRoots(net.neoforged.neoforgespi.locating.IModFile file) {
        java.util.List<Path> roots = new java.util.ArrayList<>();
        for (Path path : file.getContents().getContentRoots()) {
            if (java.nio.file.Files.isDirectory(path)) {
                roots.add(path);
            } else if (java.nio.file.Files.isRegularFile(path)) {
                java.nio.file.FileSystem fs = JAR_FILE_SYSTEMS.computeIfAbsent(path, jar -> {
                    try {
                        return java.nio.file.FileSystems.newFileSystem(jar);
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
                roots.add(fs.getPath("/"));
            }
        }
        return roots;
    }
}

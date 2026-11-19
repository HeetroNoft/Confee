package fr.heetstudio.confee.forge.platform;

import fr.heetstudio.confee.platform.Platform;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ForgePlatform implements Platform {

    @Override
    public String getPlatformName() {
        return "Forge";
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public java.util.List<Path> getModRootPaths() {
        java.util.List<Path> roots = new java.util.ArrayList<>();
        for (net.minecraftforge.forgespi.language.IModFileInfo file : ModList.get().getModFiles()) {
            try {
                roots.add(file.getFile().getSecureJar().getRootPath());
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
        net.minecraftforge.forgespi.language.IModFileInfo file = ModList.get().getModFileById(modId);
        if (logo.isEmpty() || file == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(file.getFile().findResource(logo.get()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public java.util.List<fr.heetstudio.confee.platform.LoaderConfig> getLoaderConfigs(String modId) {
        return fr.heetstudio.confee.forge.config.ForgeLoaderConfig.forMod(modId);
    }

    @Override
    public java.util.Set<String> getLoaderConfigModIds() {
        return fr.heetstudio.confee.forge.config.ForgeLoaderConfig.modIds();
    }

    @Override
    public java.util.Set<Path> getLoaderManagedConfigFiles() {
        Path dir = getConfigDir();
        return net.minecraftforge.fml.config.ConfigTracker.INSTANCE.fileMap().keySet().stream()
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
}

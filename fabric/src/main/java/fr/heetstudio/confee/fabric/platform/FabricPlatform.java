package fr.heetstudio.confee.fabric.platform;

import fr.heetstudio.confee.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public final class FabricPlatform implements Platform {

    @Override
    public String getPlatformName() {
        return FabricLoader.getInstance().isModLoaded("quilt_loader") ? "Quilt" : "Fabric";
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public java.util.List<Path> getModRootPaths() {
        java.util.List<Path> roots = new java.util.ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            roots.addAll(mod.getRootPaths());
        }
        return roots;
    }

    @Override
    public Collection<String> getModIds() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(mod -> mod.getMetadata().getId())
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Optional<String> getModDisplayName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(ModContainer::getMetadata)
                .map(meta -> meta.getName());
    }

    @Override
    public Collection<String> getTopLevelModIds() {
        return FabricLoader.getInstance().getAllMods().stream()
                .filter(mod -> mod.getContainingMod().isEmpty() && !isChildModule(mod.getMetadata()))
                .map(mod -> mod.getMetadata().getId())
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Modules that belong to another mod even when loaded on their own (in a development environment): Fabric API
     * modules, and mods that name a parent the way Mod Menu reads it ({@code "custom": {"modmenu": {"parent": ...}}}).
     */
    private static boolean isChildModule(net.fabricmc.loader.api.metadata.ModMetadata meta) {
        if (meta.containsCustomValue("fabric-api:module-lifecycle") && !meta.getId().equals("fabric-api")) {
            return true;
        }
        if (!meta.containsCustomValue("modmenu")) {
            return false;
        }
        net.fabricmc.loader.api.metadata.CustomValue modmenu = meta.getCustomValue("modmenu");
        return modmenu.getType() == net.fabricmc.loader.api.metadata.CustomValue.CvType.OBJECT
                && modmenu.getAsObject().containsKey("parent");
    }

    @Override
    public Optional<String> getModDescription(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(mod -> mod.getMetadata().getDescription())
                .filter(text -> !text.isBlank());
    }

    @Override
    public Optional<Path> getModIcon(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .flatMap(mod -> mod.getMetadata().getIconPath(64).flatMap(mod::findPath));
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    /** Mods with a {@code modmenu} entrypoint (read from the metadata: the entrypoints are not instantiated). */
    @Override
    public java.util.Set<String> getModsWithOwnConfigScreen() {
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) {
            return java.util.Set.of();
        }
        return FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class).stream()
                .map(container -> container.getProvider().getMetadata().getId())
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }
}

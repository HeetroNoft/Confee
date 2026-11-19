package fr.heetstudio.confee.platform;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Loader abstraction (spec 9.5), implemented once per loader module and loaded through {@link java.util.ServiceLoader}.
 * <p>Internal: may change without notice.</p>
 */
public interface Platform {

    /** Human readable loader name, for logs. */
    String getPlatformName();

    /** The game's {@code config/} directory. */
    Path getConfigDir();

    /** {@code true} on the physical client. */
    boolean isClient();

    /** {@code true} when running from a development environment (IDE / Gradle run). */
    boolean isDevelopmentEnvironment();

    /** IDs of every loaded mod. */
    Collection<String> getModIds();

    /** Display name declared by the mod, if the mod is loaded. */
    Optional<String> getModDisplayName(String modId);

    boolean isModLoaded(String modId);

    /**
     * Files handled by the loader's own config system (Forge/NeoForge {@code ModConfig}). The universal editor does not
     * treat them as raw files (spec 8.2). Empty on Fabric.
     */
    default Set<Path> getLoaderManagedConfigFiles() {
        return Set.of();
    }

    /** Configs of a mod declared through the loader's config system (spec 8.4). Empty on Fabric. */
    default java.util.List<LoaderConfig> getLoaderConfigs(String modId) {
        return java.util.List.of();
    }

    /**
     * Root folders of every loaded mod file (jar contents), the game included when the loader exposes it. Used to
     * list data files (biomes, structures, tags...) outside of a world.
     */
    default java.util.List<Path> getModRootPaths() {
        return java.util.List.of();
    }

    /** Mods that declared configs through the loader's config system. Empty on Fabric. */
    default Set<String> getLoaderConfigModIds() {
        return Set.of();
    }

    /**
     * Mods that provide their own config screen to the loader's mod list (Mod Menu entrypoint on Fabric/Quilt, screen
     * factory registered before Confee's on Forge/NeoForge). Used to suggest values for {@code forceConfeeEditor}.
     */
    default Set<String> getModsWithOwnConfigScreen() {
        return Set.of();
    }

    /**
     * Mods shown in mod suggestions and the mod picker: every mod except the ones bundled inside another mod (the
     * modules of Fabric API, for example), as in Mod Menu.
     */
    default Collection<String> getTopLevelModIds() {
        return getModIds();
    }

    /** Description of the mod from its metadata, or empty. */
    default java.util.Optional<String> getModDescription(String modId) {
        return java.util.Optional.empty();
    }

    /** Logo file of the mod (Fabric {@code icon}, Forge {@code logoFile}) inside its jar, or empty. */
    default java.util.Optional<Path> getModIcon(String modId) {
        return java.util.Optional.empty();
    }
}

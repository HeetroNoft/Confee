package fr.heetstudio.confee.neoforge.config;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.core.EditableConfig;
import fr.heetstudio.confee.platform.LoaderConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Forge {@link ModConfig} exposed to common code (spec 8.2 item 2, 8.4).
 * <p>Internal: may change without notice.</p>
 */
public final class NeoForgeLoaderConfig implements LoaderConfig {

    private final ModConfig config;

    private NeoForgeLoaderConfig(ModConfig config) {
        this.config = config;
    }

    /** ModConfigSpec-based configs of a mod: client, common, then server. */
    public static List<LoaderConfig> forMod(String modId) {
        List<ModConfig> configs = new ArrayList<>();
        ConfigTracker.INSTANCE.configSets().values().forEach(set -> set.stream()
                .filter(c -> c.getModId().equals(modId) && c.getSpec() instanceof ModConfigSpec)
                .forEach(configs::add));
        // Client, Common, Server (spec 10.4 A order), not Forge's enum order
        List<ModConfig.Type> order = List.of(ModConfig.Type.CLIENT, ModConfig.Type.COMMON, ModConfig.Type.STARTUP, ModConfig.Type.SERVER);
        configs.sort(Comparator.comparing((ModConfig c) -> order.indexOf(c.getType())).thenComparing(ModConfig::getFileName));
        List<LoaderConfig> out = new ArrayList<>();
        for (ModConfig c : configs) {
            out.add(new NeoForgeLoaderConfig(c));
        }
        return out;
    }

    public static Set<String> modIds() {
        Set<String> ids = new LinkedHashSet<>();
        ConfigTracker.INSTANCE.configSets().values().forEach(set -> set.stream()
                .filter(c -> c.getSpec() instanceof ModConfigSpec)
                .forEach(c -> ids.add(c.getModId())));
        return ids;
    }

    @Override
    public String modId() {
        return config.getModId();
    }

    @Override
    public String type() {
        return NeoForgeSpecConfig.type(config);
    }

    @Override
    public String fileName() {
        return config.getFileName();
    }

    @Override
    public boolean readOnly() {
        return config.getType() == ModConfig.Type.SERVER;
    }

    @Override
    public boolean loaded() {
        return config.getConfigData() != null && config.getSpec() instanceof ModConfigSpec spec && spec.isLoaded();
    }

    @Override
    @Nullable
    public EditableConfig open() {
        if (!loaded()) {
            return null;
        }
        try {
            Path path = config.getType() == ModConfig.Type.SERVER ? config.getFullPath()
                    : FMLPaths.CONFIGDIR.get().resolve(config.getFileName());
            return new NeoForgeSpecConfig(config, (ModConfigSpec) config.getSpec(), path);
        } catch (RuntimeException e) {
            ConfeeLog.error("Cannot read the Forge config " + config.getFileName() + " of " + config.getModId(), e);
            return null;
        }
    }
}

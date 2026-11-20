package fr.heetstudio.confee.rawmod;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

import java.util.List;

/**
 * Development-only mod WITHOUT Confee and without a config screen. On Forge it declares {@link ModConfigSpec}s like
 * most Forge mods do, to test Confee's Forge spec integration (spec 8.4), plus raw files for the universal editor.
 */
@Mod("confee_rawmod")
public final class ConfeeRawmodNeoForge {

    public enum Mode { FAST, BALANCED, QUALITY, EXPERIMENTAL }

    private static ModConfigSpec.IntValue zoom;

    public ConfeeRawmodNeoForge() {
        // like a real mod: re-read the values when Forge says the config was reloaded
        net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (net.neoforged.fml.event.config.ModConfigEvent.Reloading event) -> {
                    if (event.getConfig().getType() == ModConfig.Type.CLIENT) {
                        org.slf4j.LoggerFactory.getLogger("confee_rawmod")
                                .info("[rawmod] client config reloaded, zoom is now {}", zoom.get());
                    }
                });
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, clientSpec(), "confee_rawmod-forge-client.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, commonSpec(), "confee_rawmod-forge-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, serverSpec(), "confee_rawmod-forge-server.toml");
    }

    private static ModConfigSpec clientSpec() {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Show the minimap").define("showMinimap", true);
        zoom = b.comment("Zoom level of the minimap").defineInRange("zoom", 3, 1, 8);
        b.comment("Opacity of the minimap, from 0 to 1").defineInRange("opacity", 0.8, 0.0, 1.0);
        b.comment("Render mode (EXPERIMENTAL is not allowed)")
                .defineEnum("mode", Mode.BALANCED, Mode.FAST, Mode.BALANCED, Mode.QUALITY);
        b.comment("Waypoint label").define("label", "Home");
        b.comment("Color of the markers").define("markerColor", "#3366FF");
        b.comment("Mob shown on the minimap").define("favoriteMob", "minecraft:allay");
        b.comment("Rendering details").push("render");
        b.comment("Number of entities drawn").defineInRange("maxEntities", 64, 0, 512);
        b.comment("Blocks never drawn").defineListAllowEmpty(List.of("hiddenBlocks"),
                () -> List.of("minecraft:grass", "minecraft:fern"), o -> o instanceof String);
        b.pop();
        return b.build();
    }

    private static ModConfigSpec commonSpec() {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Seed offset used by the generator").worldRestart().defineInRange("seedOffset", 0L, 0L, 1_000_000L);
        b.comment("Enable the generator").define("generatorEnabled", true);
        return b.build();
    }

    private static ModConfigSpec serverSpec() {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Maximum number of waypoints per player").defineInRange("maxWaypoints", 20, 1, 100);
        b.comment("Allow teleporting to waypoints").define("allowTeleport", false);
        return b.build();
    }
}

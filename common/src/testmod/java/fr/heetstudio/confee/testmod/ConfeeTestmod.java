package fr.heetstudio.confee.testmod;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.api.Confee;
import fr.heetstudio.confee.api.ConfeeConfig;
import fr.heetstudio.confee.api.ConfigValue;
import fr.heetstudio.confee.api.Preview;
import fr.heetstudio.confee.api.builder.CategoryBuilder;
import fr.heetstudio.confee.api.builder.ConfigBuilder;
import fr.heetstudio.confee.api.builder.ObjectSpec;

import java.util.List;
import java.util.Map;

/**
 * Test mod (spec 15), not published. Declares an annotation config with every type, the equivalent builder config,
 * and a config with another theme.
 */
public final class ConfeeTestmod {

    public static final String MOD_ID = "confee_testmod";

    public static ConfeeConfig<AnnotatedTestConfig> annotated;
    public static ConfeeConfig<ThemedTestConfig> themed;
    public static ConfeeConfig<Void> builder;
    public static ConfigValue<Double> builderDashDistance;

    private ConfeeTestmod() {
    }

    public static void init() {
        annotated = Confee.register(AnnotatedTestConfig.class);
        themed = Confee.register(ThemedTestConfig.class);
        builder = registerBuilderConfig();

        AnnotatedTestConfig values = annotated.get();
        ConfeeLog.info("[testmod] annotated: showHud={} dashDistance={} item={} quality={}",
                values.showHud, values.dashDistance, values.item, values.quality);
        ConfeeLog.info("[testmod] builder: dash.distance={}", builderDashDistance.get());
    }

    /** Same options as {@link AnnotatedTestConfig}, declared with the builder. File: config/confee_testmod-builder.toml */
    private static ConfeeConfig<Void> registerBuilderConfig() {
        ConfigBuilder b = Confee.builder(MOD_ID).name("builder").comment("Confee test mod: builder API")
                .layout(fr.heetstudio.confee.api.Layout.builder().panelWidth(440).previewWidth(160).build());

        b.info("Every option type and builder method of Confee. Hover a name for its tooltip.");
        b.bool("showHud", true).comment("A simple toggle").description("Shown under the name: the HUD is drawn over the game.")
                .preview(Preview.text("Shows the §aHUD§r over the game (formatted text).")).build();
        b.intValue("particleCount", 20).name("Particle count").requiresRestart()
                .preview(Preview.renderer(ParticlePreview::new).withText("Live drawing: one dot per particle (max 200).")).build();
        b.intRange("volume", 70, 0, 100).unit("%").enabledIf("showHud").comment("Only editable when the HUD is shown")
                .description("Grayed out while the HUD is off.").build();
        b.intRange("lives", 3, 0, 64).stepper(1).description("Stepper: − and + buttons, arrows, Shift = ×10.").build();
        // custom widget registered on the client (ConfeeTestmodClient)
        b.intRange("temperature", 18, -20, 50).description("A custom widget written by the mod.").build();
        b.decoration(fr.heetstudio.confee.api.Decoration.header("Text fields").icon("minecraft:writable_book"));
        b.intRange("stepped", 5, 0, 10).slider(1).build();
        b.longValue("bigNumber", 5_000_000_000L).range(0L, null).build();
        b.floatRange("opacity", 0.8f, 0f, 1f).slider(0.05).build();
        b.string("title", "Hello Confee").comment("Free text").build();
        b.string("motd", "Welcome!\nHave fun.").multiline().comment("Several lines of text").build();
        b.string("code", "abc").pattern("[a-z_]+").maxLength(16).build();
        b.color("tint", 0xFF3C8527).build();
        b.color("overlay", 0x802D7DD2).alpha().build();
        b.item("item", "minecraft:diamond").comment("Item: icon, suggestions, picker and automatic preview").build();
        b.block("favoriteBlock", "minecraft:grass_block").build();
        b.string("direction", "north").suggestions("north", "south", "east", "west", "up", "down")
                .description("Suggestions given by the mod (press Down).").build();
        b.string("favoriteMob", "minecraft:axolotl").entity().description("Entity: spawn egg icons, mods' entities included.").build();
        b.list("spawnBiomes", String.class, List.of("minecraft:plains", "#minecraft:is_forest")).biome().tags()
                .description("Biome or biome tag: from the world when one is loaded, else from the game and mod files.").build();
        b.string("bonusEffect", "minecraft:speed").effect().build();
        b.string("favoriteEnchantment", "minecraft:mending").enchantment().build();
        b.string("alertSound", "minecraft:block.note_block.bell").sound().build();
        b.keybind("dashKey", "key.keyboard.r").description("Click, then press a key.").build();
        b.enumValue("difficulty", AnnotatedTestConfig.Difficulty.NORMAL).comment("Two values: segmented buttons").build();
        b.enumValue("quality", AnnotatedTestConfig.Quality.HIGH).comment("Five values: dropdown")
                .preview(Preview.image("confee_testmod:textures/preview/quality_{value}.png").withText("The image follows the selected value."))
                .build();
        b.bool("insaneWarning", true).visibleIf("quality=INSANE").comment("Only visible with the INSANE quality").build();
        b.decoration(fr.heetstudio.confee.api.Decoration.warning("INSANE quality needs a strong computer.").visibleIf("quality=INSANE"));
        b.enumSet("allowedQualities", AnnotatedTestConfig.Quality.class,
                List.of(AnnotatedTestConfig.Quality.LOW, AnnotatedTestConfig.Quality.HIGH))
                .description("Check boxes: several values of an enum.").build();
        b.header("Collections");
        b.list("blacklist", String.class, List.of("minecraft:dirt", "minecraft:sand")).block().build();
        b.list("numbers", Integer.class, List.of(1, 2, 3)).build();
        b.list("allowedDifficulties", AnnotatedTestConfig.Difficulty.class,
                List.of(AnnotatedTestConfig.Difficulty.EASY, AnnotatedTestConfig.Difficulty.NORMAL)).comment("List of enum values").build();
        b.map("biomeWeights", Integer.class, Map.of("desert", 3, "plains", 10)).comment("Key -> value map").build();
        ObjectSpec waypoint = ObjectSpec.builder().string("name", "new").intValue("x", 0).intValue("y", 64).bool("visible", true).build();
        b.objectList("waypoints", waypoint, List.of(waypoint.element("name", "home", "x", 0, "y", 64, "visible", true)))
                .comment("List of objects").build();
        b.intValue("internal_counter", 0).hidden().build();
        b.decoration(fr.heetstudio.confee.api.Decoration.button("Open the Minecraft wiki",
                () -> net.minecraft.Util.getPlatform().openUri("https://minecraft.wiki")).icon("minecraft:book"));

        CategoryBuilder dash = b.category("dash").icon("minecraft:feather");
        builderDashDistance = dash.doubleRange("dashDistance", 4.0, 1.0, 10.0).slider(0.5)
                .comment("Dash distance, in blocks")
                .listener((oldValue, newValue) -> ConfeeLog.info("[testmod] dash distance {} -> {}", oldValue, newValue))
                .build();
        dash.bool("dashEnabled", true).build();

        b.category("display", display -> display.icon("minecraft:spyglass").doubleRange("scale", 1.0, 0.5, 3.0).build());

        CategoryBuilder advanced = b.category("advanced").icon("minecraft:redstone").comment("Nested section");
        advanced.bool("debug", false).build();
        advanced.intRange("threads", 4, 1, 64).build();

        return b.build();
    }
}

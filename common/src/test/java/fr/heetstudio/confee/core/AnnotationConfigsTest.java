package fr.heetstudio.confee.core;

import fr.heetstudio.confee.api.ConfeeConfig;
import fr.heetstudio.confee.api.ConfigScope;
import fr.heetstudio.confee.api.annotation.Category;
import fr.heetstudio.confee.api.annotation.Color;
import fr.heetstudio.confee.api.annotation.Comment;
import fr.heetstudio.confee.api.annotation.Config;
import fr.heetstudio.confee.api.annotation.Hidden;
import fr.heetstudio.confee.api.annotation.Key;
import fr.heetstudio.confee.api.annotation.MaxLength;
import fr.heetstudio.confee.api.annotation.Name;
import fr.heetstudio.confee.api.annotation.Pattern;
import fr.heetstudio.confee.api.annotation.Range;
import fr.heetstudio.confee.api.annotation.RequiresRestart;
import fr.heetstudio.confee.api.annotation.Slider;
import fr.heetstudio.confee.api.annotation.Theme;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationConfigsTest {

    enum Difficulty { EASY, NORMAL, HARD }

    @Config(modId = "annotated")
    @Theme(accent = 0xFF2D7DD2, panelBg = 0xFF202020)
    @Comment("Annotated test config")
    public static class FullConfig {
        @Comment("Show the mod HUD")
        public boolean showHud = true;

        @Category("dash")
        @Comment("Dash distance, in blocks")
        @Range(min = 1, max = 10)
        @Slider(step = 0.5)
        public double dashDistance = 4.0;

        @Category("dash")
        public Difficulty difficulty = Difficulty.NORMAL;

        @RequiresRestart
        public int particleCount = 20;

        @Range(min = 0)
        public long bigNumber = 5_000_000_000L;

        public float ratio = 0.25f;

        @Pattern("[a-z]+")
        @MaxLength(8)
        public String code = "abc";

        @Color(alpha = true)
        public int tint = 0x802D7DD2;

        @Hidden
        @Key("secret_key")
        @Name("Secret")
        public String secret = "s";

        public List<String> blacklist = new ArrayList<>(List.of("minecraft:dirt"));

        public ResourceLocation item = new ResourceLocation("minecraft", "stone");

        public Advanced advanced = new Advanced();

        public static final int IGNORED_STATIC = 1;
        public transient int ignoredTransient = 2;
        int ignoredPackagePrivate = 3;
    }

    @fr.heetstudio.confee.api.annotation.Section
    @Comment("Advanced settings")
    public static class Advanced {
        public boolean debug = false;
    }

    @Config(modId = "broken")
    public static class BrokenConfig {
        @Slider(step = 1)
        public int noRange = 5;

        public boolean fine = true;
    }

    @Config(modId = "server", scope = ConfigScope.SERVER)
    public static class ServerConfig {
        public boolean x = true;
    }

    @Config(modId = "clientonly", scope = ConfigScope.CLIENT)
    public static class ClientConfig {
        public boolean x = true;
    }

    @TempDir
    Path dir;

    @BeforeEach
    void setUp() {
        ConfeeRegistry.clear();
        ConfeeEnvironment.override(new ConfeeEnvironment(dir, true, true));
    }

    @Test
    void buildsTheSchemaFromFields() {
        ConfigHandle<FullConfig> handle = AnnotationConfigs.create(FullConfig.class);
        Section root = handle.schema().root();
        List<String> paths = new ArrayList<>();
        root.forEachOption(o -> paths.add(o.path()));
        assertEquals(List.of("showHud", "particleCount", "bigNumber", "ratio", "code", "tint", "secret_key",
                "blacklist", "item", "dash.dashDistance", "dash.difficulty", "advanced.debug"), sorted(paths, root));

        Option<?> distance = root.findOption("dash.dashDistance").orElseThrow();
        assertEquals(WidgetHint.SLIDER, distance.widgetHint());
        assertEquals(1.0, distance.min());
        assertEquals(0.5, distance.step());
        assertTrue(root.findOption("particleCount").orElseThrow().requiresRestart());
        assertTrue(root.findOption("secret_key").orElseThrow().hidden());
        assertEquals("Secret", root.findOption("secret_key").orElseThrow().displayName());
        assertEquals(OptionType.COLOR, root.findOption("tint").orElseThrow().type());
        assertEquals(OptionType.IDENTIFIER, root.findOption("item").orElseThrow().type());
        assertEquals("minecraft:stone", root.findOption("item").orElseThrow().get());
        assertEquals(OptionType.LIST, root.findOption("blacklist").orElseThrow().type());
        assertEquals("Advanced settings", ((Section) root.find("advanced").orElseThrow()).comment());
        assertEquals(0xFF2D7DD2, handle.schema().theme().accent());
        assertEquals(0xFF202020, handle.schema().theme().panelBg());
        assertEquals("Annotated test config", root.comment());
        assertEquals(dir.resolve("annotated.toml"), handle.schema().filePath());
    }

    /** Root options are listed before sections, as in the file. */
    private static List<String> sorted(List<String> paths, Section root) {
        List<String> out = new ArrayList<>();
        paths.stream().filter(p -> !p.contains(".")).forEach(out::add);
        paths.stream().filter(p -> p.contains(".")).forEach(out::add);
        return out;
    }

    @Test
    void valuesFlowBetweenFieldsAndFile() throws Exception {
        ConfeeConfig<FullConfig> config = fr.heetstudio.confee.api.Confee.register(FullConfig.class);
        Path file = dir.resolve("annotated.toml");
        assertTrue(Files.exists(file));
        String text = Files.readString(file);
        assertTrue(text.contains("# Annotated test config"), text);
        assertTrue(text.contains("[dash]"), text);
        assertTrue(text.contains("# Range: 1.0 ~ 10.0"), text);
        assertTrue(text.contains("tint = \"#802D7DD2\""), text);
        assertTrue(text.contains("[advanced]"), text);

        // edit the file by hand, reload: fields are updated and clamped
        Files.writeString(file, text.replace("dashDistance = 4.0", "dashDistance = 99.0")
                .replace("item = \"minecraft:stone\"", "item = \"mymod:gem\"")
                .replace("showHud = true", "showHud = false"));
        config.reload();
        FullConfig values = config.get();
        assertEquals(10.0, values.dashDistance);
        assertEquals(new ResourceLocation("mymod", "gem"), values.item);
        assertFalse(values.showHud);

        // change a field from code, save, file is updated
        values.particleCount = 64;
        config.save();
        assertTrue(Files.readString(file).contains("particleCount = 64"));

        config.resetAll();
        assertTrue(values.showHud);
        assertEquals(20, values.particleCount);
    }

    @Test
    void listFieldIsDecoupledFromTheValidatedValue() {
        ConfeeConfig<FullConfig> config = fr.heetstudio.confee.api.Confee.register(FullConfig.class);
        config.get().blacklist.add("minecraft:sand");
        assertEquals(List.of("minecraft:dirt", "minecraft:sand"),
                ConfeeRegistry.get("annotated", null).orElseThrow().schema().root().findOption("blacklist")
                        .orElseThrow().get());
    }

    @Test
    void declarationMistakeThrowsInDevelopment() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AnnotationConfigs.create(BrokenConfig.class));
        assertTrue(e.getMessage().contains("noRange"), e.getMessage());
        assertTrue(e.getMessage().contains("slider"), e.getMessage());
    }

    @Test
    void declarationMistakeIsSkippedInProduction() {
        ConfeeEnvironment.override(new ConfeeEnvironment(dir, false, true));
        ConfigHandle<BrokenConfig> handle = AnnotationConfigs.create(BrokenConfig.class);
        assertTrue(handle.schema().root().findOption("noRange").isEmpty());
        assertTrue(handle.schema().root().findOption("fine").isPresent());
    }

    @Test
    void serverScopeIsReserved() {
        assertThrows(UnsupportedOperationException.class, () -> AnnotationConfigs.create(ServerConfig.class));
    }

    @Test
    void clientConfigIsNotWrittenOnADedicatedServer() {
        ConfeeEnvironment.override(new ConfeeEnvironment(dir, true, false));
        ConfeeConfig<ClientConfig> config = fr.heetstudio.confee.api.Confee.register(ClientConfig.class);
        assertTrue(config.get().x);
        assertFalse(Files.exists(dir.resolve("clientonly.toml")));
    }

    @Test
    void sameConfigCannotBeRegisteredTwice() {
        fr.heetstudio.confee.api.Confee.register(ClientConfig.class);
        assertThrows(IllegalStateException.class, () -> fr.heetstudio.confee.api.Confee.register(ClientConfig.class));
    }
}

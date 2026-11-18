package fr.heetstudio.confee.core;

import fr.heetstudio.confee.api.ConfeeConfig;
import fr.heetstudio.confee.api.ConfigValue;
import fr.heetstudio.confee.api.Decoration;
import fr.heetstudio.confee.api.Layout;
import fr.heetstudio.confee.api.SuggestionProvider;
import fr.heetstudio.confee.api.annotation.Block;
import fr.heetstudio.confee.api.annotation.Category;
import fr.heetstudio.confee.api.annotation.Config;
import fr.heetstudio.confee.api.annotation.Description;
import fr.heetstudio.confee.api.annotation.EnabledIf;
import fr.heetstudio.confee.api.annotation.Icon;
import fr.heetstudio.confee.api.annotation.Item;
import fr.heetstudio.confee.api.annotation.Keybind;
import fr.heetstudio.confee.api.annotation.Multiline;
import fr.heetstudio.confee.api.annotation.Section;
import fr.heetstudio.confee.api.annotation.Slider;
import fr.heetstudio.confee.api.annotation.Stepper;
import fr.heetstudio.confee.api.annotation.Suggestions;
import fr.heetstudio.confee.api.builder.ConfigBuilder;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Descriptions, item/block shortcuts, suggestions, new widgets, decorations, icons, layout, dev errors. */
class CustomizationTest {

    enum Flag { A, B, C, D }

    public static class MoreSuggestions implements SuggestionProvider {
        @Override
        public Collection<String> suggestions() {
            return List.of("from_provider");
        }
    }

    @Config(modId = "custom")
    @fr.heetstudio.confee.api.annotation.Layout(panelWidth = 480, sidebar = Layout.Sidebar.HIDDEN,
            banner = "custom:textures/gui/banner.png", bannerHeight = 32)
    public static class CustomConfig {
        public Decoration title = Decoration.header("Main");

        @Description("Shown under the name")
        public boolean showHud = true;

        @Item
        public ResourceLocation item = new ResourceLocation("minecraft", "diamond");

        @Block
        public List<String> blocks = List.of("minecraft:stone");

        @Suggestions(value = {"alpha", "beta"}, provider = MoreSuggestions.class)
        public String name = "alpha";

        @Stepper(step = 5)
        public int amount = 10;

        @Keybind
        public String key = "key.keyboard.g";

        public Set<Flag> flags = EnumSet.of(Flag.C, Flag.A);

        public Decoration line = Decoration.separator();

        @Category(value = "dash", icon = "minecraft:feather")
        public boolean dash = true;

        public Nested nested = new Nested();
    }

    @Section
    @Icon("minecraft:compass")
    public static class Nested {
        public int x = 1;
    }

    @Config(modId = "mistakes")
    public static class Mistakes {
        @Slider(step = 1)
        public int noRange = 5;

        @Multiline
        public int notText = 1;

        @EnabledIf("missing")
        public boolean fine = true;
    }

    @Config(modId = "typo")
    public static class Typo {
        public Nested nested = new Nested();

        @EnabledIf("x")
        public boolean dependent = true;
    }

    @TempDir
    Path dir;

    @BeforeEach
    void setUp() {
        ConfeeRegistry.clear();
        ConfeeEnvironment.override(new ConfeeEnvironment(dir, true, true));
    }

    @Test
    void annotationsFillTheModel() {
        ConfigHandle<CustomConfig> handle = AnnotationConfigs.create(CustomConfig.class);
        fr.heetstudio.confee.core.Section root = handle.schema().root();

        assertEquals("Shown under the name", root.findOption("showHud").orElseThrow().description());
        Option<?> item = root.findOption("item").orElseThrow();
        assertEquals("minecraft:item", item.registry());
        assertEquals("minecraft:item", item.registry());
        assertEquals("minecraft:block", root.findOption("blocks").orElseThrow().registry());
        assertEquals(List.of("alpha", "beta", "from_provider"),
                List.copyOf(root.findOption("name").orElseThrow().suggestions().suggestions()));
        Option<?> amount = root.findOption("amount").orElseThrow();
        assertEquals(WidgetHint.STEPPER, amount.widgetHint());
        assertEquals(5.0, amount.step());
        assertEquals(WidgetHint.KEYBIND, root.findOption("key").orElseThrow().widgetHint());

        Option<?> flags = root.findOption("flags").orElseThrow();
        assertEquals(WidgetHint.CHECKBOXES, flags.widgetHint());
        assertEquals(List.of(Flag.A, Flag.C), flags.get());

        // decorations keep their place among the options
        List<fr.heetstudio.confee.core.Section.Placed> decorations = root.decorations();
        assertEquals(2, decorations.size());
        assertEquals(Decoration.Kind.HEADER, decorations.get(0).decoration().kind());
        assertEquals(0, decorations.get(0).index());
        assertEquals(Decoration.Kind.SEPARATOR, decorations.get(1).decoration().kind());
        assertEquals(List.of("showHud", "item", "blocks", "name", "amount", "key", "flags"),
                root.children().subList(0, decorations.get(1).index()).stream().map(Node::key).toList());

        assertEquals("minecraft:feather", ((fr.heetstudio.confee.core.Section) root.find("dash").orElseThrow()).icon());
        assertEquals("minecraft:compass", ((fr.heetstudio.confee.core.Section) root.find("nested").orElseThrow()).icon());

        Layout layout = handle.schema().layout();
        assertEquals(480, layout.panelWidth());
        assertEquals(Layout.DEFAULT_PREVIEW_WIDTH, layout.previewWidth());
        assertEquals(Layout.Sidebar.HIDDEN, layout.sidebar());
        assertEquals("custom:textures/gui/banner.png", layout.banner());
        assertEquals(32, layout.bannerHeight());
    }

    @Test
    void enumSetFieldRoundTrips() throws Exception {
        ConfeeConfig<CustomConfig> config = fr.heetstudio.confee.api.Confee.register(CustomConfig.class);
        Path file = dir.resolve("custom.toml");
        String text = Files.readString(file);
        assertTrue(text.contains("flags = [\"A\", \"C\"]"), text);
        assertFalse(text.contains("title"), "decorations are not written: " + text);

        // duplicates and order in the file are cleaned up
        Files.writeString(file, text.replace("flags = [\"A\", \"C\"]", "flags = [\"D\", \"B\", \"D\"]"));
        config.reload();
        assertEquals(EnumSet.of(Flag.B, Flag.D), config.get().flags);
    }

    @Test
    void everyDeclarationMistakeIsReportedAtOnceWithAFix() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> AnnotationConfigs.create(Mistakes.class));
        String message = e.getMessage();
        assertTrue(message.contains("2 problem(s)"), message);
        assertTrue(message.contains("noRange") && message.contains("Fix: add @Range"), message);
        assertTrue(message.contains("notText") && message.contains("@Multiline"), message);
    }

    @Test
    void unknownConditionSuggestsTheRightPath() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> AnnotationConfigs.create(Typo.class));
        assertTrue(e.getMessage().contains("did you mean 'nested.x'"), e.getMessage());
        assertTrue(e.getMessage().contains("Fix:"), e.getMessage());
    }

    @Test
    void builderOffersTheSameFeatures() {
        ConfigBuilder b = fr.heetstudio.confee.api.Confee.builder("built")
                .layout(Layout.builder().previewWidth(200).background("built:textures/gui/bg.png").build());
        b.header("Top");
        ConfigValue<String> item = b.item("item", "minecraft:apple").description("An item").build();
        b.block("block", "minecraft:stone").build();
        ConfigValue<List<Flag>> flags = b.enumSet("flags", Flag.class, List.of(Flag.D, Flag.A, Flag.A)).build();
        b.keybind("key", "key.mouse.right").build();
        b.intValue("amount", 3).range(0, 10).stepper(2).build();
        b.string("name", "x").suggestions("x", "y").build();
        b.separator();
        b.category("dash").icon("minecraft:feather").bool("enabled", true).build();
        b.button("Wiki", () -> { });
        ConfeeConfig<Void> config = b.build();

        fr.heetstudio.confee.core.Section root = ConfeeRegistry.get("built", null).orElseThrow().schema().root();
        assertEquals(List.of(Flag.A, Flag.D), flags.get());
        assertEquals("minecraft:item", ((Option<?>) item).registry());
        assertEquals("An item", ((Option<?>) item).description());
        assertEquals("minecraft:block", root.findOption("block").orElseThrow().registry());
        assertEquals(WidgetHint.KEYBIND, root.findOption("key").orElseThrow().widgetHint());
        assertEquals(WidgetHint.STEPPER, root.findOption("amount").orElseThrow().widgetHint());
        assertEquals(List.of("x", "y"), List.copyOf(root.findOption("name").orElseThrow().suggestions().suggestions()));
        assertEquals(List.of(0, 6, 7), root.decorations().stream().map(fr.heetstudio.confee.core.Section.Placed::index).toList());
        assertEquals("minecraft:feather", ((fr.heetstudio.confee.core.Section) root.find("dash").orElseThrow()).icon());
        assertEquals(200, ConfeeRegistry.get("built", null).orElseThrow().schema().layout().previewWidth());
        assertNotNull(config);
    }

    @Test
    void wrongTypesAreRefusedWithAClearMessage() {
        ConfigBuilder b = fr.heetstudio.confee.api.Confee.builder("wrong");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> b.intValue("n", 1).item().build());
        assertTrue(e.getMessage().contains("@Item"), e.getMessage());
        e = assertThrows(IllegalStateException.class, () -> b.bool("b", true).stepper(1).build());
        assertTrue(e.getMessage().contains("@Stepper"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Layout.builder().panelWidth(50));
    }
}

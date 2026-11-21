package fr.heetstudio.confee.core;

import fr.heetstudio.confee.api.OnFileError;
import fr.heetstudio.confee.api.Preview;
import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.api.ValueValidator;
import fr.heetstudio.confee.api.annotation.Config;
import fr.heetstudio.confee.api.annotation.ItemStack;
import fr.heetstudio.confee.api.annotation.Section;
import fr.heetstudio.confee.api.annotation.Validator;
import fr.heetstudio.confee.ui.Suggestions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** {@code @ItemStack}, {@code OnFileError.KEEP}, section previews and the small helpers of 0.3.0. */
class StackAndKeepTest {

    @TempDir
    Path dir;
    ConfeeEnvironment env;

    @BeforeEach
    void setUp() {
        ConfeeRegistry.clear();
        env = new ConfeeEnvironment(dir, true, true);
    }

    // ------------------------------------------------------------------ StackText

    @Test
    void readsEveryFormAndWritesTheNativeOne() {
        assertEquals(new StackText("minecraft:diamond", 1, null), StackText.parse("minecraft:diamond"));
        assertEquals(new StackText("minecraft:diamond", 3, null), StackText.parse("{id:\"minecraft:diamond\",Count:3b}"));
        assertEquals(new StackText("minecraft:diamond", 5, null), StackText.parse("{id:'minecraft:diamond',count:5}"));
        StackText withComponents = StackText.parse("{id:\"minecraft:iron_sword\",count:1,components:{\"minecraft:damage\":3}}");
        assertEquals("{\"minecraft:damage\":3}", withComponents.extra());
        assertEquals("{id:\"minecraft:iron_sword\",count:1,components:{\"minecraft:damage\":3}}", withComponents.write());
        // the form of 1.20.4 is read and written in the form of this version
        assertEquals("{id:\"minecraft:diamond\",count:3}", StackText.parse("{id:\"minecraft:diamond\",Count:3b}").write());
    }

    @Test
    void refusesBrokenStacks() {
        assertThrows(IllegalArgumentException.class, () -> StackText.parse(""));
        assertThrows(IllegalArgumentException.class, () -> StackText.parse("{id:\"minecraft:diamond\""));
        assertThrows(IllegalArgumentException.class, () -> StackText.parse("{Count:1b}"));
        assertThrows(IllegalArgumentException.class, () -> StackText.parse("{id:\"minecraft:diamond\",Count:0b}"));
        assertThrows(IllegalArgumentException.class, () -> StackText.parse("Not An Id"));
        assertNull(StackText.extraProblem("{Damage:3}"));
        assertNotNull(StackText.extraProblem("{Damage:"));
    }

    // ------------------------------------------------------------------ @ItemStack and OnFileError.KEEP

    public static class NoTypo implements ValueValidator<String> {
        @Override
        public String validate(String value) {
            return value.contains("typo") ? "keepmod.error.typo" : null;
        }
    }

    @Config(modId = "keepmod")
    public static class Loot {
        @ItemStack
        public List<String> strict = new ArrayList<>(List.of("minecraft:apple"));
        @ItemStack(onFileError = OnFileError.KEEP)
        public List<String> kept = new ArrayList<>(List.of("minecraft:apple"));
        @ItemStack(maxCount = 16)
        public String single = "{id:\"minecraft:egg\",Count:1b}";
        @Validator(value = NoTypo.class, onFileError = OnFileError.KEEP)
        public String note = "fine";
    }

    @Test
    void brokenStackUsesTheDefaultUnlessKept() throws Exception {
        Files.writeString(dir.resolve("keepmod.toml"), String.join("\n",
                "strict = [\"{id:\\\"minecraft:diamond\\\",Count:2b}\", \"{id:broken\"]",
                "kept = [\"{id:\\\"minecraft:diamond\\\",Count:2b}\", \"{id:broken\"]",
                "single = \"{id:\\\"minecraft:egg\\\",Count:40b}\"",
                "note = \"a typo\"",
                ""));
        ConfigHandle<Loot> handle = AnnotationConfigs.create(Loot.class, env);
        handle.load();
        Loot loot = handle.get();
        assertEquals(List.of("minecraft:apple"), loot.strict, "one broken stack: the default list");
        assertEquals(List.of("{id:\"minecraft:diamond\",Count:2b}", "{id:broken"), loot.kept, "KEEP: kept as written");
        assertEquals("{id:\"minecraft:egg\",Count:1b}", loot.single, "above maxCount: the default");
        assertEquals("a typo", loot.note, "KEEP on @Validator: kept as written");
        handle.save();
        assertTrue(Files.readString(dir.resolve("keepmod.toml")).contains("{id:broken"), "a kept value stays in the file");
    }

    @Test
    void keptValueIsStillRefusedThroughTheApiAndTheScreen() {
        ConfigHandle<Loot> handle = AnnotationConfigs.create(Loot.class, env);
        Option<?> note = handle.schema().root().findOption("note").orElseThrow();
        Validated<?> check = note.validate("another typo");
        assertEquals(Validated.Status.KEPT, check.status());
        assertFalse(check.isOk());
        assertEquals("keepmod.error.typo", check.reason());
        @SuppressWarnings("unchecked")
        Option<String> typed = (Option<String>) note;
        typed.set("one more typo");
        assertEquals("fine", typed.get(), "ConfigValue.set refuses a kept value");
        Option<?> kept = handle.schema().root().findOption("kept").orElseThrow();
        assertEquals("confee.error.stack", kept.validateElement("{id:").reason());
    }

    // ------------------------------------------------------------------ section preview, helpers

    @Section
    @fr.heetstudio.confee.api.annotation.Preview(text = "Section preview")
    public static class Outline {
        public boolean rare = true;
        @fr.heetstudio.confee.api.annotation.Preview(text = "Own preview")
        public boolean ultra = true;
    }

    @Config(modId = "previewmod")
    public static class WithSection {
        public Outline outline = new Outline();
        public boolean other = true;
    }

    @Test
    void sectionPreviewIsUsedByItsOptionsWithoutOne() {
        ConfigHandle<WithSection> handle = AnnotationConfigs.create(WithSection.class, env);
        fr.heetstudio.confee.core.Section root = handle.schema().root();
        Preview rare = root.previewOf(root.findOption("outline.rare").orElseThrow());
        Preview ultra = root.previewOf(root.findOption("outline.ultra").orElseThrow());
        assertEquals("Section preview", rare.text());
        assertEquals("Own preview", ultra.text());
        assertNull(root.previewOf(root.findOption("other").orElseThrow()));
    }

    @Test
    void themeMixAndDimmed() {
        assertEquals(0xFF808080, Theme.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0xFFFFFFFF, Theme.mix(0xFF000000, 0xFFFFFFFF, 1f));
        assertEquals(0xFF000000, Theme.mix(0xFF000000, 0xFFFFFFFF, -3f));
        Theme theme = Theme.DEFAULT;
        assertEquals(Theme.mix(0xFFFFFFFF, theme.panelBg(), 0.55f), theme.dimmed(0xFFFFFFFF));
    }

    @Test
    void searchFindsEntriesByTheirName() {
        List<String> ids = List.of("minecraft:diamond_sword", "minecraft:stone");
        Map<String, String> names = Map.of("minecraft:diamond_sword", "épée en diamant", "minecraft:stone", "roche");
        assertEquals(List.of("minecraft:diamond_sword"), Suggestions.filter(ids, "épée", 10, names::get));
        assertEquals(List.of("minecraft:stone"), Suggestions.filter(ids, "roch", 10, names::get));
        assertEquals(List.of("minecraft:stone"), Suggestions.filter(ids, "stone", 10, names::get));
    }
}

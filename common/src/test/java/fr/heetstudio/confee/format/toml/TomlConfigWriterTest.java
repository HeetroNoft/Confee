package fr.heetstudio.confee.format.toml;

import fr.heetstudio.confee.api.ConfigFormat;
import fr.heetstudio.confee.api.ConfigScope;
import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.core.ConfigSchema;
import fr.heetstudio.confee.core.ConfigSource;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.OptionType;
import fr.heetstudio.confee.core.Section;
import fr.heetstudio.confee.core.WidgetHint;
import fr.heetstudio.confee.format.AtomicFiles;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.SchemaBinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 7.3: files owned by Confee. */
class TomlConfigWriterTest {

    enum Difficulty { EASY, NORMAL, HARD }

    private static ConfigSchema schema() {
        Section root = Section.root(null);
        root.add(Option.<Boolean>builder("showHud", OptionType.BOOLEAN)
                .defaultValue(true).comment("Show the mod HUD").build());
        Section dash = new Section("dash", null, "Dash settings");
        root.add(dash);
        dash.add(Option.<Double>builder("distance", OptionType.DOUBLE).defaultValue(4.0).range(1.0, 10.0)
                .step(0.5).widgetHint(WidgetHint.SLIDER).comment("Dash distance, in blocks").build());
        dash.add(Option.<Difficulty>builder("difficulty", OptionType.ENUM)
                .enumClass(Difficulty.class).defaultValue(Difficulty.NORMAL).build());
        root.add(Option.<Integer>builder("particleCount", OptionType.INT).defaultValue(20)
                .requiresRestart(true).build());
        root.add(Option.<List<String>>builder("blacklist", OptionType.LIST)
                .elementType(OptionType.STRING).defaultValue(List.of("minecraft:dirt")).build());
        root.add(Option.<Integer>builder("tint", OptionType.COLOR).defaultValue(0xFF2D7DD2).build());
        return new ConfigSchema("examplemod", null, ConfigScope.COMMON, ConfigFormat.TOML,
                Path.of("examplemod.toml"), Theme.DEFAULT, ConfigSource.CONFEE, root);
    }

    @Test
    void writesCommentedFileInDeclarationOrder() {
        String expected = String.join("\n",
                "# Show the mod HUD",
                "showHud = true",
                "",
                "particleCount = 20",
                "",
                "blacklist = [\"minecraft:dirt\"]",
                "",
                "tint = \"#2D7DD2\"",
                "",
                "# Dash settings",
                "[dash]",
                "# Dash distance, in blocks",
                "# Range: 1.0 ~ 10.0",
                "distance = 4.0",
                "",
                "# Allowed: EASY, NORMAL, HARD",
                "difficulty = \"NORMAL\"",
                "");
        assertEquals(expected, TomlConfigWriter.write(schema(), "\n", null));
    }

    @Test
    void writtenFileLoadsBackIntoTheSameValues() throws Exception {
        ConfigSchema source = schema();
        Option<Double> distance = cast(source.root().findOption("dash.distance").orElseThrow());
        distance.set(7.5);
        Option<List<String>> blacklist = cast(source.root().findOption("blacklist").orElseThrow());
        blacklist.set(List.of("a:b", "c \"quoted\""));
        String text = TomlConfigWriter.write(source, "\r\n", null);

        ConfigSchema target = schema();
        SchemaBinder.Report report = SchemaBinder.load(target.root(), TomlParser.parse(text), "test.toml");
        assertFalse(report.needsRewrite(), report.toString());
        target.root().forEachOption(o ->
                assertEquals(source.root().findOption(o.path()).orElseThrow().get(), o.get(), o.path()));
    }

    @Test
    void unknownKeysAreKept() throws Exception {
        String old = String.join("\n",
                "showHud = false",
                "# was removed",
                "oldOption = 5",
                "[dash]",
                "distance = 2.0",
                "legacy = 'x'",
                "[gone]",
                "k = [1, 2]",
                "");
        ConfigSchema s = schema();
        ParsedDocument previous = TomlParser.parse(old);
        SchemaBinder.load(s.root(), previous, "test.toml");
        String out = TomlConfigWriter.write(s, "\n", previous);

        ParsedDocument reparsed = TomlParser.parse(out);
        assertEquals(5L, TomlParserTest.get(reparsed, "oldOption").value());
        assertEquals("was removed", TomlParserTest.get(reparsed, "oldOption").comment());
        assertEquals("x", TomlParserTest.get(reparsed, "dash", "legacy").value());
        assertEquals(List.of(1L, 2L), TomlParserTest.get(reparsed, "gone", "k").value());
        assertEquals(false, TomlParserTest.get(reparsed, "showHud").value());
        assertEquals(2.0, TomlParserTest.get(reparsed, "dash", "distance").value());
    }

    @Test
    void loadReportsMissingCorrectedAndReplacedValues() throws Exception {
        String file = "showHud = \"yes\"\n[dash]\ndistance = 50.0\n";
        ConfigSchema s = schema();
        SchemaBinder.Report report = SchemaBinder.load(s.root(), TomlParser.parse(file), "test.toml");
        assertEquals(List.of("showHud"), report.replaced());
        assertEquals(List.of("dash.distance"), report.corrected());
        assertTrue(report.missing().contains("particleCount"));
        assertEquals(true, s.root().findOption("showHud").orElseThrow().get());
        assertEquals(10.0, s.root().findOption("dash.distance").orElseThrow().get());
    }

    @Test
    void atomicWriteReplacesTheFileAndLeavesNoTemporaryFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sub").resolve("examplemod.toml");
        AtomicFiles.write(file, "a = 1\n".getBytes());
        AtomicFiles.write(file, "a = 2\n".getBytes());
        assertEquals("a = 2\n", Files.readString(file));
        try (var files = Files.list(file.getParent())) {
            assertEquals(1, files.count());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> Option<T> cast(Option<?> option) {
        return (Option) option;
    }
}

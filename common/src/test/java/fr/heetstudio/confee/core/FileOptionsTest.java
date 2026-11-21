package fr.heetstudio.confee.core;

import fr.heetstudio.confee.api.ConfigFormat;
import fr.heetstudio.confee.api.ConfigValue;
import fr.heetstudio.confee.api.annotation.Comment;
import fr.heetstudio.confee.api.annotation.Config;
import fr.heetstudio.confee.api.annotation.Range;
import fr.heetstudio.confee.api.builder.ConfigBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** {@code writeDefaults} and {@code legacyFiles} (file options of a Confee config). */
class FileOptionsTest {

    enum Mode { FAST, SAFE }

    @TempDir
    Path dir;
    ConfeeEnvironment env;

    @BeforeEach
    void setUp() {
        ConfeeRegistry.clear();
        env = new ConfeeEnvironment(dir, true, true);
    }

    @Config(modId = "defaultsmod", writeDefaults = true)
    public static class WithDefaults {
        @Comment("Health factor")
        @Range(min = 0, max = 10)
        public double factor = 2.5;
        public Mode mode = Mode.SAFE;
        public String name = "say \"hi\"";
        public List<String> tags = List.of("a", "b");
        public List<Integer> empty = List.of();
    }

    @Test
    void defaultLineIsWrittenUnderEachOption() throws Exception {
        ConfigHandle<WithDefaults> handle = AnnotationConfigs.create(WithDefaults.class, env);
        handle.load();
        String expected = String.join("\n",
                "#: Health factor",
                "#: Range: 0.0 ~ 10.0",
                "#: Default: 2.5",
                "factor = 2.5",
                "",
                "#: Allowed: FAST, SAFE",
                "#: Default: SAFE",
                "mode = \"SAFE\"",
                "",
                "#: Default: \"say \\\"hi\\\"\"",
                "name = \"say \\\"hi\\\"\"",
                "",
                "#: Default: [\"a\", \"b\"]",
                "tags = [\"a\", \"b\"]",
                "",
                "#: Default: []",
                "empty = []",
                "");
        assertEquals(expected, Files.readString(dir.resolve("defaultsmod.toml")));
    }

    @Test
    void defaultLineStaysTheDefaultWhenTheValueChanges() throws Exception {
        Files.writeString(dir.resolve("defaultsmod.toml"), "factor = 7.0\n");
        ConfigHandle<WithDefaults> handle = AnnotationConfigs.create(WithDefaults.class, env);
        handle.load();
        handle.save();
        String text = Files.readString(dir.resolve("defaultsmod.toml"));
        assertTrue(text.contains("#: Default: 2.5\nfactor = 7.0\n"), text);
    }

    @Test
    void defaultLineInJsonAndYaml() throws Exception {
        ConfigBuilder json = ConfigBuilder.create("jsonmod", env).format(ConfigFormat.JSON5).writeDefaults();
        json.intRange("count", 3, 0, 9).build();
        json.build();
        assertTrue(Files.readString(dir.resolve("jsonmod.json5")).contains("//: Default: 3"));
        ConfigBuilder yaml = ConfigBuilder.create("yamlmod", env).format(ConfigFormat.YAML).writeDefaults();
        yaml.bool("on", true).build();
        yaml.build();
        assertTrue(Files.readString(dir.resolve("yamlmod.yml")).contains("#: Default: true"));
    }

    @Test
    void noDefaultLineWithoutTheOption() throws Exception {
        ConfigBuilder b = ConfigBuilder.create("plainmod", env);
        b.intRange("count", 3, 0, 9).build();
        b.build();
        assertFalse(Files.readString(dir.resolve("plainmod.toml")).contains("Default"));
    }

    // ------------------------------------------------------------------ legacy files

    private ConfigValue<Integer> legacyConfig(String... legacy) {
        ConfigBuilder b = ConfigBuilder.create("legacymod", env).name("common").legacyFiles(legacy);
        ConfigValue<Integer> count = b.intRange("count", 3, 0, 9).comment("How many").build();
        b.build();
        return count;
    }

    @Test
    void legacyFileIsMovedWithItsValuesAndConfeeCommentsOnly() throws Exception {
        Files.writeString(dir.resolve("Old-Common.toml"), "# Old comment\n# Default: 3\ncount = 7\n# unknown key\nremoved = true\n");
        ConfigValue<Integer> count = legacyConfig("Old-Common.toml");
        assertEquals(7, count.get());
        assertFalse(Files.exists(dir.resolve("Old-Common.toml")));
        String text = Files.readString(dir.resolve("legacymod-common.toml"));
        assertTrue(text.startsWith("#: How many\n#: Range: 0 ~ 9\ncount = 7\n"), text);
        assertFalse(text.contains("Old comment"), text);
        assertTrue(text.contains("removed = true"), "unknown keys are kept: " + text);
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(1, files.count(), "no temporary file left");
        }
    }

    @Test
    void legacyFileOnlyDifferingByCaseIsRenamed() throws Exception {
        Files.writeString(dir.resolve("LegacyMod-Common.toml"), "count = 5\n");
        ConfigValue<Integer> count = legacyConfig("LegacyMod-Common.toml");
        assertEquals(5, count.get());
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of("legacymod-common.toml"), files.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void existingConfeeFileWinsOverTheLegacyFile() throws Exception {
        Files.writeString(dir.resolve("legacymod-common.toml"), "count = 2\n");
        Files.writeString(dir.resolve("old.toml"), "count = 8\n");
        ConfigValue<Integer> count = legacyConfig("old.toml");
        assertEquals(2, count.get());
        assertTrue(Files.exists(dir.resolve("old.toml")), "the legacy file is left alone");
    }

    @Test
    void firstLegacyFileFoundIsUsedAndSubfoldersWork() throws Exception {
        Files.createDirectories(dir.resolve("legacymod"));
        Files.writeString(dir.resolve("legacymod/settings.toml"), "count = 4\n");
        ConfigValue<Integer> count = legacyConfig("missing.toml", "legacymod/settings.toml");
        assertEquals(4, count.get());
        assertFalse(Files.exists(dir.resolve("legacymod/settings.toml")));
    }

    @Test
    void unreadableLegacyFileIsLeftAndDefaultsAreUsed() throws Exception {
        Files.writeString(dir.resolve("old.toml"), "count = = =\n");
        ConfigValue<Integer> count = legacyConfig("old.toml");
        assertEquals(3, count.get());
        assertTrue(Files.exists(dir.resolve("old.toml")));
        assertTrue(Files.exists(dir.resolve("legacymod-common.toml")));
        assertFalse(Files.exists(dir.resolve("old.toml.confee-migrating")));
    }
}

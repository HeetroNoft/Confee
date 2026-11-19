package fr.heetstudio.confee.format;

import fr.heetstudio.confee.api.ConfigFormat;
import fr.heetstudio.confee.api.ConfigValue;
import fr.heetstudio.confee.api.builder.CategoryBuilder;
import fr.heetstudio.confee.api.builder.ConfigBuilder;
import fr.heetstudio.confee.core.ConfeeEnvironment;
import fr.heetstudio.confee.core.ConfeeRegistry;
import fr.heetstudio.confee.core.ConfigHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A Confee config in every format a Confee config can use (spec 7.1 / 7.3). */
class ConfeeFormatsTest {

    enum Mode { FAST, SLOW }

    @TempDir
    Path dir;

    @BeforeEach
    void setUp() {
        ConfeeRegistry.clear();
    }

    private record Values(ConfigHandle<?> handle, ConfigValue<Boolean> flag, ConfigValue<Integer> count,
                          ConfigValue<Double> ratio, ConfigValue<String> title, ConfigValue<Mode> mode,
                          ConfigValue<List<String>> list, ConfigValue<Integer> color,
                          ConfigValue<java.util.Map<String, Double>> map, ConfigValue<List<Mode>> modes,
                          ConfigValue<List<java.util.Map<String, Object>>> objects,
                          fr.heetstudio.confee.api.builder.ObjectSpec spec) {
    }

    private Values declare(ConfigFormat format, String modId) {
        ConfigBuilder b = ConfigBuilder.create(modId, new ConfeeEnvironment(dir, true, true)).format(format)
                .comment("Format test");
        ConfigValue<Boolean> flag = b.bool("flag", true).comment("A flag").build();
        ConfigValue<Integer> count = b.intRange("count", 5, 0, 10).build();
        CategoryBuilder cat = b.category("cat").comment("A category");
        ConfigValue<Double> ratio = cat.doubleValue("ratio", 0.5).build();
        ConfigValue<String> title = cat.string("title", "123").build();
        ConfigValue<Mode> mode = cat.enumValue("mode", Mode.FAST).build();
        ConfigValue<List<String>> list = b.list("list", String.class, List.of("a,b", "c\\d", "e")).build();
        ConfigValue<Integer> color = b.color("color", 0xFF112233).build();
        java.util.Map<String, Double> defaults = new java.util.LinkedHashMap<>();
        defaults.put("plains", 1.0);
        defaults.put("deep dark", 2.5);
        ConfigValue<java.util.Map<String, Double>> map = cat.map("biomes", Double.class, defaults).build();
        ConfigValue<List<Mode>> modes = b.list("modes", Mode.class, List.of(Mode.FAST, Mode.SLOW)).build();
        fr.heetstudio.confee.api.builder.ObjectSpec spec = fr.heetstudio.confee.api.builder.ObjectSpec.builder()
                .string("id", "new").doubleValue("speed", 1.0).enumValue("mode", Mode.FAST).build();
        ConfigValue<List<java.util.Map<String, Object>>> objects = b.objectList("presets", spec,
                List.of(spec.element("id", "fast", "speed", 2.5), spec.element("id", "123"))).build();
        return new Values((ConfigHandle<?>) b.build(), flag, count, ratio, title, mode, list, color, map, modes, objects, spec);
    }

    @ParameterizedTest
    @EnumSource(ConfigFormat.class)
    void writtenFileLoadsBackIntoTheSameValues(ConfigFormat format) throws Exception {
        Values v = declare(format, "fmt");
        Path file = dir.resolve("fmt." + format.extension());
        assertTrue(Files.exists(file), "file created");
        v.flag().set(false);
        v.count().set(9);
        v.ratio().set(2.25);
        v.title().set("true");
        v.mode().set(Mode.SLOW);
        v.list().set(List.of("x, y", "z"));
        v.color().set(0xFFABCDEF);
        java.util.Map<String, Double> biomes = new java.util.LinkedHashMap<>();
        biomes.put("desert", 0.5);
        biomes.put("a.b c", 3.0);
        v.map().set(biomes);
        v.modes().set(List.of(Mode.SLOW));
        v.objects().set(List.of(v.spec().element("id", "true", "speed", 4.0, "mode", Mode.SLOW)));
        v.handle().save();
        // defaults come back when reading the defaults file: check the default object list survives too
        String text = Files.readString(dir.resolve("fmt." + format.extension()));
        assertFalse(text.isEmpty());

        ConfeeRegistry.clear();
        Values again = declare(format, "fmt");
        assertEquals(false, again.flag().get());
        assertEquals(9, again.count().get());
        assertEquals(2.25, again.ratio().get());
        assertEquals("true", again.title().get(), "a text that looks like a boolean stays text");
        assertEquals(Mode.SLOW, again.mode().get());
        assertEquals(List.of("x, y", "z"), again.list().get());
        assertEquals(0xFFABCDEF, again.color().get());
        assertEquals(biomes, again.map().get());
        assertEquals(List.of(Mode.SLOW), again.modes().get());
        assertEquals(List.of(v.spec().element("id", "true", "speed", 4.0, "mode", Mode.SLOW)), again.objects().get());
    }

    @ParameterizedTest
    @EnumSource(ConfigFormat.class)
    void defaultCollectionsAndEmptyOnesRoundTrip(ConfigFormat format) throws Exception {
        Values v = declare(format, "col");
        ConfeeRegistry.clear();
        Values defaults = declare(format, "col");
        assertEquals(v.map().getDefault(), defaults.map().get());
        assertEquals(v.objects().getDefault(), defaults.objects().get(), "text field '123' stays text");
        defaults.objects().set(List.of());
        defaults.modes().set(List.of());
        defaults.handle().save();
        ConfeeRegistry.clear();
        Values empty = declare(format, "col");
        assertEquals(List.of(), empty.objects().get(), "an emptied list does not get its defaults back");
        assertEquals(List.of(), empty.modes().get());
    }

    @ParameterizedTest
    @EnumSource(value = ConfigFormat.class, names = {"JSON5", "YAML", "PROPERTIES"})
    void commentsAndHintsAreWritten(ConfigFormat format) throws Exception {
        declare(format, "cmt");
        String text = Files.readString(dir.resolve("cmt." + format.extension()));
        assertTrue(text.contains("A flag"), text);
        assertTrue(text.contains("Range: 0 ~ 10"), text);
        assertTrue(text.contains("Allowed: FAST, SLOW"), text);
    }

    @ParameterizedTest
    @EnumSource(ConfigFormat.class)
    void unknownKeysAreKept(ConfigFormat format) throws Exception {
        Values v = declare(format, "unk");
        Path file = dir.resolve("unk." + format.extension());
        String extra = switch (format) {
            case TOML -> "legacy = 42\n";
            case JSON, JSON5 -> null;
            case YAML -> "legacy: 42\n";
            case PROPERTIES -> "legacy=42\n";
        };
        String text = Files.readString(file);
        if (extra == null) {
            text = text.replaceFirst("\\{", "{\n  \"legacy\": 42,");
        } else {
            text = text + extra;
        }
        Files.writeString(file, text);
        v.handle().reload();
        v.count().set(3);
        v.handle().save();
        String saved = Files.readString(file);
        assertTrue(saved.contains("legacy") && saved.contains("42"), saved);
        ConfeeRegistry.clear();
        assertEquals(3, declare(format, "unk").count().get());
    }

    // ------------------------------------------------------------------ hand-written comments (spec 5.3)

    private static String prefix(ConfigFormat format) {
        return format == ConfigFormat.JSON5 ? "//" : "#";
    }

    /** Inserts {@code line} right above the line that declares {@code key}. */
    private static String insertAbove(String text, String key, String line) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?m)^([ \\t]*)\"?" + java.util.regex.Pattern.quote(key) + "\"?\\s*[:=]").matcher(text);
        assertTrue(m.find(), key + " not found in\n" + text);
        return text.substring(0, m.start()) + m.group(1) + line + "\n" + text.substring(m.start());
    }

    private static List<String> changedLines(String before, String after) {
        List<String> a = List.of(before.split("\n", -1));
        List<String> b = List.of(after.split("\n", -1));
        assertEquals(a.size(), b.size(), "line count changed:\n" + after);
        List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                out.add(a.get(i) + " -> " + b.get(i));
            }
        }
        return out;
    }

    @ParameterizedTest
    @EnumSource(value = ConfigFormat.class, names = {"TOML", "JSON5", "YAML", "PROPERTIES"})
    void generatedCommentsCarryTheMarker(ConfigFormat format) throws Exception {
        declare(format, "mark");
        String text = Files.readString(dir.resolve("mark." + format.extension()));
        assertTrue(text.contains(prefix(format) + ": A flag"), text);
        assertTrue(text.contains(prefix(format) + ": Range: 0 ~ 10"), text);
    }

    @ParameterizedTest
    @EnumSource(ConfigFormat.class)
    void saveWithoutChangesLeavesTheFileIdentical(ConfigFormat format) throws Exception {
        Values v = declare(format, "same");
        Path file = dir.resolve("same." + format.extension());
        String text = Files.readString(file);
        if (format != ConfigFormat.JSON) {
            text = insertAbove(text, "count", prefix(format) + " my own note");
            text = text + prefix(format) + " trailing note\n";
        }
        Files.writeString(file, text);
        byte[] before = Files.readAllBytes(file);
        v.handle().reload();
        v.handle().save();
        assertArrayEquals(before, Files.readAllBytes(file), Files.readString(file));
    }

    @ParameterizedTest
    @EnumSource(value = ConfigFormat.class, names = {"TOML", "JSON5", "YAML", "PROPERTIES"})
    void handWrittenCommentsStayAndOnlyTheValueChanges(ConfigFormat format) throws Exception {
        Values v = declare(format, "hand");
        Path file = dir.resolve("hand." + format.extension());
        String text = insertAbove(Files.readString(file), "count", prefix(format) + " my own note");
        text = insertAbove(text, "flag", prefix(format) + " about the flag");
        Files.writeString(file, text);
        v.handle().reload();
        v.count().set(3);
        v.handle().save();
        String saved = Files.readString(file);
        assertTrue(saved.contains(prefix(format) + " my own note"), saved);
        assertTrue(saved.contains(prefix(format) + " about the flag"), saved);
        List<String> changed = changedLines(text, saved);
        assertEquals(1, changed.size(), changed.toString());
        assertTrue(changed.get(0).contains("5") && changed.get(0).contains("3"), changed.toString());
    }

    @ParameterizedTest
    @EnumSource(value = ConfigFormat.class, names = {"TOML", "JSON5", "YAML", "PROPERTIES"})
    void outdatedGeneratedCommentsAreRewritten(ConfigFormat format) throws Exception {
        Values v = declare(format, "old");
        Path file = dir.resolve("old." + format.extension());
        String p = prefix(format);
        String text = Files.readString(file).replace(p + ": A flag", p + ": An outdated description");
        text = insertAbove(text, "flag", p + " kept");
        Files.writeString(file, text);
        v.handle().reload();
        v.handle().save();
        String saved = Files.readString(file);
        assertTrue(saved.contains(p + ": A flag"), saved);
        assertFalse(saved.contains("outdated"), saved);
        assertTrue(saved.contains(p + " kept"), saved);
        assertEquals(1, changedLines(text, saved).size(), saved);
    }

    @ParameterizedTest
    @EnumSource(value = ConfigFormat.class, names = {"TOML", "JSON5", "YAML", "PROPERTIES"})
    void handWrittenCommentsSurviveANewOption(ConfigFormat format) throws Exception {
        Values v = declare(format, "grow");
        Path file = dir.resolve("grow." + format.extension());
        String p = prefix(format);
        String text = insertAbove(Files.readString(file), "count", p + " my own note");
        text = insertAbove(text, format == ConfigFormat.PROPERTIES ? "cat.ratio" : "ratio", p + " ratio note");
        // the new option is not in the file yet
        text = text.replaceFirst("(?m)^\\s*\"?flag\"?\\s*[:=].*\\R", "");
        Files.writeString(file, text);
        ConfeeRegistry.clear();
        Values again = declare(format, "grow");
        again.handle().save();
        String saved = Files.readString(file);
        assertTrue(saved.contains(p + " my own note"), saved);
        assertTrue(saved.contains(p + " ratio note"), saved);
        assertTrue(saved.contains("flag"), saved);
        ConfeeRegistry.clear();
        assertEquals(5, declare(format, "grow").count().get());
    }
}

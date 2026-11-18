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
}

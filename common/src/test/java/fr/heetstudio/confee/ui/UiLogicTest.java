package fr.heetstudio.confee.ui;

import fr.heetstudio.confee.api.ConfeeConfig;
import fr.heetstudio.confee.api.ConfigValue;
import fr.heetstudio.confee.api.builder.ConfigBuilder;
import fr.heetstudio.confee.core.ConfeeEnvironment;
import fr.heetstudio.confee.core.ConfeeRegistry;
import fr.heetstudio.confee.core.ConfigHandle;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.OptionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UiLogicTest {

    @TempDir
    Path dir;

    @BeforeEach
    void setUp() {
        ConfeeRegistry.clear();
    }

    @Test
    void humanizeKeys() {
        assertEquals("Dash Distance", ValueText.humanize("dashDistance"));
        assertEquals("Max Speed", ValueText.humanize("max_speed"));
        assertEquals("Hard", ValueText.humanize("HARD"));
        assertEquals("Very High", ValueText.humanize("VERY_HIGH"));
        assertEquals("Show HUD", ValueText.humanize("showHUD"));
        assertEquals("Level 2", ValueText.humanize("level2"));
        assertEquals("Internal Counter", ValueText.humanize("internal_counter"));
    }

    @Test
    void numbersForLabels() {
        assertEquals("4", ValueText.number(4.0));
        assertEquals("0.5", ValueText.number(0.5));
        assertEquals("0.8", ValueText.number(0.8f));
        assertEquals("20", ValueText.number(20));
    }

    @Test
    void sliderSnapping() {
        assertEquals(4.5, ValueText.snap(4.4, 1, 10, 0.5));
        assertEquals(0.3, ValueText.snap(0.1 + 0.2, 0, 1, 0.05));
        assertEquals(10.0, ValueText.snap(42, 1, 10, 0.5));
    }

    @Test
    void fieldParsingRefusesInvalidValues() {
        Option<Integer> volume = Option.<Integer>builder("volume", OptionType.INT).defaultValue(70).range(0, 100).build();
        assertEquals(42, ValueText.parse(volume, "42").value());
        assertEquals(ValueText.Problem.OUT_OF_RANGE, ValueText.parse(volume, "150").problem());
        assertEquals(ValueText.Problem.NOT_AN_INTEGER, ValueText.parse(volume, "4.5").problem());
        assertEquals(ValueText.Problem.NOT_A_NUMBER, ValueText.parse(volume, "abc").problem());

        Option<Double> ratio = Option.<Double>builder("ratio", OptionType.DOUBLE).defaultValue(1.0).build();
        assertEquals(2.5, ValueText.parse(ratio, "2.5").value());
        assertEquals(ValueText.Problem.NOT_A_NUMBER, ValueText.parse(ratio, "2.").problem());

        Option<Integer> color = Option.<Integer>builder("c", OptionType.COLOR).defaultValue(0xFFFFFFFF).build();
        assertEquals(0xFF112233, ValueText.parse(color, "#112233").value());
        assertEquals(ValueText.Problem.COLOR, ValueText.parse(color, "#12").problem());

        Option<String> code = Option.<String>builder("code", OptionType.STRING).defaultValue("a").pattern("[a-z]+").maxLength(4).build();
        assertEquals(ValueText.Problem.PATTERN, ValueText.parse(code, "A").problem());
        assertEquals(ValueText.Problem.MAX_LENGTH, ValueText.parse(code, "abcdef").problem());
    }

    @Test
    void numberFieldCharacters() {
        assertTrue(ValueText.isNumberText("-12.5", true));
        assertFalse(ValueText.isNumberText("12.5", false));
        assertFalse(ValueText.isNumberText("1e5", true));
    }

    @Test
    void sessionKeepsChangesPendingUntilCommit() throws Exception {
        ConfigBuilder b = ConfigBuilder.create("uitest", new ConfeeEnvironment(dir, true, true));
        ConfigValue<Integer> volume = b.intRange("volume", 70, 0, 100).build();
        ConfigValue<Boolean> fancy = b.bool("fancy", false).requiresRestart().build();
        List<String> events = new ArrayList<>();
        volume.addListener((o, n) -> events.add("listener " + o + "->" + n));
        ConfeeConfig<Void> config = b.build();
        config.onChange(() -> events.add("onChange"));
        ConfigHandle<?> handle = (ConfigHandle<?>) config;
        Option<Integer> volumeOption = cast(handle.schema().root().findOption("volume").orElseThrow());
        Option<Boolean> fancyOption = cast(handle.schema().root().findOption("fancy").orElseThrow());

        EditSession session = new EditSession(handle);
        session.set(volumeOption, 30);
        assertEquals(30, session.get(volumeOption));
        assertEquals(70, volume.get(), "not applied before save");
        assertTrue(session.isDirty());
        assertFalse(session.isDefault(volumeOption));

        session.set(volumeOption, 70);
        assertFalse(session.isDirty(), "back to the current value: nothing pending");

        session.set(volumeOption, 30);
        session.set(fancyOption, true);
        EditSession.Result result = session.commit();
        assertEquals(2, result.changed());
        assertTrue(result.restartRequired());
        assertEquals(30, volume.get());
        assertTrue(fancy.get());
        assertEquals(List.of("listener 70->30", "onChange"), events);
        assertTrue(Files.readString(dir.resolve("uitest.toml")).contains("volume = 30"));

        session.resetAllToDefault();
        assertEquals(70, session.get(volumeOption));
        assertEquals(30, volume.get(), "reset all is pending too");
        session.discard();
        assertFalse(session.isDirty());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> Option<T> cast(Option<?> option) {
        return (Option) option;
    }

    @Test
    void savingRemovesDuplicatesSilentlyUnlessAllowed() throws Exception {
        ConfigBuilder b = ConfigBuilder.create("dups", new ConfeeEnvironment(dir, true, true));
        ConfigValue<List<String>> blocks = b.list("blocks", String.class, List.of("minecraft:sand")).build();
        ConfigValue<List<Integer>> rolls = b.list("rolls", Integer.class, List.of(1)).allowDuplicates().build();
        ConfigHandle<?> handle = (ConfigHandle<?>) b.build();
        Option<List<String>> blocksOption = cast(handle.schema().root().findOption("blocks").orElseThrow());
        Option<List<Integer>> rollsOption = cast(handle.schema().root().findOption("rolls").orElseThrow());

        EditSession session = new EditSession(handle);
        session.set(blocksOption, List.of("minecraft:dirt", "minecraft:sand", "minecraft:dirt"));
        session.set(rollsOption, List.of(6, 6));
        assertEquals(3, session.get(blocksOption).size(), "the screen keeps what the player typed until saved");
        session.commit();
        assertEquals(List.of("minecraft:dirt", "minecraft:sand"), blocks.get());
        assertEquals(List.of(6, 6), rolls.get());
    }

    @Test
    void duplicatesInAFileAreRemovedOnLoad() throws Exception {
        java.nio.file.Files.writeString(dir.resolve("dupfile.toml"), "blocks = [\"a:b\", \"c:d\", \"a:b\"]\n");
        ConfigBuilder b = ConfigBuilder.create("dupfile", new ConfeeEnvironment(dir, true, true));
        ConfigValue<List<String>> blocks = b.list("blocks", String.class, List.of()).build();
        b.build();
        assertEquals(List.of("a:b", "c:d"), blocks.get());
    }
}

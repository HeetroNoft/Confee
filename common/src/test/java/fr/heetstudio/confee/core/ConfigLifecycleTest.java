package fr.heetstudio.confee.core;

import fr.heetstudio.confee.api.ConfeeConfig;
import fr.heetstudio.confee.api.ConfigScope;
import fr.heetstudio.confee.api.ConfigValue;
import fr.heetstudio.confee.api.builder.CategoryBuilder;
import fr.heetstudio.confee.api.builder.ConfigBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Builder API (spec 6.2) and file lifecycle (spec 5.2 / 5.3). */
class ConfigLifecycleTest {

    enum Difficulty { EASY, NORMAL, HARD }

    @TempDir
    Path dir;
    ConfeeEnvironment env;

    @BeforeEach
    void setUp() {
        ConfeeRegistry.clear();
        env = new ConfeeEnvironment(dir, true, true);
    }

    private record Example(ConfeeConfig<Void> config, ConfigValue<Boolean> showHud, ConfigValue<Double> distance,
                           ConfigValue<Difficulty> difficulty, ConfigValue<List<String>> blacklist) {
    }

    private Example example() {
        ConfigBuilder b = ConfigBuilder.create("examplemod", env).name("client").scope(ConfigScope.CLIENT)
                .accentColor(0xFF2D7DD2).comment("Example config");
        ConfigValue<Boolean> showHud = b.bool("showHud", true).comment("Show the mod HUD").build();
        CategoryBuilder dash = b.category("dash");
        ConfigValue<Double> distance = dash.doubleRange("distance", 4.0, 1.0, 10.0).slider(0.5).build();
        ConfigValue<Difficulty> difficulty = dash.enumValue("difficulty", Difficulty.NORMAL).build();
        ConfigValue<List<String>> blacklist = b.list("blacklist", String.class, List.of()).build();
        return new Example(b.build(), showHud, distance, difficulty, blacklist);
    }

    private Path file() {
        return dir.resolve("examplemod-client.toml");
    }

    @Test
    void builderCreatesTheFileWithDefaultsAndComments() throws Exception {
        Example ex = example();
        assertNull(ex.config().get());
        String expected = String.join("\n",
                "#: Example config",
                "",
                "#: Show the mod HUD",
                "showHud = true",
                "",
                "blacklist = []",
                "",
                "[dash]",
                "#: Range: 1.0 ~ 10.0",
                "distance = 4.0",
                "",
                "#: Allowed: EASY, NORMAL, HARD",
                "difficulty = \"NORMAL\"",
                "");
        assertEquals(expected, Files.readString(file()));
        assertEquals(0xFF2D7DD2, ConfeeRegistry.get("examplemod", "client").orElseThrow().schema().theme().accent());
    }

    @Test
    void existingFileIsLoadedAndValidated() throws Exception {
        Files.writeString(file(), "showHud = false\n[dash]\ndistance = -5.0\ndifficulty = \"hard\"\n");
        Example ex = example();
        assertFalse(ex.showHud().get());
        assertEquals(1.0, ex.distance().get());
        assertEquals(Difficulty.HARD, ex.difficulty().get());
        // loading alone does not rewrite the file (missing keys are added on the next save)
        assertFalse(Files.readString(file()).contains("blacklist"));
        ex.config().save();
        String saved = Files.readString(file());
        assertTrue(saved.contains("blacklist = []"));
        assertTrue(saved.contains("distance = 1.0"));
    }

    @Test
    void brokenFileIsBackedUpAndRecreated() throws Exception {
        String broken = "showHud = \"unterminated\n";
        Files.writeString(file(), broken);
        Example ex = example();
        assertTrue(ex.showHud().get());
        List<Path> backups;
        try (Stream<Path> files = Files.list(dir)) {
            backups = files.filter(p -> p.getFileName().toString().startsWith("examplemod-client.toml.broken-")).toList();
        }
        assertEquals(1, backups.size());
        assertEquals(broken, Files.readString(backups.get(0)));
        assertTrue(Files.readString(file()).contains("showHud = true"));
    }

    @Test
    void savePreservesCrlfBomAndUnknownKeys() throws Exception {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "showHud = false\r\nlegacy = 1\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, content, 0, 3);
        System.arraycopy(body, 0, content, 3, body.length);
        Files.write(file(), content);

        Example ex = example();
        ex.distance().set(6.0);
        ex.config().save();
        byte[] saved = Files.readAllBytes(file());
        assertEquals((byte) 0xEF, saved[0]);
        String text = new String(saved, 3, saved.length - 3, StandardCharsets.UTF_8);
        assertTrue(text.contains("legacy = 1\r\n"), text);
        assertTrue(text.contains("distance = 6.0\r\n"), text);
        assertFalse(text.replace("\r\n", "").contains("\n"), "only CRLF line breaks");
    }

    @Test
    void reloadNotifiesListeners() throws Exception {
        Example ex = example();
        List<String> changes = new ArrayList<>();
        ex.distance().addListener((o, n) -> changes.add(o + "->" + n));
        Files.writeString(file(), Files.readString(file()).replace("distance = 4.0", "distance = 8.5"));
        ex.config().reload();
        assertEquals(List.of("4.0->8.5"), changes);
    }

    @Test
    void onChangeCallbacksAreIsolated() {
        Example ex = example();
        List<String> calls = new ArrayList<>();
        ex.config().onChange(() -> { throw new RuntimeException("boom"); });
        ex.config().onChange(() -> calls.add("called"));
        ((ConfigHandle<?>) ex.config()).fireOnChange();
        assertEquals(List.of("called"), calls);
    }

    @Test
    void invalidBuilderOptionThrowsInDevAndIsDetachedInProduction() {
        ConfigBuilder dev = ConfigBuilder.create("devmod", env);
        assertThrows(IllegalStateException.class, () -> dev.intValue("x", 5).slider(1).build());

        ConfigBuilder prod = ConfigBuilder.create("prodmod", new ConfeeEnvironment(dir, false, true));
        ConfigValue<Integer> x = prod.intValue("x", 5).slider(1).build();
        assertEquals(5, x.get());
        ConfeeConfig<Void> config = prod.build();
        assertTrue(((ConfigHandle<?>) config).schema().root().findOption("x").isEmpty());
    }

    @Test
    void builderCannotBeUsedAfterBuild() {
        ConfigBuilder b = ConfigBuilder.create("once", env);
        b.build();
        assertThrows(IllegalStateException.class, () -> b.bool("late", true));
        assertThrows(IllegalStateException.class, b::build);
    }

    @Test
    void categoryLambdaFormWorks() {
        ConfigBuilder b = ConfigBuilder.create("lambda", env);
        List<ConfigValue<Integer>> holder = new ArrayList<>();
        b.category("a", c -> holder.add(c.intRange("n", 1, 0, 5).build()));
        b.build();
        assertEquals(1, holder.get(0).get());
        assertTrue(ConfeeRegistry.get("lambda", null).orElseThrow().schema().root().findOption("a.n").isPresent());
    }
}

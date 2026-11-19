package fr.heetstudio.confee.external;

import fr.heetstudio.confee.core.ConfigSource;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.OptionType;
import fr.heetstudio.confee.core.Section;
import fr.heetstudio.confee.ui.EditSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExternalConfigTest {

    @TempDir
    Path config;

    private static final List<ConfigDiscovery.ModInfo> MODS = List.of(
            new ConfigDiscovery.ModInfo("examplemod", "Example Mod"),
            new ConfigDiscovery.ModInfo("example_mod_addon", "Example Addon"),
            new ConfigDiscovery.ModInfo("xaerominimap", "Xaero's Minimap"),
            new ConfigDiscovery.ModInfo("sodium", "Sodium"));

    private Path file(String relative, String content) throws Exception {
        Path f = config.resolve(relative);
        Files.createDirectories(f.getParent());
        Files.writeString(f, content);
        return f.toAbsolutePath().normalize();
    }

    // ------------------------------------------------------------------ discovery (spec 8.2)

    @Test
    void discoveryMatchesNamesPrefixesFoldersAndDisplayNames() throws Exception {
        Path main = file("examplemod.toml", "a = 1\n");
        Path client = file("examplemod-client.toml", "a = 1\n");
        Path underscore = file("ExampleMod_server.json", "{}");
        Path addon = file("example-mod-addon.toml", "a = 1\n");
        Path folder = file("sodium/options.json", "{}");
        Path minimap = file("xaero-minimap.txt", "x:1\n");
        file("unrelated.toml", "a = 1\n");
        file("examplemod.toml.bak", "a = 1\n");
        file("examplemod.toml.broken-20240101-000000", "a = \n");
        file("confee/backups/examplemod.toml", "a = 1\n");
        Path binary = config.resolve("examplemod-cache.bin");
        Files.write(binary, new byte[]{1, 0, 2});

        Map<String, List<Path>> found = ConfigDiscovery.discover(config, MODS, Set.of(), Map.of(), Set.of());
        assertEquals(List.of(client, main, underscore).stream().sorted().toList(), found.get("examplemod"));
        assertEquals(List.of(addon), found.get("example_mod_addon"), "longest mod ID wins");
        assertEquals(List.of(folder), found.get("sodium"));
        assertEquals(List.of(minimap), found.get("xaerominimap"), "separators are ignored");
        assertEquals(4, found.size());
    }

    @Test
    void discoveryWalksModFoldersAtAnyDepth() throws Exception {
        Path top = file("sodium/options.json", "{}");
        Path deep = file("sodium/profiles/high/video.toml", "a = 1\n");
        file("sodium/profiles/high/video.toml.bak", "a = 1\n");
        file("unrelated/deep/inner.toml", "a = 1\n");

        Map<String, List<Path>> found = ConfigDiscovery.discover(config, MODS, Set.of(), Map.of(), Set.of());
        assertEquals(List.of(top, deep).stream().sorted().toList(), found.get("sodium"));
        assertEquals(1, found.size(), "folders of no mod are not walked");
    }

    @Test
    void discoveryRespectsExclusionsIgnoredModsAndManualMappings() throws Exception {
        Path main = file("examplemod.toml", "a = 1\n");
        Path odd = file("weird-name.cfg", "B:x=true\n");
        Map<String, List<Path>> found = ConfigDiscovery.discover(config, MODS, Set.of(main),
                Map.of("sodium", List.of("weird-name.cfg", "missing.toml")), Set.of("xaerominimap"));
        assertNull(found.get("examplemod"), "excluded (Confee or loader config)");
        assertEquals(List.of(odd), found.get("sodium"));
        assertFalse(found.containsKey("xaerominimap"));
    }

    @Test
    void oversizedFilesAreSkipped() throws Exception {
        file("examplemod.toml", "a = \"" + "x".repeat((int) ConfigDiscovery.MAX_SIZE) + "\"\n");
        assertTrue(ConfigDiscovery.discover(config, MODS, Set.of(), Map.of(), Set.of()).isEmpty());
    }

    // ------------------------------------------------------------------ raw file schema (spec 8.3)

    static final String SAMPLE = String.join("\n",
            "# Enable the HUD",
            "enabled = true",
            "count = 5",
            "ratio = 0.5",
            "name = 'Steve'",
            "color = \"#FF8800\"",
            "tags = [\"a\", \"b\"]",
            "when = 2024-01-01T10:00:00Z",
            "",
            "# Rendering options",
            "[render]",
            "distance = 12 # chunks",
            "[render.fog]",
            "start = 0.75",
            "",
            "[[presets]]",
            "id = \"fast\"",
            "");

    @SuppressWarnings("unchecked")
    private static <T> Option<T> option(Section root, String path) {
        return (Option<T>) root.findOption(path).orElseThrow(() -> new AssertionError("missing " + path));
    }

    @Test
    void typesAreInferredAndComplexValuesAreReadOnly() throws Exception {
        Path f = file("examplemod.toml", SAMPLE);
        ExternalConfig cfg = ExternalConfig.open("examplemod", f, config, true);
        assertEquals(ExternalConfig.Status.OK, cfg.status());
        assertEquals(ConfigSource.RAW_FILE, cfg.schema().source());
        Section root = cfg.root();
        assertEquals(OptionType.BOOLEAN, option(root, "enabled").type());
        assertEquals("Enable the HUD", option(root, "enabled").comment());
        assertEquals(OptionType.LONG, option(root, "count").type());
        assertEquals(5L, option(root, "count").get());
        assertEquals(OptionType.DOUBLE, option(root, "ratio").type());
        assertEquals(OptionType.LIST, option(root, "tags").type());
        assertTrue(option(root, "when").readOnly());
        assertEquals("chunks", option(root, "render.distance").comment());
        assertEquals(0.75, option(root, "render.fog.start").get());
        assertEquals("Rendering options", ((Section) root.find("render").orElseThrow()).comment());
        Section render = (Section) root.find("render").orElseThrow();
        assertEquals(List.of("distance", "fog"), render.children().stream().map(n -> n.key()).toList(),
                "file order: the sub-table comes after the parent's keys");
        Section presets = (Section) root.find("presets").orElseThrow();
        Section first = (Section) presets.children().get(0);
        assertTrue(((Option<?>) first.children().get(0)).readOnly(), "arrays of tables are read-only");
    }

    @Test
    void savingOnlyTouchesChangedValuesAndKeepsABackup() throws Exception {
        Path f = file("examplemod.toml", SAMPLE);
        ExternalConfig cfg = ExternalConfig.open("examplemod", f, config, true);
        EditSession session = new EditSession(cfg);
        session.set(option(cfg.root(), "count"), 42L);
        session.set(option(cfg.root(), "render.distance"), 16L);
        session.set(option(cfg.root(), "tags"), List.of("a", "b", "c"));
        session.commit();

        String expected = SAMPLE.replace("count = 5", "count = 42")
                .replace("distance = 12 # chunks", "distance = 16 # chunks")
                .replace("tags = [\"a\", \"b\"]", "tags = [\"a\", \"b\", \"c\"]");
        assertEquals(expected, Files.readString(f));
        Path backup = config.resolve("confee/backups/examplemod.toml");
        assertEquals(SAMPLE, Files.readString(backup), "original copy kept");

        // a second change does not touch the backup; original values come from it
        ExternalConfig again = ExternalConfig.open("examplemod", f, config, true);
        assertEquals(5L, option(again.root(), "count").getDefault(), "original value");
        assertEquals(42L, option(again.root(), "count").get());
        EditSession second = new EditSession(again);
        assertFalse(second.isDefault(option(again.root(), "count")));
        second.resetToDefault(option(again.root(), "count"));
        second.commit();
        assertTrue(Files.readString(f).contains("count = 5"));
        assertEquals(SAMPLE, Files.readString(backup));
    }

    @Test
    void noBackupWhenDisabledAndRestoreNeedsOne() throws Exception {
        Path f = file("examplemod.toml", SAMPLE);
        ExternalConfig cfg = ExternalConfig.open("examplemod", f, config, false);
        EditSession session = new EditSession(cfg);
        session.set(option(cfg.root(), "enabled"), false);
        session.commit();
        assertTrue(Files.readString(f).contains("enabled = false"));
        assertFalse(Files.exists(cfg.backupPath()));
        assertFalse(cfg.restoreOriginal());
    }

    @Test
    void restoreOriginalPutsTheCopyBack() throws Exception {
        Path f = file("sodium/options.toml", SAMPLE);
        ExternalConfig cfg = ExternalConfig.open("sodium", f, config, true);
        EditSession session = new EditSession(cfg);
        session.set(option(cfg.root(), "name"), "Alex");
        session.commit();
        assertTrue(Files.readString(f).contains("name = 'Alex'"), "quote style kept");
        assertTrue(cfg.hasBackup());
        assertEquals(config.resolve("confee/backups/sodium/options.toml"), cfg.backupPath());
        assertTrue(cfg.restoreOriginal());
        assertEquals(SAMPLE, Files.readString(f));
    }

    @Test
    void externalChangesAreDetected() throws Exception {
        Path f = file("examplemod.toml", SAMPLE);
        ExternalConfig cfg = ExternalConfig.open("examplemod", f, config, true);
        assertFalse(cfg.changedOnDisk());
        Files.writeString(f, SAMPLE + "extra = 1\n");
        Files.setLastModifiedTime(f, FileTime.fromMillis(System.currentTimeMillis() + 5000));
        assertTrue(cfg.changedOnDisk());
    }

    @Test
    void unreadableAndUnsupportedFilesDoNotThrow() throws Exception {
        ExternalConfig broken = ExternalConfig.open("examplemod", file("examplemod.toml", "a = \"oops\n"), config, true);
        assertEquals(ExternalConfig.Status.UNREADABLE, broken.status());
        assertNotNull(broken.problem());
        ExternalConfig txt = ExternalConfig.open("examplemod", file("examplemod.txt", "a:1"), config, true);
        assertEquals(ExternalConfig.Status.UNSUPPORTED_FORMAT, txt.status());
        assertEquals(".txt", txt.problem());
    }

    @Test
    void forgeLegacyBoundsBecomeRanges() throws Exception {
        Path f = file("examplemod/legacy.cfg", "general {\n    # Min: 0\n    # Max: 500\n    I:maxParticles=250\n}\n");
        ExternalConfig cfg = ExternalConfig.open("examplemod", f, config, true);
        Option<Long> max = option(cfg.root(), "general.maxParticles");
        assertEquals(0L, max.min());
        assertEquals(500L, max.max());
        assertEquals(fr.heetstudio.confee.core.Validated.Status.CORRECTED, max.validate(2500L).status());
        EditSession session = new EditSession(cfg);
        session.set(max, 300L);
        session.commit();
        assertEquals("general {\n    # Min: 0\n    # Max: 500\n    I:maxParticles=300\n}\n", Files.readString(f));
    }

    @Test
    void everyFormatIsEditedInPlace() throws Exception {
        Map<String, String> files = Map.of(
                "examplemod.json", "{\n  \"count\": 5\n}\n",
                "examplemod.yml", "count: 5\n",
                "examplemod.properties", "count=5\n",
                "examplemod.ini", "[main]\ncount=5\n");
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path f = file(entry.getKey(), entry.getValue());
            ExternalConfig cfg = ExternalConfig.open("examplemod", f, config, true);
            assertEquals(ExternalConfig.Status.OK, cfg.status(), entry.getKey());
            Option<Long> count = option(cfg.root(), entry.getKey().endsWith(".ini") ? "main.count" : "count");
            EditSession session = new EditSession(cfg);
            session.set(count, 6L);
            session.commit();
            assertEquals(entry.getValue().replace("5", "6"), Files.readString(f), entry.getKey());
        }
    }

    @Test
    void readableNames() {
        Path dir = config;
        assertNull(ExternalConfig.readableName("examplemod", dir.resolve("examplemod.toml"), dir));
        assertEquals("client", ExternalConfig.readableName("examplemod", dir.resolve("examplemod-client.toml"), dir));
        assertEquals("options", ExternalConfig.readableName("sodium", dir.resolve("sodium/options.toml"), dir));
        assertEquals("sodium/options.toml", ExternalConfig.relativePath(dir.resolve("sodium/options.toml"), dir));
    }
}

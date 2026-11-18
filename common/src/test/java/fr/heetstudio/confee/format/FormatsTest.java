package fr.heetstudio.confee.format;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 7.1 / 7.2 for every format besides TOML: positions, byte-identical round trip, one-value edits, styles. */
class FormatsTest {

    private static ParsedDocument parse(FormatAdapter adapter, String text) throws Exception {
        return adapter.parse(SourceText.decode(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static ParsedValue get(ParsedDocument doc, String... path) {
        return doc.find(List.of(path)).orElseThrow(() -> new AssertionError("missing " + String.join(".", path)));
    }

    /** Edits one value and checks that only its text changed and that it reads back. */
    private static String edit(FormatAdapter adapter, String text, Object value, String... path) throws Exception {
        ParsedDocument doc = parse(adapter, text);
        ParsedValue target = get(doc, path);
        String out = doc.edit(Map.of(target, value), adapter.values());
        String written = out.substring(target.start(), out.length() - (text.length() - target.end()));
        assertEquals(text.substring(0, target.start()) + written + text.substring(target.end()), out);
        ParsedValue again = get(parse(adapter, out), path);
        assertTrue(ParsedDocument.sameValue(again.value(), value), "reads back " + value + ", got " + again.value());
        assertEquals(target.kind(), again.kind(), "kind kept");
        return out;
    }

    private static void roundTrip(FormatAdapter adapter, String text) throws Exception {
        for (String t : List.of(text, text.replace("\n", "\r\n"))) {
            ParsedDocument doc = parse(adapter, t);
            assertEquals(t, doc.edit(Map.of(), adapter.values()));
        }
    }

    // ------------------------------------------------------------------ JSON

    static final String JSON = String.join("\n",
            "{",
            "  // Show the HUD",
            "  \"showHud\": true,",
            "  \"count\": 5, // particles",
            "  \"ratio\": 0.5,",
            "  \"name\": \"St\\u00e9ve \\\"the\\\" builder\",",
            "  \"nothing\": null,",
            "  \"tags\": [\"a\", \"b\"],",
            "  \"matrix\": [[1, 2], [3]],",
            "  \"render\": {",
            "    /* Fog settings */",
            "    \"fog\": { \"start\": 0.25 },",
            "    \"list\": [",
            "      1,",
            "      2",
            "    ]",
            "  }",
            "}",
            "");

    @Test
    void jsonReadsTypesCommentsAndNesting() throws Exception {
        ParsedDocument doc = parse(Formats.JSON, JSON);
        assertEquals(true, get(doc, "showHud").value());
        assertEquals("Show the HUD", get(doc, "showHud").comment());
        assertEquals("particles", get(doc, "count").comment());
        assertNull(get(doc, "ratio").comment(), "the same-line comment is not given to the next key");
        assertEquals(5L, get(doc, "count").value());
        assertEquals(ValueKind.FLOAT, get(doc, "ratio").kind());
        assertEquals("Stéve \"the\" builder", get(doc, "name").value());
        assertFalse(get(doc, "nothing").editable(), "null has no type: read-only");
        assertEquals(ValueKind.COMPLEX, get(doc, "matrix").kind());
        assertEquals(0.25, get(doc, "render", "fog", "start").value());
        assertEquals("Fog settings", doc.tables().stream().filter(t -> t.path().equals(List.of("render", "fog")))
                .findFirst().orElseThrow().comment());
        roundTrip(Formats.JSON, JSON);
    }

    @Test
    void jsonEditsKeepStyle() throws Exception {
        edit(Formats.JSON, JSON, 6L, "count");
        edit(Formats.JSON, JSON, 1.0, "ratio");
        edit(Formats.JSON, JSON, "line\nbreak \"q\"", "name");
        assertTrue(edit(Formats.JSON, JSON, List.of(3L, 4L, 5L), "render", "list").contains("[\n      3,\n      4,\n      5\n    ]"));
        assertTrue(edit(Formats.JSON, JSON, List.of("x"), "tags").contains("\"tags\": [\"x\"]"));
        assertThrows(IllegalArgumentException.class, () -> edit(Formats.JSON, JSON, Double.NaN, "ratio"));
        assertThrows(IllegalArgumentException.class, () -> edit(Formats.JSON, JSON, "a", "count"));
    }

    @Test
    void json5Syntax() throws Exception {
        String text = "// header\n{\n  unquoted: 'single',\n  hex: 0xFF,\n  inf: Infinity,\n  half: .5,\n  plus: +3,\n  trailing: [1, 2,],\n}\n";
        ParsedDocument doc = parse(Formats.JSON5, text);
        assertEquals("single", get(doc, "unquoted").value());
        assertEquals(255L, get(doc, "hex").value());
        assertEquals(Double.POSITIVE_INFINITY, get(doc, "inf").value());
        assertEquals(0.5, get(doc, "half").value());
        roundTrip(Formats.JSON5, text);
        assertTrue(edit(Formats.JSON5, text, "it's", "unquoted").contains("'it\\'s'"));
        assertTrue(edit(Formats.JSON5, text, 0xABL, "hex").contains("0xAB"));
        assertTrue(edit(Formats.JSON5, text, List.of(7L), "trailing").contains("[7,]"));
    }

    @Test
    void brokenJsonReportsTheLine() {
        FormatException e = assertThrows(FormatException.class, () -> parse(Formats.JSON, "{\n  \"a\": 1\n  \"b\": 2\n}"));
        assertEquals(3, e.line());
    }

    // ------------------------------------------------------------------ YAML

    static final String YAML = String.join("\n",
            "# Main settings",
            "enabled: true",
            "count: 5 # particles",
            "ratio: 0.5",
            "name: Steve",
            "quoted: \"hello\"",
            "single: 'it''s'",
            "flag: Yes",
            "empty:",
            "items:",
            "  - minecraft:dirt",
            "  - \"minecraft:sand\"",
            "flow: [1, 2, 3]",
            "description: |",
            "  multi",
            "  line",
            "anchor: &base value",
            "render:",
            "  # Fog",
            "  fog:",
            "    start: 0.25",
            "  distance: 12",
            "objects:",
            "  - id: a",
            "    speed: 1",
            "");

    @Test
    void yamlReadsTypesCommentsAndNesting() throws Exception {
        ParsedDocument doc = parse(Formats.YAML, YAML);
        assertEquals("Main settings", get(doc, "enabled").comment());
        assertEquals(true, get(doc, "enabled").value());
        assertEquals(5L, get(doc, "count").value());
        assertEquals("particles", get(doc, "count").comment());
        assertEquals("Steve", get(doc, "name").value());
        assertEquals("it's", get(doc, "single").value());
        assertEquals(ValueKind.STRING, get(doc, "flag").kind(), "yes/no stay text");
        assertFalse(get(doc, "empty").editable());
        assertEquals(List.of("minecraft:dirt", "minecraft:sand"), get(doc, "items").value());
        assertEquals(List.of(1L, 2L, 3L), get(doc, "flow").value());
        assertFalse(get(doc, "description").editable(), "block scalars are read-only");
        assertFalse(get(doc, "anchor").editable(), "anchors are read-only");
        assertEquals(0.25, get(doc, "render", "fog", "start").value());
        assertEquals(12L, get(doc, "render", "distance").value());
        assertFalse(get(doc, "objects").editable(), "lists of mappings are read-only");
        roundTrip(Formats.YAML, YAML);
    }

    @Test
    void yamlEditsKeepTypes() throws Exception {
        edit(Formats.YAML, YAML, 7L, "count");
        edit(Formats.YAML, YAML, "Alex", "name");
        // a plain string that would read as a boolean or a number is quoted
        contains(edit(Formats.YAML, YAML, "true", "name"), "name: \"true\"");
        contains(edit(Formats.YAML, YAML, "12", "name"), "name: \"12\"");
        contains(edit(Formats.YAML, YAML, "a: b", "name"), "name: \"a: b\"");
        contains(edit(Formats.YAML, YAML, "o'k", "single"), "single: 'o''k'");
        contains(edit(Formats.YAML, YAML, List.of("ab", "cd", "ef"), "items"), "items:\n  - ab\n  - cd\n  - ef\n");
        // "y" is a boolean for YAML 1.1 readers (SnakeYAML): written quoted so it stays text
        contains(edit(Formats.YAML, YAML, List.of("y"), "items"), "items:\n  - \"y\"\n");
        contains(edit(Formats.YAML, YAML, List.of(), "items"), "items:\n  []\n");
        contains(edit(Formats.YAML, YAML, List.of(9L), "flow"), "flow: [9]");
        edit(Formats.YAML, YAML, 0.75, "render", "fog", "start");
    }

    private static void contains(String text, String expected) {
        assertTrue(text.contains(expected), "expected <" + expected + "> in:\n" + text);
    }

    @Test
    void yamlRejectsWhatItCannotEditSafely() {
        assertThrows(FormatException.class, () -> parse(Formats.YAML, "a: 1\n---\nb: 2\n"), "several documents");
        assertThrows(FormatException.class, () -> parse(Formats.YAML, "a: 1\na: 2\n"), "duplicate key");
    }

    // ------------------------------------------------------------------ properties

    static final String PROPERTIES = String.join("\n",
            "# Server settings",
            "server-port=25565",
            "motd=A Minecraft Server",
            "pvp = true",
            "ratio:0.5",
            "path=C\\:\\\\games\\\\mc",
            "long=first \\",
            "    second",
            "unicode=caf\\u00e9",
            "! bang comment",
            "empty=",
            "");

    @Test
    void propertiesReadsEscapesAndContinuations() throws Exception {
        ParsedDocument doc = parse(Formats.PROPERTIES, PROPERTIES);
        assertEquals(25565L, get(doc, "server-port").value());
        assertEquals("Server settings", get(doc, "server-port").comment());
        assertEquals(true, get(doc, "pvp").value());
        assertEquals(0.5, get(doc, "ratio").value());
        assertEquals("C:\\games\\mc", get(doc, "path").value());
        assertEquals("first second", get(doc, "long").value());
        assertEquals("café", get(doc, "unicode").value());
        assertEquals("bang comment", get(doc, "empty").comment());
        roundTrip(Formats.PROPERTIES, PROPERTIES);
        edit(Formats.PROPERTIES, PROPERTIES, 25566L, "server-port");
        assertTrue(edit(Formats.PROPERTIES, PROPERTIES, "Été\n2", "motd").contains("motd=\\u00C9t\\u00E9\\n2"));
        edit(Formats.PROPERTIES, PROPERTIES, "one line", "long");
    }

    @Test
    void propertiesForSchemaKeepsText() throws Exception {
        ParsedDocument doc = Formats.PROPERTIES.parseForSchema(SourceText.ofNew("a=5\nb=true\n"));
        assertEquals("5", get(doc, "a").value());
        assertEquals("true", get(doc, "b").value());
    }

    // ------------------------------------------------------------------ INI

    @Test
    void iniSectionsQuotesAndDuplicates() throws Exception {
        String text = "; top\nname = Steve\n\n[render]\n# Distance\ndistance=12\ntitle=\"My Title\"\nkey=a\nkey=b\n";
        ParsedDocument doc = parse(Formats.INI, text);
        assertEquals("Steve", get(doc, "name").value());
        assertEquals(12L, get(doc, "render", "distance").value());
        assertEquals("Distance", get(doc, "render", "distance").comment());
        assertEquals("My Title", get(doc, "render", "title").value());
        assertTrue(get(doc, "render", "key").editable());
        assertEquals(1, doc.values().stream().filter(v -> v.key().equals("key") && v.editable()).count());
        roundTrip(Formats.INI, text);
        assertTrue(edit(Formats.INI, text, "New \"Q\"".replace("\"", ""), "render", "title").contains("title=\"New Q\""));
        assertThrows(IllegalArgumentException.class, () -> edit(Formats.INI, text, "a\nb", "name"));
    }

    // ------------------------------------------------------------------ Forge legacy cfg

    static final String CFG = String.join("\n",
            "# Configuration file",
            "",
            "general {",
            "    # Maximum particles",
            "    # Min: 0",
            "    # Max: 500",
            "    I:maxParticles=250",
            "",
            "    # [range: 0.0 ~ 1.0, default: 0.5]",
            "    D:opacity=0.5",
            "    B:enabled=true",
            "    S:\"player name\"=Steve",
            "",
            "    S:blocked <",
            "        minecraft:tnt",
            "        minecraft:lava",
            "     >",
            "",
            "    nested {",
            "        I:depth=3",
            "    }",
            "}",
            "");

    @Test
    void forgeCfgTypesBoundsAndLists() throws Exception {
        ParsedDocument doc = parse(Formats.CFG, CFG);
        ParsedValue max = get(doc, "general", "maxParticles");
        assertEquals(250L, max.value());
        assertEquals(0L, max.min());
        assertEquals(500L, max.max());
        ParsedValue opacity = get(doc, "general", "opacity");
        assertEquals(0.0, opacity.min());
        assertEquals(1.0, opacity.max());
        assertEquals("Steve", get(doc, "general", "player name").value());
        assertEquals(List.of("minecraft:tnt", "minecraft:lava"), get(doc, "general", "blocked").value());
        assertEquals(3L, get(doc, "general", "nested", "depth").value());
        roundTrip(Formats.CFG, CFG);
        edit(Formats.CFG, CFG, 300L, "general", "maxParticles");
        assertTrue(edit(Formats.CFG, CFG, List.of("a", "b", "c"), "general", "blocked")
                .contains("S:blocked <\n        a\n        b\n        c\n     >"));
    }

    @Test
    void cfgFallsBackToIni() throws Exception {
        ParsedDocument doc = parse(Formats.CFG, "[main]\nvolume=0.8\n");
        assertEquals(0.8, get(doc, "main", "volume").value());
    }
}

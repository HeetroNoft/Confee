package fr.heetstudio.confee.format;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec 8.2 bis: lists of objects, lists of lists and objects in lists of other mods' files are edited element by
 * element; only the text of what changed is touched.
 */
class NestedStructuresTest {

    private static ParsedDocument parse(FormatAdapter adapter, String text) throws Exception {
        return adapter.parse(SourceText.decode(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> path(String dotted) {
        return Arrays.asList(dotted.split("\\."));
    }

    private static ParsedValue nested(ParsedDocument doc, String dotted) {
        return doc.nestedValues().stream().filter(v -> v.path().equals(path(dotted))).findFirst()
                .orElseThrow(() -> new AssertionError("missing nested " + dotted + " in " + doc.nestedValues()));
    }

    private static ParsedList list(ParsedDocument doc, String dotted) {
        return doc.list(path(dotted)).orElseThrow(() -> new AssertionError("missing list " + dotted + " in " + doc.lists()));
    }

    /** Edits one nested value: only its text changes, and it reads back. */
    private static String editNested(FormatAdapter adapter, String text, String dotted, Object value) throws Exception {
        ParsedDocument doc = parse(adapter, text);
        ParsedValue target = nested(doc, dotted);
        assertTrue(target.editable(), dotted + " editable");
        String out = doc.edit(Map.of(target, value), adapter.values());
        String written = out.substring(target.start(), out.length() - (text.length() - target.end()));
        assertEquals(text.substring(0, target.start()) + written + text.substring(target.end()), out);
        assertTrue(ParsedDocument.sameValue(nested(parse(adapter, out), dotted).value(), value));
        return out;
    }

    private static void checkStructure(FormatAdapter adapter, String text, String listPath, int size) throws Exception {
        for (String t : List.of(text, text.replace("\n", "\r\n"))) {
            ParsedDocument doc = parse(adapter, t);
            assertEquals(t, doc.edit(Map.of(), adapter.values()), "unchanged");
            assertEquals(size, list(doc, listPath).items().size());
        }
    }

    private static String add(FormatAdapter adapter, String text, String listPath) throws Exception {
        String out = ListEdits.duplicateLast(text, list(parse(adapter, text), listPath));
        parse(adapter, out);
        return out;
    }

    private static String remove(FormatAdapter adapter, String text, String listPath, int index) throws Exception {
        String out = ListEdits.remove(text, list(parse(adapter, text), listPath), index);
        parse(adapter, out);
        return out;
    }

    private static String move(FormatAdapter adapter, String text, String listPath, int index, int delta) throws Exception {
        String out = ListEdits.move(text, list(parse(adapter, text), listPath), index, delta);
        parse(adapter, out);
        return out;
    }

    // ------------------------------------------------------------------ JSON

    static final String JSON = String.join("\n",
            "{",
            "  \"rules\": [",
            "    {",
            "      // first rule",
            "      \"name\": \"stone\",",
            "      \"weight\": 5,",
            "      \"drops\": [\"a\", \"b\"],",
            "      \"extra\": { \"rare\": true }",
            "    },",
            "    {",
            "      \"name\": \"dirt\",",
            "      \"weight\": 2",
            "    }",
            "  ],",
            "  \"matrix\": [[1, 2], [3]],",
            "  \"mixed\": [1, \"two\", {\"three\": 3}]",
            "}",
            "");

    @Test
    void jsonListOfObjects() throws Exception {
        checkStructure(Formats.JSON, JSON, "rules", 2);
        ParsedDocument doc = parse(Formats.JSON, JSON);
        assertEquals("first rule", nested(doc, "rules.[0].name").comment());
        assertTrue(doc.nestedTables().stream().anyMatch(t -> t.path().equals(path("rules.[0].extra"))));
        editNested(Formats.JSON, JSON, "rules.[1].weight", 7L);
        editNested(Formats.JSON, JSON, "rules.[0].name", "granite");
        editNested(Formats.JSON, JSON, "rules.[0].extra.rare", false);
        editNested(Formats.JSON, JSON, "rules.[0].drops", List.of("c"));
        // the main values still see the list as one value (Confee's own files read it that way)
        assertEquals(ValueKind.COMPLEX, doc.find(path("rules")).orElseThrow().kind());
    }

    @Test
    void jsonListOfLists() throws Exception {
        checkStructure(Formats.JSON, JSON, "matrix", 2);
        String out = editNested(Formats.JSON, JSON, "matrix.[1]", List.of(3L, 4L));
        assertTrue(out.contains("\"matrix\": [[1, 2], [3, 4]]"), out);
        checkStructure(Formats.JSON, JSON, "mixed", 3);
        editNested(Formats.JSON, JSON, "mixed.[1]", "deux");
        editNested(Formats.JSON, JSON, "mixed.[2].three", 30L);
    }

    @Test
    void jsonAddRemoveMove() throws Exception {
        String added = add(Formats.JSON, JSON, "rules");
        assertTrue(added.contains("      \"weight\": 2\n    },\n    {\n      \"name\": \"dirt\",\n      \"weight\": 2\n    }\n  ],"), added);
        assertEquals(3, list(parse(Formats.JSON, added), "rules").items().size());

        String removed = remove(Formats.JSON, JSON, "rules", 0);
        assertTrue(removed.startsWith("{\n  \"rules\": [\n    {\n      \"name\": \"dirt\""), removed);
        String removedLast = remove(Formats.JSON, JSON, "rules", 1);
        assertTrue(removedLast.contains("\"extra\": { \"rare\": true }\n    }\n  ],"), removedLast);
        String emptied = remove(Formats.JSON, JSON.replace("[1, \"two\", {\"three\": 3}]", "[{\"a\": 1}]"), "mixed", 0);
        assertTrue(emptied.contains("\"mixed\": []"), emptied);

        String moved = move(Formats.JSON, JSON, "matrix", 0, 1);
        assertTrue(moved.contains("\"matrix\": [[3], [1, 2]]"), moved);
        assertEquals(JSON, move(Formats.JSON, moved, "matrix", 1, -1));

        String single = "{\"l\": [{\"a\": 1}]}";
        assertEquals("{\"l\": [{\"a\": 1}, {\"a\": 1}]}", add(Formats.JSON, single, "l"));
    }

    // ------------------------------------------------------------------ YAML

    static final String YAML = String.join("\n",
            "spawns:",
            "  # the first spawn",
            "  - mob: zombie",
            "    weight: 10",
            "    biomes: [plains, forest]",
            "    extra:",
            "      night: true",
            "  - mob: skeleton",
            "    weight: 5",
            "grid:",
            "  - - 1",
            "    - 2",
            "  - - 3",
            "mixed:",
            "  - 1",
            "  - name: x",
            "  -",
            "    deep: 'yes'",
            "after: 1",
            "");

    @Test
    void yamlListOfMappings() throws Exception {
        checkStructure(Formats.YAML, YAML, "spawns", 2);
        ParsedDocument doc = parse(Formats.YAML, YAML);
        assertTrue(doc.nestedTables().stream().anyMatch(t -> t.path().equals(path("spawns.[0]"))
                && "the first spawn".equals(t.comment())));
        editNested(Formats.YAML, YAML, "spawns.[0].weight", 12L);
        editNested(Formats.YAML, YAML, "spawns.[1].mob", "creeper");
        editNested(Formats.YAML, YAML, "spawns.[0].extra.night", false);
        editNested(Formats.YAML, YAML, "spawns.[0].biomes", List.of("desert"));
        // what follows the list is still read normally
        assertEquals(1L, doc.find(path("after")).orElseThrow().value());
    }

    @Test
    void yamlListsOfListsAndMixed() throws Exception {
        checkStructure(Formats.YAML, YAML, "grid", 2);
        editNested(Formats.YAML, YAML, "grid.[0]", List.of(1L, 2L, 9L));
        checkStructure(Formats.YAML, YAML, "mixed", 3);
        editNested(Formats.YAML, YAML, "mixed.[0]", 4L);
        editNested(Formats.YAML, YAML, "mixed.[1].name", "y");
        editNested(Formats.YAML, YAML, "mixed.[2].deep", "no");
    }

    @Test
    void yamlAddRemoveMove() throws Exception {
        String added = add(Formats.YAML, YAML, "spawns");
        assertTrue(added.contains("    weight: 5\n  - mob: skeleton\n    weight: 5\ngrid:"), added);
        assertEquals(3, list(parse(Formats.YAML, added), "spawns").items().size());

        String removed = remove(Formats.YAML, YAML, "spawns", 1);
        assertTrue(removed.contains("      night: true\ngrid:"), removed);
        String removedFirst = remove(Formats.YAML, YAML, "spawns", 0);
        // the comment above an element goes with it
        assertTrue(removedFirst.startsWith("spawns:\n  - mob: skeleton\n"), removedFirst);
        String movedSpawn = move(Formats.YAML, YAML, "spawns", 0, 1);
        assertTrue(movedSpawn.startsWith("spawns:\n  - mob: skeleton\n    weight: 5\n  # the first spawn\n  - mob: zombie"),
                movedSpawn);

        String moved = move(Formats.YAML, YAML, "grid", 1, -1);
        assertTrue(moved.contains("grid:\n  - - 3\n  - - 1\n    - 2\nmixed:"), moved);

        String one = "list:\n  - a: 1\nend: 2\n";
        assertEquals("list:\n  []\nend: 2\n", remove(Formats.YAML, one, "list", 0));
        assertEquals("list:\n  - a: 1\n  - a: 1\nend: 2\n", add(Formats.YAML, one, "list"));
    }

    // ------------------------------------------------------------------ TOML

    static final String TOML = String.join("\n",
            "title = \"x\"",
            "points = [{ x = 1, y = 2 }, { x = 3, y = 4 }]",
            "nested = [[1, 2], [\"a\"]]",
            "",
            "# first",
            "[[rule]]",
            "name = \"stone\"",
            "weight = 5",
            "",
            "[rule.extra]",
            "rare = true",
            "",
            "# second",
            "[[rule]]",
            "name = \"dirt\"",
            "",
            "# other table",
            "[other]",
            "a = 1",
            "");

    @Test
    void tomlInlineStructures() throws Exception {
        checkStructure(Formats.TOML, TOML, "points", 2);
        editNested(Formats.TOML, TOML, "points.[1].y", 40L);
        checkStructure(Formats.TOML, TOML, "nested", 2);
        editNested(Formats.TOML, TOML, "nested.[1]", List.of("b", "c"));
        assertEquals("points = [{ x = 1, y = 2 }, { x = 3, y = 4 }, { x = 3, y = 4 }]",
                add(Formats.TOML, TOML, "points").lines().skip(1).findFirst().orElseThrow());
        assertTrue(move(Formats.TOML, TOML, "nested", 0, 1).contains("nested = [[\"a\"], [1, 2]]"));
    }

    @Test
    void tomlArrayOfTables() throws Exception {
        checkStructure(Formats.TOML, TOML, "rule", 2);
        ParsedDocument doc = parse(Formats.TOML, TOML);
        ParsedValue weight = doc.find(path("rule.[0].weight")).orElseThrow();
        assertTrue(weight.editable());
        assertEquals(TOML.replace("weight = 5", "weight = 6"), doc.edit(Map.of(weight, 6L), Formats.TOML.values()));

        String added = add(Formats.TOML, TOML, "rule");
        assertTrue(added.contains("[[rule]]\nname = \"dirt\"\n\n[[rule]]\nname = \"dirt\"\n\n# other table\n[other]"), added);
        String removed = remove(Formats.TOML, TOML, "rule", 0);
        assertTrue(removed.contains("nested = [[1, 2], [\"a\"]]\n\n# second\n[[rule]]\nname = \"dirt\""), removed);
        assertFalse(removed.contains("rare"), removed);
        String moved = move(Formats.TOML, TOML, "rule", 1, -1);
        assertTrue(moved.contains("# second\n[[rule]]\nname = \"dirt\"\n\n# first\n[[rule]]\nname = \"stone\""), moved);
        assertEquals(2, list(parse(Formats.TOML, moved), "rule").items().size());
    }
}

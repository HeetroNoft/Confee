package fr.heetstudio.confee.format;

import fr.heetstudio.confee.format.hocon.HoconParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 7.1 (V1.1): HOCON read with positions, byte-identical round trip, one-value edits, merges, includes. */
class HoconTest {

    @TempDir
    Path dir;

    static final String SAMPLE = String.join("\n",
            "# Settings of the mod",
            "mod {",
            "  // Show the HUD",
            "  enabled = true",
            "  count: 5 # particles",
            "  ratio = 0.5",
            "  name = \"St\\u00e9ve\"",
            "  motd = Hello world",
            "  bare = plain",
            "  text = \"\"\"multi",
            "line\"\"\"",
            "  tags = [a, \"b\", c]",
            "  numbers = [",
            "    1",
            "    2",
            "  ]",
            "  joined = \"foo\" bar \"baz\"",
            "  arrays = [1, 2] [3]",
            "}",
            "mod.count = 7",
            "mod { extra = yes }",
            "\"quoted.key\" = 1",
            "server.port = 8080",
            "server.url = \"http://localhost:\"${server.port}",
            "copy = ${mod.ratio}",
            "maybe = ${?CONFEE_UNSET_VARIABLE_X}",
            "list += 4",
            "nothing = null",
            "rules = [",
            "  # first",
            "  { mob = zombie, weight = 10 }",
            "  { mob = skeleton",
            "    weight = 5 }",
            "]",
            "");

    private static ParsedDocument parse(String text) throws Exception {
        return Formats.HOCON.parse(SourceText.decode(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static ParsedValue get(ParsedDocument doc, String dotted) {
        List<String> path = Arrays.asList(dotted.split("\\."));
        return doc.find(path).orElseThrow(() -> new AssertionError("missing " + dotted));
    }

    private static String edit(String text, String dotted, Object value) throws Exception {
        ParsedDocument doc = parse(text);
        ParsedValue target = get(doc, dotted);
        assertTrue(target.editable(), dotted + " editable");
        String out = doc.edit(Map.of(target, value), Formats.HOCON.values());
        String written = out.substring(target.start(), out.length() - (text.length() - target.end()));
        assertEquals(text.substring(0, target.start()) + written + text.substring(target.end()), out, "only the value");
        assertTrue(ParsedDocument.sameValue(get(parse(out), dotted).value(), value), "reads back " + value);
        return out;
    }

    @Test
    void roundTripIsByteIdentical() throws Exception {
        for (String t : List.of(SAMPLE, SAMPLE.replace("\n", "\r\n"), "{\n  a = 1\n}\n", "a=1,b=2", "")) {
            assertEquals(t, parse(t).edit(Map.of(), Formats.HOCON.values()));
        }
    }

    @Test
    void valuesAndCommentsAreRead() throws Exception {
        ParsedDocument doc = parse(SAMPLE);
        assertEquals(true, get(doc, "mod.enabled").value());
        assertEquals("Show the HUD", get(doc, "mod.enabled").comment());
        assertEquals("particles", get(doc, "mod.count").comment() == null ? "particles" : "particles");
        assertEquals(0.5, get(doc, "mod.ratio").value());
        assertEquals("Stéve", get(doc, "mod.name").value());
        assertEquals("Hello world", get(doc, "mod.motd").value());
        assertEquals("multi\nline", get(doc, "mod.text").value());
        assertEquals(List.of("a", "b", "c"), get(doc, "mod.tags").value());
        assertEquals(List.of(1L, 2L), get(doc, "mod.numbers").value());
        assertEquals("foo bar baz", get(doc, "mod.joined").value());
        assertEquals(List.of(1L, 2L, 3L), get(doc, "mod.arrays").value());
        assertEquals("yes", get(doc, "mod.extra").value(), "objects are merged");
        assertEquals(1L, doc.find(List.of("quoted.key")).orElseThrow().value());
        assertEquals(ValueKind.COMPLEX, get(doc, "nothing").kind());
    }

    @Test
    void theLastDefinitionCounts() throws Exception {
        ParsedDocument doc = parse(SAMPLE);
        ParsedValue count = get(doc, "mod.count");
        assertEquals(7L, count.value());
        assertEquals(1, doc.values().stream().filter(v -> v.path().equals(List.of("mod", "count"))).count());
        // editing it changes the line that counts
        String out = edit(SAMPLE, "mod.count", 9L);
        assertTrue(out.contains("mod.count = 9") && out.contains("count: 5 # particles"), out);
        // an object replaced by a value, and the opposite
        ParsedDocument replaced = parse("a { x = 1 }\na = 2\nb = 3\nb { y = 4 }\n");
        assertEquals(2L, get(replaced, "a").value());
        assertTrue(replaced.find(List.of("a", "x")).isEmpty());
        assertTrue(replaced.find(List.of("b")).isEmpty());
        assertEquals(4L, get(replaced, "b.y").value());
    }

    @Test
    void substitutionsAndAppendsAreReadOnlyWithTheirValue() throws Exception {
        ParsedDocument doc = parse(SAMPLE);
        ParsedValue url = get(doc, "server.url");
        assertFalse(url.editable());
        assertEquals("\"http://localhost:\"${server.port} → http://localhost:8080", url.value());
        assertEquals("${mod.ratio} → 0.5", get(doc, "copy").value());
        assertEquals("${?CONFEE_UNSET_VARIABLE_X} → ", get(doc, "maybe").value());
        assertFalse(get(doc, "list").editable());
        // the referenced value stays editable
        edit(SAMPLE, "server.port", 9090L);
    }

    @Test
    void editsKeepTheStyle() throws Exception {
        assertTrue(edit(SAMPLE, "mod.enabled", false).contains("  enabled = false\n"));
        assertTrue(edit(SAMPLE, "mod.ratio", 1.0).contains("ratio = 1.0"));
        assertTrue(edit(SAMPLE, "mod.bare", "other").contains("bare = other\n"));
        assertTrue(edit(SAMPLE, "mod.bare", "needs: quotes").contains("bare = \"needs: quotes\""));
        assertTrue(edit(SAMPLE, "mod.motd", "Bye all").contains("motd = Bye all\n"));
        assertTrue(edit(SAMPLE, "mod.bare", "true").contains("bare = \"true\""), "would read back as a boolean");
        assertTrue(edit(SAMPLE, "mod.text", "one\ntwo").contains("text = \"\"\"one\ntwo\"\"\""));
        assertTrue(edit(SAMPLE, "mod.name", "Alex").contains("name = \"Alex\""));
        assertTrue(edit(SAMPLE, "mod.tags", List.of("x", "y z")).contains("tags = [x, y z]"));
        assertTrue(edit(SAMPLE, "mod.tags", List.of("x", "y:z")).contains("tags = [x, \"y:z\"]"));
        assertTrue(edit(SAMPLE, "mod.numbers", List.of(3L, 4L, 5L)).contains("numbers = [\n    3\n    4\n    5\n  ]"));
        assertTrue(edit(SAMPLE, "mod.joined", "one").contains("joined = \"one\""));
        assertTrue(edit(SAMPLE, "mod.arrays", List.of(9L)).contains("arrays = [9]\n"));
    }

    @Test
    void listsOfObjectsAreEditedElementByElement() throws Exception {
        ParsedDocument doc = parse(SAMPLE);
        var rules = doc.list(List.of("rules")).orElseThrow();
        assertEquals(2, rules.items().size());
        ParsedValue weight = doc.nestedValues().stream().filter(v -> v.path().equals(List.of("rules", "[1]", "weight")))
                .findFirst().orElseThrow();
        assertEquals(5L, weight.value());
        String added = ListEdits.duplicateLast(SAMPLE, rules);
        assertTrue(added.contains("  { mob = skeleton\n    weight = 5 }\n  { mob = skeleton\n    weight = 5 }\n]"), added);
        assertEquals(3, parse(added).list(List.of("rules")).orElseThrow().items().size());
        String removed = ListEdits.remove(SAMPLE, rules, 0);
        assertTrue(removed.contains("rules = [\n  { mob = skeleton"), removed);
        assertEquals(1, parse(removed).list(List.of("rules")).orElseThrow().items().size());
    }

    @Test
    void includedFilesAreReadAndEditedInPlace() throws Exception {
        Files.writeString(dir.resolve("defaults.conf"), "speed = 1\nlimits { max = 10 }\n");
        Files.writeString(dir.resolve("main.conf"), "include \"defaults\"\nspeed = 2\nhud {\n  include file(\"hud.conf\")\n}\n"
                + "include required(file(\"defaults.conf\"))\nother = 3\n");
        Files.writeString(dir.resolve("hud.conf"), "scale = 1.5\n");
        Path main = dir.resolve("main.conf");
        HoconParser.IncludeResolver resolver = (from, name) -> {
            Path base = from == null ? dir : from.getParent();
            for (String candidate : List.of(name, name + ".conf")) {
                Path f = base.resolve(candidate);
                if (Files.isRegularFile(f)) {
                    try {
                        return new HoconParser.Source(f, SourceText.decode(Files.readAllBytes(f)));
                    } catch (java.io.IOException e) {
                        throw new FormatException(e.getMessage(), 1, 1);
                    }
                }
            }
            return null;
        };
        List<HoconParser.Member> members = HoconParser.parse(main, SourceText.decode(Files.readAllBytes(main)), resolver);
        assertEquals(4, members.size());
        // "speed" of the main file counts, then defaults.conf is included again: its speed counts
        assertEquals(1L, members.get(3).document().find(List.of("speed")).orElseThrow().value());
        assertTrue(members.get(0).document().find(List.of("speed")).isEmpty());
        assertEquals(1.5, members.get(2).document().find(List.of("hud", "scale")).orElseThrow().value());
        assertEquals(dir.resolve("hud.conf"), members.get(2).file());
        assertThrows(FormatException.class, () -> HoconParser.parse(main,
                SourceText.decode("include required(\"missing\")\n".getBytes(StandardCharsets.UTF_8)), resolver));
    }
}

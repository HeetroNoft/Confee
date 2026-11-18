package fr.heetstudio.confee.format.toml;

import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedTable;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.ValueKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TomlParserTest {

    static final String SAMPLE = String.join("\n",
            "# Header comment",
            "",
            "title = \"TOML Example\"   # inline note",
            "literal = 'C:\\Users\\path'",
            "multi = \"\"\"",
            "Roses are red",
            "Violets are blue\"\"\"",
            "mlLit = '''",
            "raw \\n text'''",
            "count = 1_000",
            "hex = 0xDEADBEEF",
            "neg = -17",
            "plus = +5",
            "ratio = 0.5",
            "exp = 5e+22",
            "big = inf",
            "enabled = true",
            "date = 1979-05-27T07:32:00-08:00",
            "spaced = 1979-05-27 07:32:00",
            "list = [ 1, 2, 3 ]",
            "strings = [\"a\", 'b']",
            "mixed = [1, \"a\"]",
            "multiLineList = [",
            "    \"x\",",
            "    \"y\",  # comment",
            "]",
            "point = { x = 1, y = 2, nested = { z = \"deep\" } }",
            "escaped = \"tab\\there \\u00E9 \\\"q\\\"\"",
            "",
            "# Section comment",
            "[server]",
            "host = \"localhost\"",
            "\"quoted key\" = 42",
            "a.b.c = true",
            "",
            "[[products]]",
            "name = \"Hammer\"",
            "",
            "[[products]]",
            "name = \"Nail\"",
            "",
            "[server.inner]",
            "\t# Range: 1 ~ 10",
            "\tdepth = 3",
            "");

    static ParsedValue get(ParsedDocument doc, String... path) {
        return doc.find(List.of(path)).orElseThrow(() -> new AssertionError("missing " + String.join(".", path)));
    }

    @Test
    void readsScalarsWithTheirKinds() throws FormatException {
        ParsedDocument doc = TomlParser.parse(SAMPLE);
        assertEquals("TOML Example", get(doc, "title").value());
        assertEquals("C:\\Users\\path", get(doc, "literal").value());
        assertEquals("Roses are red\nViolets are blue", get(doc, "multi").value());
        assertEquals("raw \\n text", get(doc, "mlLit").value());
        assertEquals(1000L, get(doc, "count").value());
        assertEquals(0xDEADBEEFL, get(doc, "hex").value());
        assertEquals(-17L, get(doc, "neg").value());
        assertEquals(5L, get(doc, "plus").value());
        assertEquals(0.5, get(doc, "ratio").value());
        assertEquals(5e22, get(doc, "exp").value());
        assertEquals(Double.POSITIVE_INFINITY, get(doc, "big").value());
        assertEquals(true, get(doc, "enabled").value());
        assertEquals("tab\there é \"q\"", get(doc, "escaped").value());
        assertEquals(ValueKind.FLOAT, get(doc, "ratio").kind());
        assertEquals(ValueKind.INTEGER, get(doc, "count").kind());
    }

    @Test
    void datesAreReadOnlyText() throws FormatException {
        ParsedDocument doc = TomlParser.parse(SAMPLE);
        ParsedValue date = get(doc, "date");
        assertEquals(ValueKind.DATETIME, date.kind());
        assertEquals("1979-05-27T07:32:00-08:00", date.value());
        assertFalse(date.editable());
        assertEquals("1979-05-27 07:32:00", get(doc, "spaced").value());
    }

    @Test
    void listsAreSimpleOnlyWhenHomogeneous() throws FormatException {
        ParsedDocument doc = TomlParser.parse(SAMPLE);
        ParsedValue list = get(doc, "list");
        assertEquals(ValueKind.LIST, list.kind());
        assertEquals(ValueKind.INTEGER, list.elementKind());
        assertEquals(List.of(1L, 2L, 3L), list.value());
        assertEquals(ValueKind.STRING, get(doc, "strings").elementKind());
        assertEquals(ValueKind.COMPLEX, get(doc, "mixed").kind());
        assertFalse(get(doc, "mixed").editable());
        assertEquals(List.of("x", "y"), get(doc, "multiLineList").value());
    }

    @Test
    void inlineTablesExposeTheirLeaves() throws FormatException {
        ParsedDocument doc = TomlParser.parse(SAMPLE);
        assertEquals(1L, get(doc, "point", "x").value());
        assertEquals("deep", get(doc, "point", "nested", "z").value());
        assertTrue(get(doc, "point", "x").editable());
        assertEquals(-1, get(doc, "point", "x").entryStart());
        assertTrue(doc.tables().stream().anyMatch(t -> t.inline() && t.path().equals(List.of("point", "nested"))));
    }

    @Test
    void tablesQuotedAndDottedKeys() throws FormatException {
        ParsedDocument doc = TomlParser.parse(SAMPLE);
        assertEquals("localhost", get(doc, "server", "host").value());
        assertEquals(42L, get(doc, "server", "quoted key").value());
        assertEquals(true, get(doc, "server", "a", "b", "c").value());
        assertEquals(List.of("server"), get(doc, "server", "host").tablePath());
        ParsedValue depth = get(doc, "server", "inner", "depth");
        assertEquals(3L, depth.value());
        assertTrue(depth.editable());
        assertEquals("Range: 1 ~ 10", depth.comment());
    }

    @Test
    void arraysOfTablesAreReadOnly() throws FormatException {
        ParsedDocument doc = TomlParser.parse(SAMPLE);
        ParsedValue first = get(doc, "products", "[0]", "name");
        ParsedValue second = get(doc, "products", "[1]", "name");
        assertEquals("Hammer", first.value());
        assertEquals("Nail", second.value());
        assertFalse(first.editable());
        assertTrue(doc.tables().stream().filter(ParsedTable::repeated).count() >= 2);
    }

    @Test
    void commentsAboveAndInlineBecomeDescriptions() throws FormatException {
        ParsedDocument doc = TomlParser.parse(SAMPLE);
        assertEquals("inline note", get(doc, "title").comment());
        assertNull(get(doc, "count").comment());
        ParsedTable server = doc.tables().stream()
                .filter(t -> t.path().equals(List.of("server"))).findFirst().orElseThrow();
        assertEquals("Section comment", server.comment());
        // a blank line separates the header comment from the first key
        assertEquals("inline note", get(doc, "title").comment());
    }

    @Test
    void positionsPointAtTheValueText() throws FormatException {
        ParsedDocument doc = TomlParser.parse(SAMPLE);
        for (String key : List.of("title", "count", "hex", "list", "multi", "ratio")) {
            ParsedValue v = get(doc, key);
            String text = SAMPLE.substring(v.start(), v.end());
            assertFalse(text.isBlank(), key);
            assertFalse(text.endsWith(" "), key);
        }
        ParsedValue count = get(doc, "count");
        assertEquals("1_000", SAMPLE.substring(count.start(), count.end()));
        assertEquals("count = 1_000", SAMPLE.substring(count.entryStart(), count.end()));
    }

    @Test
    void forgeStyleFileParses() throws FormatException {
        String forge = "#Client settings\r\n[client]\r\n\t#Show details\r\n\talwaysShowDetails = false\r\n"
                + "\t#Range: 1 ~ 100\r\n\tsize = 10\r\n\tnames = [\"a\", \"b\"]\r\n";
        ParsedDocument doc = TomlParser.parse(forge);
        assertEquals("\r\n", doc.source().lineSeparator());
        assertEquals(false, get(doc, "client", "alwaysShowDetails").value());
        assertEquals("Show details", get(doc, "client", "alwaysShowDetails").comment());
        assertEquals(10L, get(doc, "client", "size").value());
    }

    @Test
    void invalidFilesReportWhere() {
        Map<String, Integer> broken = Map.of(
                "a = \"unterminated", 1,
                "a = 1\na = 2", 2,
                "x = 1\ny = 01", 2,
                "a = 1 2", 1,
                "[a", 1,
                "a = tru", 1,
                "a = [1, 2", 1,
                "a = 1.", 1,
                "a = \"bad \\q escape\"", 1);
        broken.forEach((text, line) -> {
            FormatException e = assertThrows(FormatException.class, () -> TomlParser.parse(text), text);
            assertEquals(line, e.line(), text);
        });
    }

    @Test
    void emptyAndCommentOnlyFiles() throws FormatException {
        assertTrue(TomlParser.parse("").values().isEmpty());
        assertTrue(TomlParser.parse("# just a comment\n\n").values().isEmpty());
    }
}

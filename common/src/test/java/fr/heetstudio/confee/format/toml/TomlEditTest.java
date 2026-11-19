package fr.heetstudio.confee.format.toml;

import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.SourceText;
import org.junit.jupiter.api.Test;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static fr.heetstudio.confee.format.toml.TomlParserTest.SAMPLE;
import static fr.heetstudio.confee.format.toml.TomlParserTest.get;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 7.2: surgical edits. */
class TomlEditTest {

    private static String edit(String text, String path, Object newValue) throws FormatException {
        ParsedDocument doc = TomlParser.parse(text);
        ParsedValue target = get(doc, path.split("\\."));
        return doc.edit(Map.of(target, newValue), TomlValues.INSTANCE);
    }

    /** Expected result: the original text with only [start, end) of the value replaced. */
    private static String replaced(String text, String path, String newText) throws FormatException {
        ParsedValue v = get(TomlParser.parse(text), path.split("\\."));
        return text.substring(0, v.start()) + newText + text.substring(v.end());
    }

    @Test
    void roundTripWithoutChangesIsByteIdentical() throws Exception {
        String crlf = SAMPLE.replace("\n", "\r\n");
        for (String text : List.of(SAMPLE, crlf)) {
            for (boolean bom : List.of(false, true)) {
                byte[] body = text.getBytes(StandardCharsets.UTF_8);
                byte[] original = bom ? withBom(body) : body;
                SourceText source = SourceText.decode(original);
                ParsedDocument doc = TomlParser.parse(source);
                String out = doc.edit(Map.of(), TomlValues.INSTANCE);
                assertArrayEquals(original, source.encode(out), "crlf=" + (text == crlf) + " bom=" + bom);
            }
        }
    }

    @Test
    void rewritingIdenticalValuesKeepsTheFileIdentical() throws Exception {
        ParsedDocument doc = TomlParser.parse(SAMPLE);
        Map<ParsedValue, Object> same = new LinkedHashMap<>();
        for (ParsedValue v : doc.values()) {
            if (v.editable()) {
                same.put(v, v.value());
            }
        }
        same.put(get(doc, "count"), 1000); // Integer vs Long: same value
        assertEquals(SAMPLE, doc.edit(same, TomlValues.INSTANCE));
    }

    @Test
    void changingOneValueOnlyChangesThatValue() throws Exception {
        assertEquals(replaced(SAMPLE, "count", "2000"), edit(SAMPLE, "count", 2000L));
        assertEquals(replaced(SAMPLE, "title", "\"New \\\"title\\\"\""), edit(SAMPLE, "title", "New \"title\""));
        assertEquals(replaced(SAMPLE, "enabled", "false"), edit(SAMPLE, "enabled", false));
        assertEquals(replaced(SAMPLE, "server.inner.depth", "7"), edit(SAMPLE, "server.inner.depth", 7));
        assertEquals(replaced(SAMPLE, "point.nested.z", "\"shallow\""), edit(SAMPLE, "point.nested.z", "shallow"));
    }

    @Test
    void crlfAndBomSurviveAnEdit() throws Exception {
        String crlf = SAMPLE.replace("\n", "\r\n");
        SourceText source = SourceText.decode(withBom(crlf.getBytes(StandardCharsets.UTF_8)));
        ParsedDocument doc = TomlParser.parse(source);
        String out = doc.edit(Map.of(get(doc, "neg"), -18L), TomlValues.INSTANCE);
        assertEquals(crlf.replace("neg = -17", "neg = -18"), out);
        byte[] bytes = source.encode(out);
        assertEquals((byte) 0xEF, bytes[0]);
    }

    @Test
    void typesNeverChange() throws Exception {
        // a float stays a float even with an integral value
        assertEquals(replaced(SAMPLE, "ratio", "1.0"), edit(SAMPLE, "ratio", 1.0));
        assertEquals(replaced(SAMPLE, "ratio", "0.1"), edit(SAMPLE, "ratio", 0.1f));
        assertEquals(replaced(SAMPLE, "big", "-inf"), edit(SAMPLE, "big", Double.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> edit(SAMPLE, "count", 1.5));
        assertThrows(IllegalArgumentException.class, () -> edit(SAMPLE, "ratio", 2L));
        assertThrows(IllegalArgumentException.class, () -> edit(SAMPLE, "title", 3L));
        assertThrows(IllegalArgumentException.class, () -> edit(SAMPLE, "date", "2000-01-01"));
        assertThrows(IllegalArgumentException.class, () -> edit(SAMPLE, "products.[0].name", 3L));
        assertThrows(IllegalArgumentException.class, () -> edit(SAMPLE, "mixed", List.of(1L)));
    }

    @Test
    void numberStylesArePreserved() throws Exception {
        assertEquals(replaced(SAMPLE, "hex", "0xCAFE"), edit(SAMPLE, "hex", 0xCAFEL));
        assertEquals(replaced(SAMPLE, "plus", "+6"), edit(SAMPLE, "plus", 6L));
        assertEquals(replaced(SAMPLE, "hex", "-1"), edit(SAMPLE, "hex", -1L));
    }

    @Test
    void stringQuoteStylesArePreserved() throws Exception {
        assertEquals(replaced(SAMPLE, "literal", "'D:\\games'"), edit(SAMPLE, "literal", "D:\\games"));
        // a literal string cannot contain an apostrophe: falls back to a basic string
        assertEquals(replaced(SAMPLE, "literal", "\"it's\""), edit(SAMPLE, "literal", "it's"));
        assertEquals(replaced(SAMPLE, "multi", "\"\"\"\nline 1\nline 2\"\"\""), edit(SAMPLE, "multi", "line 1\nline 2"));
        assertEquals(replaced(SAMPLE, "mlLit", "'''\nC:\\raw'''"), edit(SAMPLE, "mlLit", "C:\\raw"));
    }

    @Test
    void editedValuesParseBackToTheNewValue() throws Exception {
        String[] tricky = {"a\"\"\"b", "ends with quote\"", "back\\slash", "ctrl\u0001char", "''' triple", "é ü 日本"};
        for (String key : List.of("title", "literal", "multi", "mlLit")) {
            for (String value : tricky) {
                String out = edit(SAMPLE, key, value);
                assertEquals(value, get(TomlParser.parse(out), key).value(), key + " <- " + value);
            }
        }
    }

    @Test
    void listsKeepTheirLayout() throws Exception {
        assertEquals(replaced(SAMPLE, "list", "[ 4, 5 ]"), edit(SAMPLE, "list", List.of(4L, 5L)));
        assertEquals(replaced(SAMPLE, "strings", "[\"c\"]"), edit(SAMPLE, "strings", List.of("c")));
        assertEquals(replaced(SAMPLE, "list", "[]"), edit(SAMPLE, "list", List.of()));
        String multi = edit(SAMPLE, "multiLineList", List.of("x", "y", "z"));
        assertEquals(replaced(SAMPLE, "multiLineList", "[\n    \"x\",\n    \"y\",\n    \"z\",\n]"), multi);
        assertEquals(List.of("x", "y", "z"), get(TomlParser.parse(multi), "multiLineList").value());
    }

    @Test
    void invalidUtf8IsRefused() {
        byte[] latin1 = "name = \"caf\u00e9\"".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(CharacterCodingException.class, () -> SourceText.decode(latin1));
    }

    private static byte[] withBom(byte[] body) {
        byte[] out = new byte[body.length + 3];
        out[0] = (byte) 0xEF;
        out[1] = (byte) 0xBB;
        out[2] = (byte) 0xBF;
        System.arraycopy(body, 0, out, 3, body.length);
        return out;
    }
}

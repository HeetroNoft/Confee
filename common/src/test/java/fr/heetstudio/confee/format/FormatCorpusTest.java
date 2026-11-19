package fr.heetstudio.confee.format;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Optional check against real config files of every supported format (read only).
 * Run with {@code CONFEE_CORPUS=<dir>} and optionally {@code CONFEE_CORPUS_EXT=json,yaml}: every matching file must
 * round-trip byte for byte, and accept a one-value edit (scalars and lists) that parses back to the new value.
 */
class FormatCorpusTest {

    @Test
    void realFilesRoundTrip() throws Exception {
        String dir = System.getenv("CONFEE_CORPUS");
        Assumptions.assumeTrue(dir != null && !dir.isBlank(), "CONFEE_CORPUS not set");
        String only = System.getenv("CONFEE_CORPUS_EXT");
        List<Path> files;
        try (Stream<Path> walk = Files.walk(Path.of(dir))) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> Formats.forFile(p).isPresent())
                    .filter(p -> only == null || only.isBlank() || List.of(only.split(",")).contains(Formats.extension(p)))
                    .filter(p -> p.toString().contains("config"))
                    .toList();
        }
        Map<String, int[]> stats = new TreeMap<>();
        List<String> unreadable = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Path file : files) {
            FormatAdapter adapter = Formats.forFile(file).orElseThrow();
            int[] stat = stats.computeIfAbsent(adapter.name(), k -> new int[3]);
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length > 1024 * 1024) {
                continue;
            }
            SourceText source;
            ParsedDocument doc;
            try {
                source = SourceText.decode(bytes);
                doc = adapter.parse(source);
            } catch (FormatException | CharacterCodingException e) {
                stat[1]++;
                unreadable.add(file + ": " + e.getMessage());
                continue;
            }
            try {
                assertArrayEquals(bytes, source.encode(doc.edit(Map.of(), adapter.values())), "round trip");
                for (ParsedValue value : doc.values()) {
                    if (!value.editable()) {
                        continue;
                    }
                    Object changed = changed(value);
                    if (changed == null) {
                        continue;
                    }
                    String out = doc.edit(Map.of(value, changed), adapter.values());
                    ParsedDocument again = adapter.parse(new SourceText(out, source.bom(), source.lineSeparator()));
                    ParsedValue reparsed = again.find(value.path()).orElseThrow(() -> new AssertionError("lost " + value.joinedPath()));
                    assertTrue(ParsedDocument.sameValue(reparsed.value(), changed),
                            value.joinedPath() + ": expected " + changed + ", got " + reparsed.value());
                    assertEquals(value.kind(), reparsed.kind(), value.joinedPath() + " kind");
                }
                checkNested(adapter, source, doc);
                stat[0]++;
            } catch (AssertionError | RuntimeException | FormatException e) {
                stat[2]++;
                failures.add(file + ": " + e.getMessage());
            }
        }
        stats.forEach((name, s) -> System.out.println("[FormatCorpusTest] " + name + ": " + s[0] + " OK, "
                + s[1] + " unreadable, " + s[2] + " FAILED"));
        unreadable.forEach(u -> System.out.println("  unreadable: " + u));
        failures.forEach(f -> System.out.println("  FAILED: " + f));
        assertTrue(failures.isEmpty(), failures.size() + " files failed, see the output");
    }

    /**
     * Spec 8.2 bis: every value inside list elements accepts a one-value edit, and every list accepts adding, removing
     * and moving an element (the result must still parse, with one element more or less).
     */
    private static void checkNested(FormatAdapter adapter, SourceText source, ParsedDocument doc) throws FormatException {
        String text = source.text();
        for (ParsedValue value : doc.nestedValues()) {
            Object changed = value.editable() ? changed(value) : null;
            if (changed == null) {
                continue;
            }
            String out = doc.edit(Map.of(value, changed), adapter.values());
            ParsedDocument again = adapter.parse(new SourceText(out, source.bom(), source.lineSeparator()));
            ParsedValue reparsed = again.find(value.path()).orElseThrow(() -> new AssertionError("lost " + value.joinedPath()));
            assertTrue(ParsedDocument.sameValue(reparsed.value(), changed), value.joinedPath() + " nested edit");
        }
        for (ParsedList list : doc.lists()) {
            int n = list.items().size();
            String name = String.join(".", list.path());
            assertEquals(n + 1, size(adapter, source, ListEdits.duplicateLast(text, list), list), name + " add");
            if (n > 1) {
                assertEquals(n - 1, size(adapter, source, ListEdits.remove(text, list, 0), list), name + " remove first");
                assertEquals(n - 1, size(adapter, source, ListEdits.remove(text, list, n - 1), list), name + " remove last");
                assertEquals(n, size(adapter, source, ListEdits.move(text, list, 0, 1), list), name + " move");
            }
        }
    }

    private static int size(FormatAdapter adapter, SourceText source, String text, ParsedList list) throws FormatException {
        ParsedDocument doc = adapter.parse(new SourceText(text, source.bom(), source.lineSeparator()));
        return doc.list(list.path()).map(l -> l.items().size()).orElse(0);
    }

    private static Object changed(ParsedValue value) {
        return switch (value.kind()) {
            case BOOLEAN -> !(Boolean) value.value();
            case INTEGER -> (Long) value.value() + 1;
            case FLOAT -> {
                double d = (Double) value.value();
                yield Double.isFinite(d) ? d + 0.5 : null;
            }
            case STRING -> value.value() + "!";
            case LIST -> {
                List<Object> list = new ArrayList<>((List<?>) value.value());
                if (list.isEmpty()) {
                    yield null;
                }
                list.add(list.get(0));
                yield list;
            }
            default -> null;
        };
    }
}

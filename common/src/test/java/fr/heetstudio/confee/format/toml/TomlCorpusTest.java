package fr.heetstudio.confee.format.toml;

import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.SourceText;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Optional check against real config files (read only).
 * Run with {@code CONFEE_TOML_CORPUS=<dir>}: every {@code .toml} below it must parse, round-trip byte for byte,
 * and accept a one-value edit that parses back.
 */
class TomlCorpusTest {

    @Test
    void realFilesRoundTrip() throws Exception {
        String dir = System.getenv("CONFEE_TOML_CORPUS");
        Assumptions.assumeTrue(dir != null && !dir.isBlank(), "CONFEE_TOML_CORPUS not set");
        List<Path> files;
        try (Stream<Path> walk = Files.walk(Path.of(dir))) {
            files = walk.filter(p -> p.toString().endsWith(".toml") && Files.isRegularFile(p)).toList();
        }
        List<String> unreadable = new ArrayList<>();
        int checked = 0;
        for (Path file : files) {
            byte[] bytes = Files.readAllBytes(file);
            SourceText source;
            ParsedDocument doc;
            try {
                source = SourceText.decode(bytes);
                doc = TomlParser.parse(source);
            } catch (FormatException | java.nio.charset.CharacterCodingException e) {
                unreadable.add(file + ": " + e.getMessage());
                continue;
            }
            assertArrayEquals(bytes, source.encode(doc.edit(Map.of(), TomlValues.INSTANCE)), file.toString());
            for (ParsedValue value : doc.values()) {
                if (value.editable() && value.kind().isScalar()) {
                    Object changed = switch (value.kind()) {
                        case BOOLEAN -> !(Boolean) value.value();
                        case INTEGER -> (Long) value.value() + 1;
                        case FLOAT -> (Double) value.value() + 0.5;
                        default -> value.value() + "!";
                    };
                    String out = doc.edit(Map.of(value, changed), TomlValues.INSTANCE);
                    ParsedDocument again = TomlParser.parse(out);
                    assertEquals(changed, again.find(value.path()).orElseThrow().value(), file + " " + value.joinedPath());
                    assertEquals(doc.values().size(), again.values().size(), file.toString());
                }
            }
            checked++;
        }
        System.out.println("[TomlCorpusTest] " + checked + " files OK, " + unreadable.size() + " unreadable");
        unreadable.forEach(u -> System.out.println("  unreadable: " + u));
    }
}

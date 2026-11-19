package fr.heetstudio.confee.format;

import fr.heetstudio.confee.core.ConfigSchema;
import fr.heetstudio.confee.core.Node;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.Section;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves a Confee config while keeping what the player wrote by hand (spec 5.3).
 * <p>The writer of the format first produces the file Confee would write from scratch. Then:</p>
 * <ul>
 *     <li><b>in place</b> when every option is already in the file: only the changed values and the changed generated
 *     comments ({@link CommentMarker}) are replaced, everything else is copied character for character (hand-written
 *     comments, blank lines, key order, inline comments, unknown keys);</li>
 *     <li>otherwise (an option was added to the config, or the file was moved around too much) the new file is
 *     written and the hand-written comment lines directly above each option and each section header are put back.</li>
 * </ul>
 * <p>Internal: may change without notice.</p>
 */
public final class ConfeeFileMerger {

    private ConfeeFileMerger() {
    }

    /**
     * @param fresh the whole file as the format writer produced it, with the line separator of {@code previous}
     * @return the text to write
     */
    public static String merge(FormatAdapter adapter, ConfigSchema schema, ParsedDocument previous, String fresh) {
        String nl = previous.source().lineSeparator();
        ParsedDocument generated;
        try {
            generated = adapter.parseForSchema(new SourceText(fresh, false, nl));
        } catch (FormatException e) {
            return fresh;
        }
        List<List<String>> paths = new ArrayList<>();
        collectPaths(schema.root(), new ArrayList<>(), paths);
        Merge merge = new Merge(adapter, previous, generated, paths, nl);
        String inPlace = merge.inPlace();
        if (inPlace != null && sameValues(adapter, inPlace, generated, paths, nl)) {
            return inPlace;
        }
        if (adapter.commentPrefix() == null) {
            return fresh;
        }
        String carried = new Merge(adapter, previous, generated, paths, nl).carry();
        return carried != null ? carried : fresh;
    }

    private static void collectPaths(Section section, List<String> prefix, List<List<String>> out) {
        for (Node child : section.children()) {
            List<String> path = new ArrayList<>(prefix);
            if (child instanceof Option<?> option) {
                if (option.get() != null) {
                    path.add(option.key());
                    out.add(List.copyOf(path));
                }
            } else if (child instanceof Section sub) {
                path.add(sub.key());
                collectPaths(sub, path, out);
            }
        }
    }

    /** Safety net: the in-place text must hold exactly the values of the generated file. */
    private static boolean sameValues(FormatAdapter adapter, String text, ParsedDocument generated,
                                      List<List<String>> paths, String nl) {
        ParsedDocument check;
        try {
            check = adapter.parseForSchema(new SourceText(text, false, nl));
        } catch (FormatException e) {
            return false;
        }
        Lines checkLines = new Lines(check.source().text());
        Lines genLines = new Lines(generated.source().text());
        for (List<String> path : paths) {
            Located expected = locate(generated, genLines, path);
            Located actual = locate(check, checkLines, path);
            if (expected == null || actual == null || !sameParts(actual, expected, path)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Where an option is in a file: the line of its key and its value(s). A map or a list of objects may be several
     * values (nested keys, or flat "path.key" keys in properties).
     */
    private record Located(int keyLine, @Nullable ParsedValue single, List<ParsedValue> parts) {
    }

    @Nullable
    private static Located locate(ParsedDocument document, Lines lines, List<String> path) {
        ParsedValue single = find(document, path);
        if (single != null && single.entryStart() >= 0) {
            return new Located(lines.lineOf(single.entryStart()), single, List.of(single));
        }
        String flat = String.join(".", path);
        List<ParsedValue> parts = new ArrayList<>(document.valuesUnder(path));
        for (ParsedValue value : document.values()) {
            if (value.path().size() == 1 && value.key().startsWith(flat + ".")) {
                parts.add(value);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        int first = parts.stream().mapToInt(ParsedValue::start).min().orElse(0);
        int line = lines.lineOf(first);
        String key = path.get(path.size() - 1);
        // the key line is the value line (inline table, flat keys) or a line above it (block map in YAML)
        for (int k = line; k >= 0 && k >= line - 64; k--) {
            String stripped = lines.stripped(k);
            if (stripped.startsWith(key) || stripped.startsWith("\"" + key) || stripped.startsWith("'" + key)
                    || stripped.startsWith(flat)) {
                return new Located(k, null, parts);
            }
        }
        return new Located(line, null, parts);
    }

    /** Whether two locations hold the same values (relative paths and values, in document order). */
    private static boolean sameParts(Located a, Located b, List<String> path) {
        if (a.parts().size() != b.parts().size()) {
            return false;
        }
        for (int i = 0; i < a.parts().size(); i++) {
            ParsedValue x = a.parts().get(i);
            ParsedValue y = b.parts().get(i);
            if (!relative(x, path).equals(relative(y, path)) || !ParsedDocument.sameValue(x.value(), y.value())) {
                return false;
            }
        }
        return true;
    }

    private static String relative(ParsedValue value, List<String> path) {
        if (value.path().size() > path.size()) {
            return String.join(".", value.path().subList(path.size(), value.path().size()));
        }
        String flat = String.join(".", path);
        return value.key().length() > flat.length() ? value.key().substring(flat.length()) : "";
    }

    /** Nested formats split the path; flat ones (properties) keep "cat.key" as one key (see SchemaBinder). */
    @Nullable
    static ParsedValue find(ParsedDocument document, List<String> path) {
        return document.find(path).or(() -> document.find(List.of(String.join(".", path)))).orElse(null);
    }

    // ------------------------------------------------------------------ text lines

    /** The lines of a text with their offsets. */
    static final class Lines {
        final String text;
        private final int[] starts;

        Lines(String text) {
            this.text = text;
            List<Integer> list = new ArrayList<>();
            list.add(0);
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n' && i + 1 < text.length()) {
                    list.add(i + 1);
                }
            }
            starts = list.stream().mapToInt(Integer::intValue).toArray();
        }

        int count() {
            return starts.length;
        }

        int start(int line) {
            return starts[line];
        }

        /** Offset just after the line break of the line (end of text for the last line). */
        int next(int line) {
            return line + 1 < starts.length ? starts[line + 1] : text.length();
        }

        /** The line without its line break. */
        String raw(int line) {
            int end = next(line);
            if (end > starts[line] && text.charAt(end - 1) == '\n') {
                end--;
                if (end > starts[line] && text.charAt(end - 1) == '\r') {
                    end--;
                }
            }
            return text.substring(starts[line], end);
        }

        String stripped(int line) {
            return raw(line).strip();
        }

        int lineOf(int offset) {
            int index = Arrays.binarySearch(starts, offset);
            return index >= 0 ? index : -index - 2;
        }
    }

    // ------------------------------------------------------------------ merge

    private record Edit(int start, int end, String text) {
    }

    private static final class Merge {
        private final FormatAdapter adapter;
        private final ParsedDocument previous;
        private final ParsedDocument generated;
        private final List<List<String>> paths;
        private final String nl;
        @Nullable
        private final String prefix;
        private final Lines prev;
        private final Lines gen;
        private final boolean[] prevUsed;
        private final boolean[] genUsed;
        private final List<Edit> edits = new ArrayList<>();

        Merge(FormatAdapter adapter, ParsedDocument previous, ParsedDocument generated, List<List<String>> paths,
              String nl) {
            this.adapter = adapter;
            this.previous = previous;
            this.generated = generated;
            this.paths = paths;
            this.nl = nl;
            this.prefix = adapter.commentPrefix();
            this.prev = new Lines(previous.source().text());
            this.gen = new Lines(generated.source().text());
            this.prevUsed = new boolean[prev.count()];
            this.genUsed = new boolean[gen.count()];
        }

        // -------------------------------------------------------------- in place

        /** @return the previous text with only the changed values and generated comments replaced, or null */
        @Nullable
        String inPlace() {
            for (List<String> path : paths) {
                Located p = locate(previous, prev, path);
                Located f = locate(generated, gen, path);
                if (p == null || f == null) {
                    return null; // an option missing from the file
                }
                ParsedValue ps = p.single();
                ParsedValue fs = f.single();
                if (ps != null && fs != null && ps.editable()) {
                    if (ps.kind() != fs.kind() || !ParsedDocument.sameValue(ps.value(), fs.value())) {
                        edits.add(new Edit(ps.start(), ps.end(), valueText(ps, fs)));
                    }
                } else if (!sameParts(p, f, path)) {
                    return null; // a changed map or list of objects: the file is written again
                }
                if (prefix != null) {
                    replaceGenerated(block(prev, p.keyLine(), prevUsed), block(gen, f.keyLine(), genUsed),
                            prev.start(p.keyLine()));
                }
            }
            if (prefix != null) {
                sectionComments(true);
            }
            return apply();
        }

        private String valueText(ParsedValue p, ParsedValue f) {
            String raw = generated.rawText(f);
            if (p.kind() != f.kind()) {
                return raw;
            }
            try {
                return adapter.values().write(p, f.value(), nl);
            } catch (RuntimeException e) {
                return raw;
            }
        }

        /** Replaces the generated lines of {@code oldBlock} by those of {@code newBlock}, if they differ. */
        private void replaceGenerated(List<Integer> oldBlock, List<Integer> newBlock, int insertAt) {
            List<Integer> oldMarks = generatedLines(prev, oldBlock);
            List<Integer> newMarks = generatedLines(gen, newBlock);
            if (strippedTexts(prev, oldMarks).equals(strippedTexts(gen, newMarks))) {
                return;
            }
            StringBuilder text = new StringBuilder();
            for (int line : newMarks) {
                text.append(gen.raw(line)).append(nl);
            }
            if (oldMarks.isEmpty()) {
                edits.add(new Edit(insertAt, insertAt, text.toString()));
                return;
            }
            for (int i = 0; i < oldMarks.size(); i++) {
                int line = oldMarks.get(i);
                edits.add(new Edit(prev.start(line), prev.next(line), i == 0 ? text.toString() : ""));
            }
        }

        // -------------------------------------------------------------- fallback

        /** The generated file with the hand-written comment lines of the previous file put back, or null. */
        @Nullable
        String carry() {
            for (List<String> path : paths) {
                Located p = locate(previous, prev, path);
                Located f = locate(generated, gen, path);
                if (p == null || f == null) {
                    continue;
                }
                List<Integer> oldBlock = block(prev, p.keyLine(), prevUsed);
                List<Integer> newBlock = block(gen, f.keyLine(), genUsed);
                insertHand(oldBlock, newBlock, f.keyLine());
            }
            sectionComments(false);
            // edits target the generated text here
            return applyTo(gen.text);
        }

        private void insertHand(List<Integer> oldBlock, List<Integer> newBlock, int genAnchor) {
            List<String> present = strippedTexts(gen, newBlock);
            StringBuilder text = new StringBuilder();
            for (int line : oldBlock) {
                String stripped = prev.stripped(line);
                if (!CommentMarker.isGenerated(stripped, prefix) && !present.contains(stripped)) {
                    text.append(prev.raw(line)).append(nl);
                }
            }
            if (text.length() > 0) {
                int at = gen.start(newBlock.isEmpty() ? genAnchor : newBlock.get(0));
                edits.add(new Edit(at, at, text.toString()));
            }
        }

        // -------------------------------------------------------------- sections and file header

        /**
         * Comment blocks that are not above an option: above a section header (matched by the text of the header line)
         * or at the top of the file.
         *
         * @param inPlace true: update the generated lines of the previous file; false: put the hand-written lines of
         *                the previous file back in the generated one
         */
        private void sectionComments(boolean inPlace) {
            // file header: the comment lines at the very top that do not belong to an option
            List<Integer> prevTop = topBlock(prev, prevUsed);
            List<Integer> genTop = topBlock(gen, genUsed);
            if (inPlace) {
                if (!prevTop.isEmpty() || !genTop.isEmpty()) {
                    List<Integer> newMarks = generatedLines(gen, genTop);
                    boolean blankAfter = !genTop.isEmpty() && genTop.get(genTop.size() - 1) + 1 < gen.count()
                            && gen.stripped(genTop.get(genTop.size() - 1) + 1).isEmpty();
                    if (generatedLines(prev, prevTop).isEmpty() && !newMarks.isEmpty() && blankAfter) {
                        // a header comment added to the config: keep the blank line that follows it
                        StringBuilder text = new StringBuilder();
                        newMarks.forEach(line -> text.append(gen.raw(line)).append(nl));
                        edits.add(new Edit(0, 0, text.append(nl).toString()));
                    } else {
                        replaceGenerated(prevTop, genTop, 0);
                    }
                }
            } else {
                insertHand(prevTop, genTop, 0);
            }
            // other blocks, matched by the line that follows them
            Map<String, List<Integer>> genAnchors = anchors(gen, genUsed);
            Map<String, List<Integer>> prevAnchors = anchors(prev, prevUsed);
            for (Map.Entry<String, List<Integer>> entry : genAnchors.entrySet()) {
                List<Integer> prevLines = prevAnchors.get(entry.getKey());
                if (prevLines == null) {
                    continue;
                }
                for (int i = 0; i < Math.min(prevLines.size(), entry.getValue().size()); i++) {
                    int prevAnchor = prevLines.get(i);
                    int genAnchor = entry.getValue().get(i);
                    List<Integer> oldBlock = block(prev, prevAnchor, prevUsed);
                    List<Integer> newBlock = block(gen, genAnchor, genUsed);
                    if (inPlace) {
                        replaceGenerated(oldBlock, newBlock, prev.start(prevAnchor));
                    } else {
                        insertHand(oldBlock, newBlock, genAnchor);
                    }
                }
            }
        }

        /** Non-blank, non-comment lines that have a comment block above them not used yet, by stripped text. */
        private Map<String, List<Integer>> anchors(Lines lines, boolean[] used) {
            Map<String, List<Integer>> out = new HashMap<>();
            for (int line = 1; line < lines.count(); line++) {
                String stripped = lines.stripped(line);
                if (stripped.isEmpty() || isComment(stripped)) {
                    continue;
                }
                // every occurrence is listed, so the n-th header of one file matches the n-th of the other
                out.computeIfAbsent(stripped, k -> new ArrayList<>()).add(line);
            }
            out.values().removeIf(list -> list.stream().noneMatch(l -> isComment(lines.stripped(l - 1)) && !used[l - 1]));
            return out;
        }

        private List<Integer> topBlock(Lines lines, boolean[] used) {
            List<Integer> out = new ArrayList<>();
            for (int line = 0; line < lines.count() && isComment(lines.stripped(line)) && !used[line]; line++) {
                out.add(line);
            }
            if (!out.isEmpty()) {
                int after = out.get(out.size() - 1) + 1;
                if (after < lines.count() && !lines.stripped(after).isEmpty()) {
                    return List.of(); // directly above something: handled with that line
                }
            }
            for (int line : out) {
                used[line] = true;
            }
            return out;
        }

        // -------------------------------------------------------------- helpers

        /** The comment lines directly above {@code line} (no blank line between), marked as used. */
        private List<Integer> block(Lines lines, int line, boolean[] used) {
            List<Integer> out = new ArrayList<>();
            for (int k = line - 1; k >= 0 && !used[k] && isComment(lines.stripped(k)); k--) {
                out.add(0, k);
            }
            for (int k : out) {
                used[k] = true;
            }
            return out;
        }

        private boolean isComment(String stripped) {
            return prefix != null && stripped.startsWith(prefix);
        }

        private List<Integer> generatedLines(Lines lines, List<Integer> block) {
            List<Integer> out = new ArrayList<>();
            for (int line : block) {
                if (CommentMarker.isGenerated(lines.stripped(line), prefix)) {
                    out.add(line);
                }
            }
            return out;
        }

        private static List<String> strippedTexts(Lines lines, List<Integer> block) {
            List<String> out = new ArrayList<>();
            for (int line : block) {
                out.add(lines.stripped(line));
            }
            return out;
        }

        @Nullable
        private String apply() {
            return applyTo(prev.text);
        }

        @Nullable
        private String applyTo(String text) {
            edits.sort(Comparator.comparingInt(Edit::start).thenComparingInt(Edit::end));
            StringBuilder out = new StringBuilder(text.length() + 64);
            int cursor = 0;
            for (Edit edit : edits) {
                if (edit.start() < cursor) {
                    return null; // overlapping edits: give up
                }
                out.append(text, cursor, edit.start()).append(edit.text());
                cursor = edit.end();
            }
            return out.append(text, cursor, text.length()).toString();
        }
    }
}

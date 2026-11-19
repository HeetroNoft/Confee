package fr.heetstudio.confee.external;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.api.ConfigFormat;
import fr.heetstudio.confee.api.ConfigScope;
import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.core.ConfigSchema;
import fr.heetstudio.confee.core.ConfigSource;
import fr.heetstudio.confee.core.EditableConfig;
import fr.heetstudio.confee.core.FileStamp;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.Section;
import fr.heetstudio.confee.format.AtomicFiles;
import fr.heetstudio.confee.core.ListActions;
import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ListEdits;
import fr.heetstudio.confee.format.ParsedList;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.ValueKind;
import fr.heetstudio.confee.format.FormatAdapter;
import fr.heetstudio.confee.format.Formats;
import fr.heetstudio.confee.format.hocon.HoconParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Config file of a mod that does not use Confee (spec 8), edited in place.
 * <ul>
 *   <li>Saving only replaces the text of the changed values (spec 7.2), atomically.</li>
 *   <li>Before the first change, the original file is copied to {@code config/confee/backups/<relative path>};
 *       its values are the "original values" used by the reset buttons (spec 8.3).</li>
 *   <li>Never throws because of the file: an unreadable file gives a {@link Status} and a reason.</li>
 * </ul>
 * <p>Internal: may change without notice.</p>
 */
public final class ExternalConfig implements EditableConfig {

    public enum Status { OK, UNSUPPORTED_FORMAT, UNREADABLE }

    private final String modId;
    private final Path file;
    private final Path configDir;
    private final boolean backups;
    private Status status = Status.OK;
    @Nullable
    private String problem;
    @Nullable
    private ConfigSchema schema;
    /** The file, then the files it includes (HOCON); each with its text on disk and its original copy. */
    private List<Member> members = List.of();
    private Map<Option<?>, RawSchemaBuilder.Binding> bindings = Map.of();
    /** Texts changed in memory by list changes (spec 8.2 bis), written on "Save". */
    private final Map<Path, String> working = new LinkedHashMap<>();
    /** Empty lists that can get an element again, and the text that gives them one ({@link #emptyListText}). */
    private Map<List<String>, String> templates = Map.of();

    /**
     * One file of the config.
     *
     * @param file     the file
     * @param document its values (from {@link #working} when changed in memory)
     * @param diskText its text on disk
     * @param stamp    its size and date when read
     * @param original its copy made before Confee's first change, parsed, or null
     */
    private record Member(Path file, ParsedDocument document, String diskText, FileStamp stamp,
                          @Nullable ParsedDocument original, List<String> prefix) {
    }

    private ExternalConfig(String modId, Path file, Path configDir, boolean backups) {
        this.modId = modId;
        this.file = file.toAbsolutePath().normalize();
        this.configDir = configDir.toAbsolutePath().normalize();
        this.backups = backups;
    }

    /**
     * @param backups whether to copy the original file before the first change ({@code backupExternalFiles})
     */
    public static ExternalConfig open(String modId, Path file, Path configDir, boolean backups) {
        ExternalConfig config = new ExternalConfig(modId, file, configDir, backups);
        config.read();
        return config;
    }

    /** File formats the editor can read (spec 7.1). Others get the "cannot read" screen (spec 10.4 D). */
    public static boolean isSupported(Path file) {
        return Formats.forFile(file).isPresent();
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Reads the files from disk, forgetting what was changed in memory. */
    private void read() {
        working.clear();
        if (!isSupported(file)) {
            status = Status.UNSUPPORTED_FORMAT;
            problem = extension(file).isEmpty() ? "unknown format" : "." + extension(file);
            return;
        }
        try {
            members = load(Map.of());
        } catch (CharacterCodingException e) {
            fail("not UTF-8 text");
            return;
        } catch (FormatException e) {
            fail(e.getMessage());
            return;
        } catch (IOException e) {
            fail(e.getMessage());
            return;
        }
        status = Status.OK;
        problem = null;
        buildSchema();
    }

    /** Reads the config, with the texts of {@code texts} in place of the files' content. */
    private List<Member> load(Map<Path, String> texts) throws IOException, FormatException {
        FormatAdapter adapter = Formats.forFile(file).orElseThrow();
        SourceText main = source(file, texts);
        if (adapter != Formats.HOCON) {
            ParsedDocument document = adapter.parse(main);
            return List.of(member(file, document, List.of()));
        }
        HoconParser.IncludeResolver resolver = (from, name) -> {
            Path found = includedFile(from == null ? file : from, name);
            if (found == null) {
                return null;
            }
            try {
                return new HoconParser.Source(found, source(found, texts));
            } catch (IOException e) {
                throw new FormatException("Cannot read the included file " + name + ": " + e.getMessage(), 1, 1);
            }
        };
        List<Member> out = new ArrayList<>();
        for (HoconParser.Member m : HoconParser.parse(file, main, resolver)) {
            out.add(member(Objects.requireNonNull(m.file()).toAbsolutePath().normalize(), m.document(), m.prefix()));
        }
        return out;
    }

    /** The file of {@code include "name"} next to the including file, inside the config folder; null when missing. */
    @Nullable
    private Path includedFile(Path from, String name) {
        Path base = from.toAbsolutePath().normalize().getParent();
        for (String candidate : List.of(name, name + ".conf", name + ".json")) {
            Path found = base.resolve(candidate).toAbsolutePath().normalize();
            // never write outside the config folder
            if (found.startsWith(configDir) && Files.isRegularFile(found)) {
                return found;
            }
        }
        return null;
    }

    private SourceText source(Path path, Map<Path, String> texts) throws IOException {
        SourceText disk = SourceText.decode(Files.readAllBytes(path));
        String text = texts.get(path);
        return text == null ? disk : new SourceText(text, disk.bom(), disk.lineSeparator());
    }

    private Member member(Path path, ParsedDocument document, List<String> prefix) throws IOException {
        String diskText = SourceText.decode(Files.readAllBytes(path)).text();
        return new Member(path, document, diskText, FileStamp.of(path), readOriginal(path, prefix), List.copyOf(prefix));
    }

    private void buildSchema() {
        List<ParsedDocument> documents = new ArrayList<>();
        List<ParsedDocument> originals = new ArrayList<>();
        for (Member m : members) {
            documents.add(m.document());
            originals.add(m.original());
        }
        templates = new LinkedHashMap<>();
        for (Member m : members) {
            List<ParsedValue> values = new ArrayList<>(m.document().values());
            values.addAll(m.document().nestedValues());
            for (ParsedValue value : values) {
                if (value.kind() == ValueKind.LIST && ((List<?>) value.value()).isEmpty()) {
                    String text = emptyListText(m, value);
                    if (text != null) {
                        templates.put(value.path(), text);
                    }
                }
            }
        }
        RawSchemaBuilder.Result result = RawSchemaBuilder.build(documents, originals, templates.keySet(), this::listActions);
        bindings = result.bindings();
        schema = new ConfigSchema(modId, readableName(), ConfigScope.CLIENT, confeeFormat(), file, Theme.DEFAULT,
                ConfigSource.RAW_FILE, result.root());
    }

    // ------------------------------------------------------------------ list elements (spec 8.2 bis)

    private ListActions listActions(List<String> path) {
        return new ListActions() {
            @Override
            public boolean add(Map<Option<?>, Object> pending) {
                if (templates.containsKey(path)) {
                    // an emptied list: an element of the original file is the model
                    String filled = templates.get(path);
                    return changeText(path, pending, owner -> owner.document().find(path)
                            .filter(v -> v.kind() == ValueKind.LIST && ((List<?>) v.value()).isEmpty())
                            .map(v -> {
                                String text = owner.document().source().text();
                                return text.substring(0, v.start()) + filled + text.substring(v.end());
                            }).orElse(null));
                }
                return changeList(path, pending, ListEdits::duplicateLast);
            }

            @Override
            public boolean remove(int index, Map<Option<?>, Object> pending) {
                return changeList(path, pending, (text, list) -> ListEdits.remove(text, list, index));
            }

            @Override
            public boolean move(int index, int delta, Map<Option<?>, Object> pending) {
                return changeList(path, pending, (text, list) -> ListEdits.move(text, list, index, delta));
            }
        };
    }

    /**
     * Keeps the values edited on screen, then changes the list, in memory only: "Save" writes the result, "Cancel"
     * reads the files again. The schema is rebuilt (the screen opens it again).
     */
    private boolean changeList(List<String> path, Map<Option<?>, Object> pending,
                               BiFunction<String, ParsedList, String> change) {
        return changeText(path, pending, owner -> owner.document().list(path)
                .map(list -> change.apply(owner.document().source().text(), list)).orElse(null));
    }

    /**
     * Keeps the values edited on screen, then changes the text of the file that holds {@code path} ({@code change}
     * gives null for a file that does not hold it), in memory only.
     */
    private boolean changeText(List<String> path, Map<Option<?>, Object> pending,
                               java.util.function.Function<Member, String> change) {
        if (status != Status.OK || members.isEmpty()) {
            return false;
        }
        try {
            Map<Path, String> texts = editedTexts(pending);
            List<Member> withValues = load(texts);
            boolean done = false;
            for (Member owner : withValues) {
                String changedText = change.apply(owner);
                if (changedText != null) {
                    texts.put(owner.file(), changedText);
                    done = true;
                    break;
                }
            }
            if (!done) {
                throw new IllegalStateException("no list " + String.join(".", path));
            }
            List<Member> changed = load(texts);
            working.clear();
            for (Member m : changed) {
                if (!m.document().source().text().equals(m.diskText())) {
                    working.put(m.file(), m.document().source().text());
                }
            }
            members = changed;
        } catch (IOException | FormatException | RuntimeException e) {
            ConfeeLog.warn("Cannot change the list {} of {}: {}", String.join(".", path), relativePath(), e.getMessage());
            return false;
        }
        buildSchema();
        return true;
    }

    /**
     * What replaces an empty list ({@code []}) to give it one element: a copy of the first element the list had in the
     * original copy of the file, else in the file on disk; null when there is none, or when it cannot be put there
     * (a YAML element must keep the column it was written at).
     */
    @Nullable
    private String emptyListText(Member member, ParsedValue empty) {
        List<ParsedDocument> sources = new ArrayList<>();
        if (member.original() != null) {
            sources.add(member.original());
        }
        FormatAdapter adapter = Formats.forFile(file).orElseThrow();
        try {
            SourceText disk = new SourceText(member.diskText(), false, SourceText.detectLineSeparator(member.diskText()));
            sources.add(adapter == Formats.HOCON ? HoconParser.parse(disk, member.prefix()) : adapter.parse(disk));
        } catch (FormatException e) {
            // the file on disk cannot be read any more: only the original copy can help
        }
        String text = member.document().source().text();
        for (ParsedDocument source : sources) {
            ParsedList list = source.list(empty.path()).orElse(null);
            if (list == null || list.items().isEmpty()) {
                continue;
            }
            ParsedList.Item first = list.items().get(0);
            String sourceText = source.source().text();
            String element = sourceText.substring(first.valueStart(), first.end());
            if (adapter != Formats.YAML) {
                return "[" + element + "]";
            }
            // YAML: "- ..." lines, at the column of the original element, on a line of their own
            int lineStart = text.lastIndexOf('\n', empty.start() - 1) + 1;
            int sourceLineStart = sourceText.lastIndexOf('\n', first.valueStart() - 1) + 1;
            if (text.substring(lineStart, empty.start()).isBlank()
                    && empty.start() - lineStart == first.valueStart() - sourceLineStart) {
                return element;
            }
        }
        return null;
    }

    /** The text of every file with these option values written in. */
    private Map<Path, String> editedTexts(Map<Option<?>, Object> values) {
        List<Map<ParsedValue, Object>> changes = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            changes.add(new LinkedHashMap<>());
        }
        values.forEach((option, value) -> {
            RawSchemaBuilder.Binding binding = bindings.get(option);
            if (binding != null) {
                changes.get(binding.file()).put(binding.value(), toFileValue(value));
            }
        });
        Map<Path, String> texts = new LinkedHashMap<>();
        FormatAdapter adapter = Formats.forFile(file).orElseThrow();
        for (int i = 0; i < members.size(); i++) {
            Member m = members.get(i);
            texts.put(m.file(), m.document().edit(changes.get(i), adapter.values()));
        }
        return texts;
    }

    @Override
    public boolean hasUnsavedStructure() {
        return !working.isEmpty();
    }

    @Override
    public void discard() {
        if (!working.isEmpty()) {
            read();
        }
    }

    /** Closest Confee format, for the model only (the file keeps its own format). */
    private ConfigFormat confeeFormat() {
        return switch (extension(file)) {
            case "json", "jsonc" -> ConfigFormat.JSON;
            case "json5" -> ConfigFormat.JSON5;
            case "yml", "yaml" -> ConfigFormat.YAML;
            case "properties", "ini", "cfg" -> ConfigFormat.PROPERTIES;
            default -> ConfigFormat.TOML;
        };
    }

    private void fail(String reason) {
        status = Status.UNREADABLE;
        problem = reason;
        members = List.of();
        ConfeeLog.warn("Cannot read {} of mod {}: {}", relativePath(), modId, reason);
    }

    /** The backup of a file made before Confee's first change, parsed; null when there is none. */
    @Nullable
    private ParsedDocument readOriginal(Path path, List<String> prefix) {
        Path backup = backupPath(path);
        if (!Files.isRegularFile(backup)) {
            return null;
        }
        try {
            SourceText text = SourceText.decode(Files.readAllBytes(backup));
            FormatAdapter adapter = Formats.forFile(file).orElseThrow();
            return adapter == Formats.HOCON ? HoconParser.parse(text, prefix) : adapter.parse(text);
        } catch (IOException | FormatException e) {
            ConfeeLog.warn("Ignoring unreadable backup {}: {}", backup, e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ EditableConfig

    @Override
    public ConfigSchema schema() {
        return Objects.requireNonNull(schema, "file not readable");
    }

    /**
     * Surgical save of the values changed in memory (spec 7.2), in the file each value comes from (an included HOCON
     * file is written in place).
     */
    @Override
    public void save() {
        if (status != Status.OK || members.isEmpty()) {
            return;
        }
        Map<Option<?>, Object> values = new LinkedHashMap<>();
        bindings.keySet().forEach(option -> values.put(option, option.get()));
        Map<Path, String> texts;
        try {
            texts = editedTexts(values);
        } catch (IllegalArgumentException e) {
            ConfeeLog.error("Cannot save " + relativePath(), e);
            return;
        }
        for (Member m : members) {
            String text = texts.get(m.file());
            if (text == null || text.equals(m.diskText())) {
                continue;
            }
            String name = relativePath(m.file(), configDir);
            try {
                if (backups) {
                    backupOriginal(m.file());
                }
                AtomicFiles.write(m.file(), m.document().source().encode(text));
                ConfeeLog.info("Saved {} (mod {})", name, modId);
            } catch (IOException e) {
                ConfeeLog.error("Cannot write " + name, e);
            }
        }
        // positions changed: read again
        read();
    }

    /** Values of the model use Long / Double, like the parsed file: nothing to convert but integral types. */
    private static Object toFileValue(Object value) {
        return value instanceof Integer i ? (Object) i.longValue() : value;
    }

    /** Spec 8.3: copy of the original file at the first change, never overwritten afterwards. */
    private void backupOriginal(Path path) throws IOException {
        Path backup = backupPath(path);
        if (Files.exists(backup)) {
            return;
        }
        Files.createDirectories(backup.getParent());
        Files.copy(path, backup);
        ConfeeLog.info("Backed up the original {} to {}", relativePath(path, configDir), configDir.relativize(backup));
    }

    @Override
    public void fireOnChange() {
        // the mod does not use Confee: nothing to notify (changes apply on the next restart)
    }

    @Override
    public boolean changedOnDisk() {
        return status == Status.OK && members.stream().anyMatch(m -> !FileStamp.of(m.file()).equals(m.stamp()));
    }

    // ------------------------------------------------------------------ original file

    public boolean hasBackup() {
        return members.stream().anyMatch(m -> Files.isRegularFile(backupPath(m.file())))
                || Files.isRegularFile(backupPath());
    }

    /**
     * "Reset all" for a raw file (spec 8.3): puts the original copies back (the file and the files it includes).
     *
     * @return false when there is no backup or it cannot be copied
     */
    public boolean restoreOriginal() {
        List<Path> files = new ArrayList<>();
        files.add(file);
        members.forEach(m -> {
            if (!files.contains(m.file())) {
                files.add(m.file());
            }
        });
        boolean any = false;
        for (Path path : files) {
            Path backup = backupPath(path);
            if (!Files.isRegularFile(backup)) {
                continue;
            }
            try {
                AtomicFiles.write(path, Files.readAllBytes(backup));
                ConfeeLog.info("Restored the original {}", relativePath(path, configDir));
                any = true;
            } catch (IOException e) {
                ConfeeLog.error("Cannot restore " + relativePath(path, configDir), e);
                return false;
            }
        }
        if (any) {
            read();
        }
        return any;
    }

    public Path backupPath() {
        return backupPath(file);
    }

    private Path backupPath(Path path) {
        Path relative = path.startsWith(configDir) ? configDir.relativize(path) : path.getFileName();
        return configDir.resolve(ConfigDiscovery.CONFEE_DIR).resolve("backups").resolve(relative);
    }

    // ------------------------------------------------------------------ accessors

    public String modId() {
        return modId;
    }

    public Path file() {
        return file;
    }

    public Status status() {
        return status;
    }

    /** Why the file cannot be edited, or null. */
    @Nullable
    public String problem() {
        return problem;
    }

    /** Path relative to config/ ({@code mymod/client.toml}). */
    public String relativePath() {
        return relativePath(file, configDir);
    }

    public static String relativePath(Path file, Path configDir) {
        Path f = file.toAbsolutePath().normalize();
        Path dir = configDir.toAbsolutePath().normalize();
        Path relative = f.startsWith(dir) ? dir.relativize(f) : f;
        return relative.toString().replace('\\', '/');
    }

    /**
     * Readable name for titles and the file list: the part after the mod ID ({@code mymod-client.toml} → "client"),
     * the file name inside {@code config/<modid>/}, or null for the mod's main file.
     */
    @Nullable
    public String readableName() {
        return readableName(modId, file, configDir);
    }

    @Nullable
    public static String readableName(String modId, Path file, Path configDir) {
        Path f = file.toAbsolutePath().normalize();
        Path dir = configDir.toAbsolutePath().normalize();
        Path relative = f.startsWith(dir) ? dir.relativize(f) : f.getFileName();
        String base = ConfigDiscovery.baseName(relative.getFileName().toString());
        if (relative.getNameCount() > 1) {
            return base;
        }
        String compactBase = ConfigDiscovery.compact(base);
        String compactId = ConfigDiscovery.compact(modId);
        if (compactBase.equals(compactId)) {
            return null;
        }
        String lower = base.toLowerCase(Locale.ROOT);
        for (String variant : new String[]{modId, modId.replace('_', '-'), modId.replace('-', '_')}) {
            String v = variant.toLowerCase(Locale.ROOT);
            if (lower.startsWith(v) && lower.length() > v.length() + 1) {
                return base.substring(v.length() + 1);
            }
        }
        return base;
    }

    /** Root section, or an empty one when the file cannot be read. */
    public Section root() {
        return schema == null ? Section.root(null) : schema.root();
    }
}

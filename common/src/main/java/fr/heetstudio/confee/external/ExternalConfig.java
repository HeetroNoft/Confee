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
import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.ParsedValue;
import fr.heetstudio.confee.format.SourceText;
import fr.heetstudio.confee.format.FormatAdapter;
import fr.heetstudio.confee.format.Formats;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
    @Nullable
    private ParsedDocument document;
    private Map<Option<?>, ParsedValue> bindings = Map.of();
    private FileStamp stamp = FileStamp.MISSING;

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

    private void read() {
        if (!isSupported(file)) {
            status = Status.UNSUPPORTED_FORMAT;
            problem = extension(file).isEmpty() ? "unknown format" : "." + extension(file);
            return;
        }
        FormatAdapter adapter = Formats.forFile(file).orElseThrow();
        try {
            SourceText source = SourceText.decode(Files.readAllBytes(file));
            document = adapter.parse(source);
            stamp = FileStamp.of(file);
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
        RawSchemaBuilder.Result result = RawSchemaBuilder.build(document, readOriginal());
        bindings = result.bindings();
        schema = new ConfigSchema(modId, readableName(), ConfigScope.CLIENT, confeeFormat(), file, Theme.DEFAULT,
                ConfigSource.RAW_FILE, result.root());
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
        ConfeeLog.warn("Cannot read {} of mod {}: {}", relativePath(), modId, reason);
    }

    /** The backup made before Confee's first change, parsed; null when there is none. */
    @Nullable
    private ParsedDocument readOriginal() {
        Path backup = backupPath();
        if (!Files.isRegularFile(backup)) {
            return null;
        }
        try {
            return Formats.forFile(file).orElseThrow().parse(SourceText.decode(Files.readAllBytes(backup)));
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

    /** Surgical save of the values changed in memory (spec 7.2). */
    @Override
    public void save() {
        if (status != Status.OK || document == null) {
            return;
        }
        Map<ParsedValue, Object> changes = new LinkedHashMap<>();
        bindings.forEach((option, parsed) -> changes.put(parsed, toFileValue(option.get())));
        String text;
        try {
            text = document.edit(changes, Formats.forFile(file).orElseThrow().values());
        } catch (IllegalArgumentException e) {
            ConfeeLog.error("Cannot save " + relativePath(), e);
            return;
        }
        if (text.equals(document.source().text())) {
            return;
        }
        try {
            if (backups) {
                backupOriginal();
            }
            AtomicFiles.write(file, document.source().encode(text));
            ConfeeLog.info("Saved {} (mod {})", relativePath(), modId);
        } catch (IOException e) {
            ConfeeLog.error("Cannot write " + relativePath(), e);
            return;
        }
        // positions changed: read again
        read();
    }

    /** Values of the model use Long / Double, like the parsed file: nothing to convert but integral types. */
    private static Object toFileValue(Object value) {
        return value instanceof Integer i ? (Object) i.longValue() : value;
    }

    /** Spec 8.3: copy of the original file at the first change, never overwritten afterwards. */
    private void backupOriginal() throws IOException {
        Path backup = backupPath();
        if (Files.exists(backup)) {
            return;
        }
        Files.createDirectories(backup.getParent());
        Files.copy(file, backup);
        ConfeeLog.info("Backed up the original {} to {}", relativePath(), configDir.relativize(backup));
    }

    @Override
    public void fireOnChange() {
        // the mod does not use Confee: nothing to notify (changes apply on the next restart)
    }

    @Override
    public boolean changedOnDisk() {
        return status == Status.OK && !FileStamp.of(file).equals(stamp);
    }

    // ------------------------------------------------------------------ original file

    public boolean hasBackup() {
        return Files.isRegularFile(backupPath());
    }

    /**
     * "Reset all" for a raw file (spec 8.3): puts the original copy back.
     *
     * @return false when there is no backup or it cannot be copied
     */
    public boolean restoreOriginal() {
        Path backup = backupPath();
        if (!Files.isRegularFile(backup)) {
            return false;
        }
        try {
            AtomicFiles.write(file, Files.readAllBytes(backup));
            ConfeeLog.info("Restored the original {}", relativePath());
            read();
            return true;
        } catch (IOException e) {
            ConfeeLog.error("Cannot restore " + relativePath(), e);
            return false;
        }
    }

    public Path backupPath() {
        Path relative = file.startsWith(configDir) ? configDir.relativize(file) : file.getFileName();
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

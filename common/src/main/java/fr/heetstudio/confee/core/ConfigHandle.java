package fr.heetstudio.confee.core;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.api.ConfeeConfig;
import fr.heetstudio.confee.api.ConfigFormat;
import fr.heetstudio.confee.api.ConfigScope;
import fr.heetstudio.confee.format.AtomicFiles;
import fr.heetstudio.confee.format.FormatAdapter;
import fr.heetstudio.confee.format.FormatException;
import fr.heetstudio.confee.format.Formats;
import fr.heetstudio.confee.format.ParsedDocument;
import fr.heetstudio.confee.format.SchemaBinder;
import fr.heetstudio.confee.format.SourceText;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lifecycle of a Confee config (spec 5.2): load, save, reload, reset. Never crashes the game because of a file
 * (spec 5.3 / 14).
 * <p>Internal: may change without notice. Mods use it through {@link ConfeeConfig}.</p>
 */
public final class ConfigHandle<T> implements ConfeeConfig<T>, EditableConfig {

    private static final DateTimeFormatter BACKUP_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ConfigSchema schema;
    private final FormatAdapter adapter;
    @Nullable
    private final T instance;
    /** False for a CLIENT config on a dedicated server: defaults only, no file access (spec 5.4). */
    private final boolean active;
    private final List<Runnable> onChange = new CopyOnWriteArrayList<>();
    /** State of the file when it was last read or written by Confee. */
    private FileStamp stamp = FileStamp.MISSING;

    public ConfigHandle(ConfigSchema schema, @Nullable T instance, boolean active) {
        if (schema.scope() == ConfigScope.SERVER) {
            throw new UnsupportedOperationException("[Confee] Config '" + schema.id()
                    + "': SERVER scope is planned for a future version (server to client synchronization). "
                    + "Use CLIENT or COMMON.");
        }
        this.schema = schema;
        this.adapter = Formats.forConfee(schema.format());
        this.instance = instance;
        this.active = active;
    }

    @Override
    public ConfigSchema schema() {
        return schema;
    }

    public boolean isActive() {
        return active;
    }

    // ------------------------------------------------------------------ load / save

    /** Reads the file, or creates it with defaults and comments if it does not exist (spec 5.2). */
    public void load() {
        if (!active) {
            return;
        }
        Path file = schema.filePath();
        if (migrateLegacyFile(file)) {
            return;
        }
        if (!Files.exists(file)) {
            ConfeeLog.info("Creating {} with default values", fileName());
            save();
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            ConfeeLog.error("Cannot read " + fileName() + ", using default values", e);
            return;
        }
        try {
            ParsedDocument document = adapter.parseForSchema(SourceText.decode(bytes));
            stamp = FileStamp.of(file);
            SchemaBinder.Report report = SchemaBinder.load(schema.root(), document, fileName());
            if (!report.missing().isEmpty()) {
                ConfeeLog.info("{}: missing keys {} will be added on the next save", fileName(), report.missing());
            }
        } catch (FormatException | CharacterCodingException e) {
            recoverBrokenFile(file, e.getMessage());
        }
    }

    /**
     * {@code @Config(legacyFiles)}: when the Confee file does not exist yet, moves the first legacy file found to it.
     * Its values are read, then the file is written again by Confee (Confee's comments only, unknown keys kept) and the
     * old file is removed. Never throws: on any problem the legacy file is left as it is.
     *
     * @return true when a legacy file was moved (the values are loaded)
     */
    private boolean migrateLegacyFile(Path file) {
        for (String legacy : schema.legacyFiles()) {
            Path old;
            try {
                old = file.resolveSibling(legacy).normalize();
            } catch (java.nio.file.InvalidPathException e) {
                ConfeeLog.warn("{}: legacy file name '{}' is not valid: {}", schema.id(), legacy, e.getMessage());
                continue;
            }
            if (!Files.isRegularFile(old)) {
                continue;
            }
            boolean sameFile = sameFile(old, file);
            if (sameFile && exactName(file)) {
                return false; // already the Confee file
            }
            if (!sameFile && Files.exists(file)) {
                ConfeeLog.info("{}: {} is not used, {} already exists", schema.id(), legacy, fileName());
                return false;
            }
            // move it aside first: on a case-insensitive file system the old and new names are the same file
            Path moving = old.resolveSibling(old.getFileName() + ".confee-migrating");
            try {
                Files.move(old, moving, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                ConfeeLog.error("Cannot move legacy file " + legacy + " to " + fileName(), e);
                return false;
            }
            try {
                SourceText source = SourceText.decode(Files.readAllBytes(moving));
                ParsedDocument document = adapter.parseForSchema(source);
                SchemaBinder.Report report = SchemaBinder.load(schema.root(), document, legacy);
                if (!report.missing().isEmpty()) {
                    ConfeeLog.info("{}: missing keys {} get their default value", legacy, report.missing());
                }
                Files.createDirectories(file.getParent());
                writeFile(source, document, false);
                if (!Files.exists(file)) {
                    throw new IOException(fileName() + " was not written");
                }
                Files.delete(moving);
                ConfeeLog.info("Moved legacy config file {} to {}", legacy, fileName());
                return true;
            } catch (IOException | FormatException | RuntimeException e) {
                ConfeeLog.warn("Cannot move legacy file {} to {} ({}); it is left as it was", legacy, fileName(), e.getMessage());
                boolean unreadable = e instanceof FormatException || e instanceof CharacterCodingException;
                try {
                    if (!Files.exists(old)) {
                        Files.move(moving, old);
                    }
                } catch (IOException restore) {
                    ConfeeLog.error("Cannot restore legacy file " + legacy + " (it is in " + moving.getFileName() + ")", restore);
                }
                resetValues();
                // unreadable content: Confee's file is created with defaults; else (disk error) try again next time
                return !unreadable;
            }
        }
        return false;
    }

    private static boolean sameFile(Path a, Path b) {
        try {
            return Files.exists(b) && Files.isSameFile(a, b);
        } catch (IOException e) {
            return false;
        }
    }

    /** Whether the file on disk has exactly the expected name (and not only the same name with another case). */
    private static boolean exactName(Path file) {
        try {
            return file.toRealPath().getFileName().toString().equals(file.getFileName().toString());
        } catch (IOException e) {
            return true;
        }
    }

    /** Spec 5.3: back up the unreadable file, then write a new one with the default values. */
    private void recoverBrokenFile(Path file, String reason) {
        Path backup = file.resolveSibling(file.getFileName() + ".broken-" + LocalDateTime.now().format(BACKUP_DATE));
        ConfeeLog.warn("{} cannot be read ({}). It was copied to {} and recreated with default values.",
                fileName(), reason, backup.getFileName());
        try {
            Files.copy(file, backup);
        } catch (IOException e) {
            ConfeeLog.error("Cannot back up " + fileName() + "; the file is left untouched", e);
            resetValues();
            return;
        }
        resetValues();
        writeFile(null, null, true);
    }

    @Override
    public void save() {
        if (!active) {
            return;
        }
        SourceText previousSource = null;
        ParsedDocument previous = null;
        Path file = schema.filePath();
        if (Files.exists(file)) {
            try {
                previousSource = SourceText.decode(Files.readAllBytes(file));
                previous = adapter.parseForSchema(previousSource);
            } catch (IOException | FormatException e) {
                // unreadable previous file: its unknown keys cannot be kept
                previous = null;
            }
        }
        writeFile(previousSource, previous, true);
    }

    /**
     * @param merge keep the hand-written comments of {@code previous} (false when moving a legacy file: only Confee's
     *              comments are written)
     */
    private void writeFile(@Nullable SourceText previousSource, @Nullable ParsedDocument previous, boolean merge) {
        String lineSeparator = previousSource != null ? previousSource.lineSeparator() : "\n";
        String text = java.util.Objects.requireNonNull(adapter.configWriter(), "no config writer")
                .write(schema, lineSeparator, previous);
        if (previous != null && merge) {
            // hand-written comments, blank lines and key order of the file are kept (spec 5.3)
            text = fr.heetstudio.confee.format.ConfeeFileMerger.merge(adapter, schema, previous, text);
            if (text.equals(previous.source().text())) {
                stamp = FileStamp.of(schema.filePath());
                return; // nothing changed: the file is left untouched
            }
        }
        byte[] bytes = previousSource != null ? previousSource.encode(text) : text.getBytes(StandardCharsets.UTF_8);
        try {
            AtomicFiles.write(schema.filePath(), bytes);
            stamp = FileStamp.of(schema.filePath());
        } catch (IOException e) {
            ConfeeLog.error("Cannot write " + fileName(), e);
        }
    }

    @Override
    public boolean changedOnDisk() {
        return active && !FileStamp.of(schema.filePath()).equals(stamp);
    }

    @Override
    public void reload() {
        load();
    }

    @Override
    public void resetAll() {
        resetValues();
    }

    private void resetValues() {
        schema.root().forEachOption(Option::reset);
    }

    // ------------------------------------------------------------------ change notification (spec 9.6)

    @Override
    public void onChange(Runnable callback) {
        onChange.add(callback);
    }

    /** Called by the config screen after it saved. */
    @Override
    public void fireOnChange() {
        for (Runnable callback : onChange) {
            try {
                callback.run();
            } catch (RuntimeException e) {
                ConfeeLog.error("onChange callback of " + schema.id() + " threw an exception", e);
            }
        }
    }

    // ------------------------------------------------------------------ accessors

    @Override
    @Nullable
    public T get() {
        return instance;
    }

    @Override
    public net.minecraft.client.gui.screens.Screen createScreen(net.minecraft.client.gui.screens.Screen parent) {
        // the cast is resolved lazily: no client class is loaded unless this is called (spec 9.4)
        return (net.minecraft.client.gui.screens.Screen) ClientBridge.createScreen(this, parent);
    }

    @Override
    public String modId() {
        return schema.modId();
    }

    @Override
    @Nullable
    public String name() {
        return schema.name();
    }

    @Override
    public ConfigScope scope() {
        return schema.scope();
    }

    private String fileName() {
        return schema.filePath().getFileName().toString();
    }

    /** {@code config/<file>} for a Confee config, built from the environment config directory. */
    static Path filePath(ConfeeEnvironment environment, String modId, @Nullable String name, ConfigFormat format) {
        return environment.configDir().resolve(ConfigSchema.fileName(modId, name, format));
    }

    /** CLIENT configs are only active on the physical client. */
    static boolean isActive(ConfeeEnvironment environment, ConfigScope scope) {
        return scope != ConfigScope.CLIENT || environment.client();
    }
}

package fr.heetstudio.confee.core;

import fr.heetstudio.confee.api.ConfigFormat;
import fr.heetstudio.confee.api.ConfigScope;
import fr.heetstudio.confee.api.Theme;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Internal model shared by the annotation API, the builder API and the universal editor (spec 5).
 * The UI, serialization and validation only work on this.
 * <p>Internal: may change without notice.</p>
 */
public final class ConfigSchema {

    private final String modId;
    @Nullable
    private final String name;
    private final ConfigScope scope;
    private final ConfigFormat format;
    private final Path filePath;
    private final Theme theme;
    private final ConfigSource source;
    private final Section root;
    private final fr.heetstudio.confee.api.Layout layout;

    public ConfigSchema(String modId, @Nullable String name, ConfigScope scope, ConfigFormat format,
                        Path filePath, Theme theme, ConfigSource source, Section root) {
        this(modId, name, scope, format, filePath, theme, source, root, fr.heetstudio.confee.api.Layout.DEFAULT);
    }

    public ConfigSchema(String modId, @Nullable String name, ConfigScope scope, ConfigFormat format,
                        Path filePath, Theme theme, ConfigSource source, Section root,
                        fr.heetstudio.confee.api.Layout layout) {
        this.layout = Objects.requireNonNull(layout);
        this.modId = Objects.requireNonNull(modId);
        this.name = name == null || name.isEmpty() ? null : name;
        this.scope = Objects.requireNonNull(scope);
        this.format = Objects.requireNonNull(format);
        this.filePath = Objects.requireNonNull(filePath);
        this.theme = Objects.requireNonNull(theme);
        this.source = Objects.requireNonNull(source);
        this.root = Objects.requireNonNull(root);
    }

    /**
     * File name of a Confee config (spec 6.1): {@code <modId>.<ext>} or {@code <modId>-<name>.<ext>}.
     */
    public static String fileName(String modId, @Nullable String name, ConfigFormat format) {
        String base = name == null || name.isEmpty() ? modId : modId + "-" + name;
        return base + "." + format.extension();
    }

    public String modId() {
        return modId;
    }

    /** Config name ({@code client}, {@code common}...), or {@code null} for the mod's main config. */
    @Nullable
    public String name() {
        return name;
    }

    /** Registry key: {@code modId} or {@code modId:name}. */
    public String id() {
        return name == null ? modId : modId + ":" + name;
    }

    public ConfigScope scope() {
        return scope;
    }

    public ConfigFormat format() {
        return format;
    }

    public Path filePath() {
        return filePath;
    }

    public fr.heetstudio.confee.api.Layout layout() {
        return layout;
    }

    public Theme theme() {
        return theme;
    }

    public ConfigSource source() {
        return source;
    }

    public Section root() {
        return root;
    }
}

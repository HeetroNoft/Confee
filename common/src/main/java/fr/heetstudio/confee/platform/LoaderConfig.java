package fr.heetstudio.confee.platform;

import fr.heetstudio.confee.core.EditableConfig;
import org.jetbrains.annotations.Nullable;

/**
 * A config declared through the loader's own config system (Forge/NeoForge {@code ModConfig}, spec 8.4),
 * as seen from common code.
 * <p>Internal: may change without notice.</p>
 */
public interface LoaderConfig {

    String modId();

    /** {@code client}, {@code common} or {@code server}. */
    String type();

    /** File path relative to {@code config/} (or the file name for server configs). */
    String fileName();

    /** Spec 8.4: SERVER configs are read-only in V1. */
    boolean readOnly();

    /** Values are available (server configs only exist while a world is loaded). */
    boolean loaded();

    /** Builds an editable snapshot of the current values, or null when not loaded. */
    @Nullable
    EditableConfig open();
}

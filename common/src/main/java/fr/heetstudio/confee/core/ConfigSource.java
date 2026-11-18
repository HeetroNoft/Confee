package fr.heetstudio.confee.core;

/**
 * Where a {@link ConfigSchema} comes from (spec 5.1).
 * <p>Internal: may change without notice.</p>
 */
public enum ConfigSource {
    /** Declared with the Confee API (annotations or builder). Confee owns the file. */
    CONFEE,
    /** Read from a Forge/NeoForge {@code ForgeConfigSpec}. */
    FORGE_SPEC,
    /** Inferred from a raw file of a mod that does not use Confee. Surgical edits only. */
    RAW_FILE
}

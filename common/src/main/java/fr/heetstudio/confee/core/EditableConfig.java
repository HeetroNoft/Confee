package fr.heetstudio.confee.core;

/**
 * Something the config screen can edit: a Confee config ({@link ConfigHandle}) or another mod's file
 * ({@code ExternalConfig}). One UI for every source (spec 5).
 * <p>Internal: may change without notice.</p>
 */
public interface EditableConfig {

    ConfigSchema schema();

    /** Writes the in-memory values to the file. Never throws. */
    void save();

    /** Called after a save made from the screen (spec 9.6). */
    void fireOnChange();

    /**
     * For external files: the file changed on disk since it was read (spec 14), so saving would overwrite
     * someone else's changes.
     */
    default boolean changedOnDisk() {
        return false;
    }

    /** Changes waiting for "Save" that are not option values: list elements added, removed or moved (spec 8.2 bis). */
    default boolean hasUnsavedStructure() {
        return false;
    }

    /** "Cancel": forgets the changes of {@link #hasUnsavedStructure()}. */
    default void discard() {
    }
}

package fr.heetstudio.confee.api;

/**
 * What Confee does with a value of the file refused by a check of the mod ({@code @Validator}, {@code @ItemStack}).
 */
public enum OnFileError {
    /** The default value is used (and written back on the next save). Default. */
    USE_DEFAULT,
    /**
     * The value is kept as written, with a warning in the log: your mod reads it and must skip it itself. On the
     * screen, the value shows its error and "Save" is disabled until it is fixed or removed. On a list, only the
     * refused values are kept as they are, the others stay valid.
     */
    KEEP
}

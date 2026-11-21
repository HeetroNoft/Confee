package fr.heetstudio.confee.core;

import org.jetbrains.annotations.Nullable;

/**
 * Outcome of validating a raw value against an {@link Option} (spec 5.3).
 *
 * @param value   the value to use (corrected or default when needed)
 * @param status  what happened to the raw value
 * @param message human readable reason when {@code status != OK}, for the log
 * @param reason  message of the mod's own validator ({@code ValueValidator}): translation key or text, else null
 * <p>Internal: may change without notice.</p>
 */
public record Validated<T>(T value, Status status, @Nullable String message, @Nullable String reason) {

    public Validated(T value, Status status, @Nullable String message) {
        this(value, status, message, null);
    }

    public enum Status {
        /** The raw value was valid and is used as is. */
        OK,
        /** The raw value was readable but out of bounds: it was clamped. */
        CORRECTED,
        /** The raw value was unreadable or of the wrong type: the default value is used. */
        REPLACED_BY_DEFAULT,
        /**
         * Refused by a check of the mod with {@code OnFileError.KEEP}: kept as written when read from the file,
         * refused elsewhere (screen, API).
         */
        KEPT
    }

    public static <T> Validated<T> ok(T value) {
        return new Validated<>(value, Status.OK, null);
    }

    public static <T> Validated<T> corrected(T value, String message) {
        return new Validated<>(value, Status.CORRECTED, message);
    }

    public static <T> Validated<T> replaced(T value, String message) {
        return new Validated<>(value, Status.REPLACED_BY_DEFAULT, message);
    }

    /** Refused by the mod's own validator: the default is used, {@code reason} is shown on the screen. */
    public static <T> Validated<T> refused(T value, String message, String reason) {
        return new Validated<>(value, Status.REPLACED_BY_DEFAULT, message, reason);
    }

    /** Refused, but kept as written ({@code OnFileError.KEEP}). */
    public static <T> Validated<T> kept(T value, String message, String reason) {
        return new Validated<>(value, Status.KEPT, message, reason);
    }

    public boolean isOk() {
        return status == Status.OK;
    }
}

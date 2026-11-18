package fr.heetstudio.confee.api;

/**
 * Handle on a single config option (spec 6.2).
 *
 * @param <T> the option value type
 */
public interface ConfigValue<T> {

    /** Current in-memory value. */
    T get();

    /**
     * Sets the in-memory value. Out of range values are clamped, invalid values are rejected
     * (the current value is kept and a warning is logged). Call {@code ConfeeConfig#save()} to write the file.
     */
    void set(T value);

    /** Default value, or {@code null} when unknown. */
    T getDefault();

    /** Restores the default value in memory. */
    void reset();

    void addListener(ChangeListener<T> listener);
}

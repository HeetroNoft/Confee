package fr.heetstudio.confee.api;

/**
 * Called after an option value changed (spec 9.6).
 *
 * @param <T> the option value type
 */
@FunctionalInterface
public interface ChangeListener<T> {
    void onChange(T oldValue, T newValue);
}

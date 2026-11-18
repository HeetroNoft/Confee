package fr.heetstudio.confee.core;

/**
 * Where an option keeps its current value: a plain holder (builder API, external files)
 * or a field of the user's annotated class (annotation API).
 * <p>Internal: may change without notice.</p>
 */
public interface ValueStore<T> {

    T get();

    void set(T value);

    static <T> ValueStore<T> memory(T initial) {
        return new ValueStore<>() {
            private T value = initial;

            @Override
            public T get() {
                return value;
            }

            @Override
            public void set(T value) {
                this.value = value;
            }
        };
    }
}

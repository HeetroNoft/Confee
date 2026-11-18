package fr.heetstudio.confee.platform;

import java.util.ServiceLoader;

/**
 * Holds the loader-specific service implementations.
 * <p>Internal: may change without notice.</p>
 */
public final class Services {

    public static final Platform PLATFORM = load(Platform.class);

    private Services() {
    }

    private static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "[Confee] No implementation of " + type.getName() + " found for this loader"));
    }
}

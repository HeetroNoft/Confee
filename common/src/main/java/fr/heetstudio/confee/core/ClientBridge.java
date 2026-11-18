package fr.heetstudio.confee.core;

import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * Lets common code create client screens without referencing client classes, so nothing client-only is loaded on a
 * dedicated server (spec 9.4). The client entrypoint installs the factory.
 * <p>Internal: may change without notice.</p>
 */
public final class ClientBridge {

    @Nullable
    private static volatile BiFunction<ConfigHandle<?>, Object, Object> screenFactory;

    private ClientBridge() {
    }

    public static void setScreenFactory(BiFunction<ConfigHandle<?>, Object, Object> factory) {
        screenFactory = factory;
    }

    /** @return a {@code Screen}, typed as Object on purpose */
    public static Object createScreen(ConfigHandle<?> handle, Object parent) {
        BiFunction<ConfigHandle<?>, Object, Object> factory = screenFactory;
        if (factory == null) {
            throw new IllegalStateException("[Confee] Config screens only exist on the client");
        }
        return factory.apply(handle, parent);
    }
}

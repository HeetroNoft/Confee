package fr.heetstudio.confee.core;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every Confee config registered by mods, keyed by {@code modId} + {@code name} (spec 5.2).
 * <p>Internal: may change without notice.</p>
 */
public final class ConfeeRegistry {

    private static final Map<String, ConfigHandle<?>> CONFIGS = new LinkedHashMap<>();

    private ConfeeRegistry() {
    }

    public static synchronized void register(ConfigHandle<?> handle) {
        String id = handle.schema().id();
        if (CONFIGS.containsKey(id)) {
            throw new IllegalStateException("[Confee] A config '" + id + "' is already registered. "
                    + "Give each config of a mod a different name.");
        }
        CONFIGS.put(id, handle);
    }

    public static synchronized Optional<ConfigHandle<?>> get(String modId, @Nullable String name) {
        String id = name == null || name.isEmpty() ? modId : modId + ":" + name;
        return Optional.ofNullable(CONFIGS.get(id));
    }

    /** Configs of one mod, in registration order. */
    public static synchronized List<ConfigHandle<?>> forMod(String modId) {
        List<ConfigHandle<?>> out = new ArrayList<>();
        for (ConfigHandle<?> handle : CONFIGS.values()) {
            if (handle.schema().modId().equals(modId)) {
                out.add(handle);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static synchronized Collection<ConfigHandle<?>> all() {
        return List.copyOf(CONFIGS.values());
    }

    /** For unit tests only. */
    public static synchronized void clear() {
        CONFIGS.clear();
    }
}

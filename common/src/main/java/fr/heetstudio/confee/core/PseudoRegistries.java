package fr.heetstudio.confee.core;

import fr.heetstudio.confee.platform.Services;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Lists of IDs that are not game registries but are used like them (suggestions, icons, grid picker): the installed
 * mods. Safe on a dedicated server (no client class).
 * <p>Internal: may change without notice.</p>
 */
public final class PseudoRegistries {

    /** The installed mods ({@code @ModId}, {@code .modId()}), except those bundled inside another mod. */
    public static final String MOD = "confee:mod";
    /** Mods whose own config screen Confee can replace (the {@code forceConfeeEditor} setting). */
    public static final String REPLACEABLE_MOD = "confee:replaceable_mod";
    /** Mods the universal editor handles (the {@code ignoredMods} setting). */
    public static final String EDITABLE_MOD = "confee:editable_mod";

    private static final Map<String, Supplier<Collection<String>>> SOURCES = new ConcurrentHashMap<>();

    private PseudoRegistries() {
    }

    /** Sets where the IDs of a pseudo-registry come from ({@link #REPLACEABLE_MOD}, {@link #EDITABLE_MOD}). */
    public static void register(String registry, Supplier<Collection<String>> source) {
        SOURCES.put(registry, source);
    }

    public static boolean isPseudo(String registry) {
        return MOD.equals(registry) || REPLACEABLE_MOD.equals(registry) || EDITABLE_MOD.equals(registry);
    }

    /** Whether the entries of this registry are mods (mod logo as icon, mod name as name). */
    public static boolean isModList(String registry) {
        return isPseudo(registry);
    }

    /** IDs of the pseudo-registry (empty when unknown or when its source fails). */
    public static Collection<String> ids(String registry) {
        if (MOD.equals(registry)) {
            return Services.PLATFORM.getTopLevelModIds();
        }
        Supplier<Collection<String>> source = SOURCES.get(registry);
        return source == null ? List.of() : source.get();
    }
}

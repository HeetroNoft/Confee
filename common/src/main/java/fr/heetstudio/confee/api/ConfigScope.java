package fr.heetstudio.confee.api;

/**
 * Where a config is loaded (spec 5.4).
 */
public enum ConfigScope {
    /** Loaded on the physical client only. */
    CLIENT,
    /** Loaded on both sides, each side with its own local file. No synchronization. */
    COMMON,
    /**
     * Reserved for the future server to client synchronization.
     * Not usable yet: registering a SERVER config throws an {@link UnsupportedOperationException}.
     */
    SERVER
}

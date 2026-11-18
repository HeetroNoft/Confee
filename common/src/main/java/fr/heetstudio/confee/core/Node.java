package fr.heetstudio.confee.core;

import org.jetbrains.annotations.Nullable;

/**
 * Element of a config tree: either a {@link Section} or an {@link Option}.
 * <p>Internal: may change without notice.</p>
 */
public sealed interface Node permits Section, Option {

    /** Key in the file (last path segment). */
    String key();

    /** Explicit display name, or {@code null} to use translation / humanized key (spec 13). */
    @Nullable
    String displayName();

    /** Comment from the file or from {@code @Comment}, or {@code null}. */
    @Nullable
    String comment();
}

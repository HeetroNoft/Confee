package fr.heetstudio.confee.api;

/**
 * Limits a status effect ID option ({@code @Effect(only = ...)}) to the effects of a category. Several filters = any
 * of them.
 */
public enum EffectFilter {
    /** Speed, regeneration... */
    BENEFICIAL,
    /** Poison, wither... */
    HARMFUL,
    /** Glowing... */
    NEUTRAL
}

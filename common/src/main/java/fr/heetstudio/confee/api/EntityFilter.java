package fr.heetstudio.confee.api;

/**
 * Limits an entity ID option ({@code @Entity(only = ...)}, {@code .entity(...)}) to some entities: the spawn categories
 * Minecraft gives every entity type, plus {@link #LIVING}. Several filters = any of them. Applies to the suggestions,
 * the picker and the check of the field in the screen.
 */
public enum EntityFilter {
    /** Hostile mobs (zombie, creeper...). */
    MONSTER,
    /** Passive land animals (cow, pig...). */
    CREATURE,
    /** Bats. */
    AMBIENT,
    /** Axolotls. */
    AXOLOTLS,
    /** Glow squids. */
    UNDERGROUND_WATER_CREATURE,
    /** Squids, dolphins... */
    WATER_CREATURE,
    /** Fish. */
    WATER_AMBIENT,
    /** Everything else: villagers, golems, bosses, items, projectiles, minecarts... */
    MISC,
    /** Every living entity (mobs, the player, armor stands...), not items, projectiles or vehicles. */
    LIVING
}

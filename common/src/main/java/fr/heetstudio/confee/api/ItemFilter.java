package fr.heetstudio.confee.api;

/**
 * Limits an item or block ID option ({@code @Item(only = ...)}, {@code @Block(only = ...)}) to the items of creative
 * inventory tabs (mods' items in these tabs included), plus {@link #BLOCKS}. Several filters = any of them. Applies to
 * the suggestions, the picker and the check of the field in the screen.
 */
public enum ItemFilter {
    BUILDING_BLOCKS,
    COLORED_BLOCKS,
    NATURAL_BLOCKS,
    FUNCTIONAL_BLOCKS,
    REDSTONE_BLOCKS,
    TOOLS_AND_UTILITIES,
    COMBAT,
    FOOD_AND_DRINKS,
    INGREDIENTS,
    SPAWN_EGGS,
    /** Every item that places a block. */
    BLOCKS
}

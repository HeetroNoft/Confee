package fr.heetstudio.confee.api;

/**
 * Limits an enchantment ID option ({@code @Enchantment(only = ...)}) to the enchantments of an item category, or to
 * treasure / curse enchantments. Several filters = any of them.
 */
public enum EnchantmentFilter {
    ARMOR,
    ARMOR_FEET,
    ARMOR_LEGS,
    ARMOR_CHEST,
    ARMOR_HEAD,
    WEAPON,
    /** Tools that dig (pickaxe, shovel...). */
    DIGGER,
    FISHING_ROD,
    TRIDENT,
    /** Anything that can break (unbreaking, mending). */
    BREAKABLE,
    BOW,
    WEARABLE,
    CROSSBOW,
    VANISHABLE,
    /** Only found as loot (mending, frost walker...). */
    TREASURE,
    /** Curse of binding, curse of vanishing. */
    CURSE
}

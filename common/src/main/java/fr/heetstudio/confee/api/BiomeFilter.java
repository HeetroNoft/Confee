package fr.heetstudio.confee.api;

/**
 * Limits a biome ID option ({@code @Biome(only = ...)}) to the biomes of a dimension (the {@code minecraft:is_overworld},
 * {@code is_nether} and {@code is_end} biome tags, mods' biomes included). Several filters = any of them.
 */
public enum BiomeFilter {
    OVERWORLD,
    NETHER,
    END
}

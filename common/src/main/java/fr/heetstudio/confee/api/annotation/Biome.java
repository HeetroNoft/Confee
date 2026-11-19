package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value is a biome ({@code minecraft:plains}): suggestions from the {@code minecraft:worldgen/biome} registry, mods' entries included (no icon).
 * On {@code ResourceLocation}, {@code String} or {@code List<String>} fields. Same as {@code @Registry("minecraft:worldgen/biome")}.
 * <pre>{@code
 * @Biome(tags = true)
 * public List<String> spawnBiomes = new ArrayList<>(List.of("minecraft:plains", "#minecraft:is_forest"));
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Biome {

    /**
     * Only these entries ({@code only = BiomeFilter.NETHER}); several = any of them. Empty = all.
     */
    fr.heetstudio.confee.api.BiomeFilter[] only() default {};

    /** Also suggest and accept tags ({@code #minecraft:...}); needs a String or List&lt;String&gt; field. */
    boolean tags() default false;
}

package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registry used to suggest values while typing (ResourceLocation, String or List&lt;String&gt; fields), e.g.
 * {@code "minecraft:entity_type"}, {@code "minecraft:worldgen/biome"} or a registry added by a mod. Entries added by
 * mods are included. World registries (biomes, structures, dimensions...) come from the world when one is loaded,
 * else from the data files of the game and of the mods.
 * <pre>{@code
 * @Registry(value = "minecraft:entity_type", tags = true)
 * public List<String> mobs = new ArrayList<>(List.of("minecraft:zombie"));
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Registry {

    String value();

    /** Also suggest and accept tags of the registry ({@code #namespace:path}); needs a String or List&lt;String&gt; field. */
    boolean tags() default false;
}

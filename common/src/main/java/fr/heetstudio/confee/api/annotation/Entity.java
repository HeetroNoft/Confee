package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value is an entity type ({@code minecraft:zombie}): suggestions from the {@code minecraft:entity_type} registry, mods' entries included (spawn egg icon, picker).
 * On {@code ResourceLocation}, {@code String} or {@code List<String>} fields. Same as {@code @Registry("minecraft:entity_type")}.
 * <pre>{@code
 * @Entity
 * public String favoriteMob = "minecraft:axolotl";
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Entity {

    /**
     * Only these entries ({@code only = EntityFilter.MONSTER}); several = any of them. Empty = all.
     */
    fr.heetstudio.confee.api.EntityFilter[] only() default {};

    /** Also suggest and accept tags ({@code #minecraft:...}); needs a String or List&lt;String&gt; field. */
    boolean tags() default false;
}

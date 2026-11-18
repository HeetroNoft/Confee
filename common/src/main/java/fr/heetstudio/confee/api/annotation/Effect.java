package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value is a status effect ({@code minecraft:speed}): suggestions from the {@code minecraft:mob_effect} registry, mods' entries included (effect icon, picker).
 * On {@code ResourceLocation}, {@code String} or {@code List<String>} fields. Same as {@code @Registry("minecraft:mob_effect")}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Effect {

    /** Also suggest and accept tags ({@code #minecraft:...}); needs a String or List&lt;String&gt; field. */
    boolean tags() default false;
}

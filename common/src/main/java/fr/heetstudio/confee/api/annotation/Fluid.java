package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value is a fluid ({@code minecraft:water}): suggestions from the {@code minecraft:fluid} registry, mods' entries included (bucket icon, picker).
 * On {@code Identifier}, {@code String} or {@code List<String>} fields. Same as {@code @Registry("minecraft:fluid")}.
 * <pre>{@code
 * @Fluid
 * public String lavaReplacement = "minecraft:water";
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Fluid {

    /** Also suggest and accept tags ({@code #minecraft:...}); needs a String or List&lt;String&gt; field. */
    boolean tags() default false;
}

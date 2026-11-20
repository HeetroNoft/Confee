package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value is a dimension ({@code minecraft:the_nether}): suggestions from the {@code minecraft:dimension} registry, mods' entries included (no icon).
 * On {@code Identifier}, {@code String} or {@code List<String>} fields. Same as {@code @Registry("minecraft:dimension")}.
 * <pre>{@code
 * @Dimension
 * public String homeDimension = "minecraft:overworld";
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Dimension {

    /** Also suggest and accept tags ({@code #minecraft:...}); needs a String or List&lt;String&gt; field. */
    boolean tags() default false;
}

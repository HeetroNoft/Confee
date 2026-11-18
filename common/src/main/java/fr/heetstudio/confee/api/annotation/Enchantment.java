package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value is an enchantment ({@code minecraft:sharpness}): suggestions from the {@code minecraft:enchantment} registry, mods' entries included (enchanted book icon, picker).
 * On {@code ResourceLocation}, {@code String} or {@code List<String>} fields. Same as {@code @Registry("minecraft:enchantment")}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Enchantment {

    /** Also suggest and accept tags ({@code #minecraft:...}); needs a String or List&lt;String&gt; field. */
    boolean tags() default false;
}

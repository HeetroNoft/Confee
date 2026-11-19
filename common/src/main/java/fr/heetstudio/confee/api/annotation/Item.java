package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value is an item ID: suggestions from the item registry, item icon in the field, item picker (grid) and an
 * automatic preview. On {@code ResourceLocation}, {@code String} or {@code List<String>} fields.
 * <pre>{@code
 * @Item(tags = true)
 * public List<String> fuels = new ArrayList<>(List.of("minecraft:coal", "#minecraft:logs"));
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Item {

    /** Also suggest and accept tags ({@code #minecraft:logs}); needs a String or List&lt;String&gt; field. */
    boolean tags() default false;
}

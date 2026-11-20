package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value is a block ID: suggestions from the block registry, block icon in the field, block picker (grid) and an
 * automatic preview. On {@code Identifier}, {@code String} or {@code List<String>} fields.
 * <pre>{@code
 * @Block
 * public List<String> unbreakable = new ArrayList<>(List.of("minecraft:bedrock"));
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Block {

    /**
     * Only these entries ({@code only = ItemFilter.NATURAL_BLOCKS}); several = any of them. Empty = all.
     */
    fr.heetstudio.confee.api.ItemFilter[] only() default {};

    /** Also suggest and accept tags ({@code #minecraft:logs}); needs a String or List&lt;String&gt; field. */
    boolean tags() default false;
}

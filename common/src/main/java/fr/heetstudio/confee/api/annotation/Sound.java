package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value is a sound ({@code minecraft:block.note_block.bell}): suggestions from the {@code minecraft:sound_event} registry, mods' entries included (no icon).
 * On {@code ResourceLocation}, {@code String} or {@code List<String>} fields. Same as {@code @Registry("minecraft:sound_event")}.
 * <pre>{@code
 * @Sound
 * public String alertSound = "minecraft:block.note_block.bell";
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Sound {

    /** Also suggest and accept tags ({@code #minecraft:...}); needs a String or List&lt;String&gt; field. */
    boolean tags() default false;
}

package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Shows an {@code int} option as a color (ARGB). Spec 6.1.
 * <pre>{@code
 * @Color(alpha = true)
 * public int overlay = 0x802D7DD2;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Color {

    /** Whether the alpha channel can be edited. */
    boolean alpha() default false;
}

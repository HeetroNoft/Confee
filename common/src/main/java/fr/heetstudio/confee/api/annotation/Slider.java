package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Shows a numeric option as a slider. Needs {@link Range} with both bounds. Spec 6.1.
 * <pre>{@code
 * @Range(min = 0, max = 1) @Slider(step = 0.05)
 * public float opacity = 0.8f;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Slider {

    /** Slider step; values are rounded to it. */
    double step();
}

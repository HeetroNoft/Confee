package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bounds of a numeric option, or of each value of a list of numbers. Out of range values are clamped (in the screen,
 * the field shows an error). Omit a bound to leave it open. For the number of values of a list, use {@link Size}.
 * <pre>{@code
 * @Range(min = 1, max = 10)     // on a List of numbers: each value
 * public double dashDistance = 4.0;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Range {

    double min() default Double.NEGATIVE_INFINITY;

    double max() default Double.POSITIVE_INFINITY;
}

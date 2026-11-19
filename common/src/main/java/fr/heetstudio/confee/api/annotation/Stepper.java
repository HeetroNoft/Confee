package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Number field with − and + buttons on each side (Shift = 10 steps). On numeric fields.
 * <pre>{@code
 * @Range(min = 0, max = 99) @Stepper(step = 1)
 * public int lives = 5;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Stepper {

    double step() default 1;
}

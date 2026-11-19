package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static nested class used as a sub-section: a field of this type becomes a nested table. Spec 6.1.
 * <pre>{@code
 * @Section
 * public static class Dash { public double distance = 4.0; }
 * public Dash dash = new Dash();
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Section {
}

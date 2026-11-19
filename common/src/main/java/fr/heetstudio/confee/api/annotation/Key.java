package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Key in the file, when it differs from the Java field name. Spec 6.1.
 * <pre>{@code
 * @Key("max_speed")
 * public double maxSpeed = 1.5;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Key {

    String value();
}

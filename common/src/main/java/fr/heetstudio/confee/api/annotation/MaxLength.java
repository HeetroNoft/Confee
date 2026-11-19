package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maximum length of a String option (or of each value of a {@code List<String>}); longer values are replaced by the
 * default, and the screen shows an error on the field.
 * <pre>{@code
 * @MaxLength(16)                // on a List<String>: each value
 * public String nickname = "Steve";
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MaxLength {

    int value();
}

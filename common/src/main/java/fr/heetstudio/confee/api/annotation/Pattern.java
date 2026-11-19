package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Regular expression a String option (or each value of a {@code List<String>}) must fully match; otherwise the
 * default value is used, and the screen shows an error on the field.
 * <pre>{@code
 * @Pattern("[a-z0-9_]+")        // on a List<String>: each value
 * public String code = "abc";
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Pattern {

    String value();
}

package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Display name used when there is no translation key. Spec 6.1 / 13.
 * <pre>{@code
 * @Name("HUD opacity")
 * public int hudOpacity = 80;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Name {

    String value();
}

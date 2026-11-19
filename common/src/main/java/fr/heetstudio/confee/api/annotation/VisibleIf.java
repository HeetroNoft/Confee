package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The option is only shown when the condition holds. Same syntax as {@link EnabledIf}.
 * <pre>{@code
 * @VisibleIf("quality=INSANE")
 * public boolean insaneWarning = true;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface VisibleIf {

    String value();
}

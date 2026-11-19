package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Changing this option needs a game restart: shows a yellow "!" icon. Spec 6.1.
 * <pre>{@code
 * @RequiresRestart
 * public boolean fancyWorldgen = false;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RequiresRestart {
}

package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Description shown under the name of the option on the screen (not written in the file: use {@link Comment}
 * for that). Plain text or a translation key. Without it, Confee uses the translation
 * {@code config.<modId>.<path>.description} when it exists.
 * <pre>{@code
 * @Description("Shown under the name, not written in the file.")
 * public boolean showHud = true;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Description {

    String value();
}

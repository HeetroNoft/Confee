package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The String value is a key ({@code key.keyboard.g}, {@code key.mouse.right}...), edited by pressing it.
 * Read it on the client with {@code InputConstants.getKey(value)}. {@code key.keyboard.unknown} = no key.
 * <pre>{@code
 * @Keybind
 * public String dashKey = "key.keyboard.r";
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Keybind {

}

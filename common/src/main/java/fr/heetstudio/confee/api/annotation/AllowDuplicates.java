package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets a List option hold the same value twice. By default a list never does: duplicates are removed when the file
 * is loaded and when the config screen saves.
 * <pre>{@code
 * @AllowDuplicates
 * public List<Integer> diceRolls = new ArrayList<>(List.of(6, 6, 1));
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AllowDuplicates {
}

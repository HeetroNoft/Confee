package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For a {@code List<String>}: "Dirt" and "dirt" count as the same value when looking for duplicates (the first one is
 * kept). Builder: {@code .ignoreCase()}.
 * <pre>{@code
 * @IgnoreCase
 * public List<String> bannedWords = new ArrayList<>(List.of("spam"));
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IgnoreCase {
}

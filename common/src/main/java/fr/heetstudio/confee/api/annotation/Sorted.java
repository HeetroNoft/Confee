package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Keeps a List option sorted: natural order for texts and numbers, declaration order for enums. The list is sorted
 * when the file is loaded and when the screen saves; the screen hides the move arrows.
 * Builder: {@code .sorted()}.
 * <pre>{@code
 * @Sorted
 * public List<String> labels = new ArrayList<>(List.of("alpha", "beta"));
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Sorted {
}

package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Number of values a List option may hold. A longer list in the file loses its last values; a shorter one is replaced
 * by the default value. The screen greys out "+ Add" at the maximum and the remove buttons at the minimum.
 * <pre>{@code @Size(min = 1, max = 5) public List<String> homes = ...;}</pre>
 * Builder: {@code .size(min, max)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Size {

    int min() default 0;

    int max() default Integer.MAX_VALUE;
}

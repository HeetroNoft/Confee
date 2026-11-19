package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Shows the ▲ ▼ buttons that move the values of a list (or the objects of a list of objects), for lists whose order
 * matters (priorities...). Without it, the list has no move buttons. Not with {@code @Sorted}.
 * Builder: {@code .reorderable()}.
 * <pre>{@code
 * @Reorderable
 * public List<String> lootPriority = new ArrayList<>(List.of("minecraft:diamond", "minecraft:emerald"));
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Reorderable {
}

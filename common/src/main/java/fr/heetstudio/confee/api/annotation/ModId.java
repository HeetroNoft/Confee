package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value is the ID of an installed mod ({@code "sodium"}): suggestions of the installed mods with their logos, the
 * logo in the field and a grid picker of the mods. On {@code String} or {@code List<String>} fields (a mod ID is not a
 * Identifier). Builder: {@code .modId()}, or the {@code modId(...)} / {@code modList(...)} shortcuts.
 * <pre>{@code
 * @ModId
 * public List<String> compatibleMods = new ArrayList<>(List.of("sodium"));
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ModId {
}

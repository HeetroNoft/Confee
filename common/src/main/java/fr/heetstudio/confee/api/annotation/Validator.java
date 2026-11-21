package fr.heetstudio.confee.api.annotation;

import fr.heetstudio.confee.api.ValueValidator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Your own check of the option ({@link ValueValidator}), run after Confee's checks. On a list of simple values it
 * checks each value. The class needs a public no-argument constructor and must not use client classes.
 * <pre>{@code
 * @Validator(SnbtCheck.class)        // on a List<String>: each value
 * public List<String> loot = new ArrayList<>();
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Validator {

    Class<? extends ValueValidator<?>> value();
}

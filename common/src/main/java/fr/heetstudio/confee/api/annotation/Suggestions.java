package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Values suggested while typing in a text field ({@code String}, {@code Identifier} or {@code List<String>}).
 * Fixed {@link #value() values} and/or a {@link #provider() provider} called when the screen is opened.
 * The player can still type any other value: use an enum to restrict the choice.
 * <pre>{@code
 * @Suggestions({"north", "south", "east", "west"})
 * public String direction = "north";
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Suggestions {

    String[] value() default {};

    /** Class with a public no-argument constructor giving the suggestions; {@code SuggestionProvider.class} = none. */
    Class<? extends fr.heetstudio.confee.api.SuggestionProvider> provider() default fr.heetstudio.confee.api.SuggestionProvider.class;
}

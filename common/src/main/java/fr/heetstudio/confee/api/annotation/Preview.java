package fr.heetstudio.confee.api.annotation;

import fr.heetstudio.confee.api.PreviewRenderer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional preview of the option in the third column of the screen (see {@link fr.heetstudio.confee.api.Preview}).
 * Every attribute is optional; combine them freely.
 *
 * <pre>{@code
 * @Preview(image = "examplemod:textures/preview/mode_{value}.png", text = "examplemod.preview.mode")
 * public Mode mode = Mode.FAST;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface Preview {

    /** Image resource; {@code {value}} becomes the lower-case enum value. */
    String image() default "";

    /** Plain text (§ codes allowed) or translation key. */
    String text() default "";

    /** Custom rendering class with a constructor without arguments; created on the client only. */
    Class<? extends PreviewRenderer> renderer() default PreviewRenderer.class;
}

package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Icon of a sub-section ({@code @Section} field or class), shown on its navigation row.
 * An item ID ({@code minecraft:feather}) or a texture ({@code examplemod:textures/gui/dash.png}, 16×16 drawn).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface Icon {

    String value();
}

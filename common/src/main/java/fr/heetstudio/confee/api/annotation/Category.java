package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Puts the field in a category (a tab of the sidebar, a table in the file). Spec 6.1.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Category {

    /** Category key, also the table name in the file. */
    String value();

    /**
     * Icon of the category in the sidebar: an item ID ({@code minecraft:feather}) or a texture
     * ({@code examplemod:textures/gui/dash.png}). Set it on any one field of the category.
     */
    String icon() default "";
}

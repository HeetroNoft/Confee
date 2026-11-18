package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Layout of the config screen. Every attribute is optional. Builder: {@code ConfigBuilder.layout(...)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Layout {

    /** Maximum width of the option panel, in GUI pixels (0 = default, 400). */
    int panelWidth() default 0;

    /** Width of the preview column (0 = default, 150). */
    int previewWidth() default 0;

    /** {@code HIDDEN}: no sidebar, the categories are in a dropdown above the options. */
    fr.heetstudio.confee.api.Layout.Sidebar sidebar() default fr.heetstudio.confee.api.Layout.Sidebar.AUTO;

    /** Banner texture shown at the top of the option panel ("" = none). */
    String banner() default "";

    /** Height of the banner in GUI pixels (its width follows the image ratio, at most the panel width). */
    int bannerHeight() default 48;

    /** Texture tiled behind the panels ("" = plain {@code panelBgDeep} color). */
    String background() default "";
}

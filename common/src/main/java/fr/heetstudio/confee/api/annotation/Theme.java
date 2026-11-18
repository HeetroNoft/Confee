package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Colors of the config screen (spec 10.2 / 11), as ARGB ints. Every attribute is optional:
 * a token left to {@link #UNSET} keeps Confee's default (or is derived from {@link #accent()}).
 *
 * <pre>{@code @Theme(accent = 0xFF2D7DD2)}</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Theme {

    /** Marker for "not set". A fully transparent black token is not usable. */
    int UNSET = 0;

    int accent() default UNSET;
    int accentDark() default UNSET;
    int accentLight() default UNSET;
    int onAccentText() default UNSET;
    int panelBg() default UNSET;
    int panelBgDeep() default UNSET;
    int buttonFace() default UNSET;
    int buttonFaceHover() default UNSET;
    int buttonShadow() default UNSET;
    int outline() default UNSET;
    int textOnDark() default UNSET;
    int textOnLight() default UNSET;
    int textDisabled() default UNSET;
    int warning() default UNSET;
    int error() default UNSET;
    int toggleOffTrack() default UNSET;
    int titleBarBg() default UNSET;
    int titleBarText() default UNSET;
}

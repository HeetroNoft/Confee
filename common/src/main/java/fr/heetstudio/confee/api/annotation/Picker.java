package fr.heetstudio.confee.api.annotation;

import fr.heetstudio.confee.api.PickerStyle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Styles of the picker of an ID option that has one ({@code @Item}, {@code @Block}, {@code @Entity},
 * {@code @Enchantment}, {@code @Effect}, {@code @Fluid}, {@code @ModId}). The player switches between the allowed
 * styles with the buttons at the top of the picker; Confee remembers the last choice for each kind of entry.
 * With one style only, there is no switch. Builder: {@code .picker(style)} and {@code .pickerStyles(styles...)}.
 * <pre>{@code
 * @Item
 * @Picker(styles = {PickerStyle.LIST, PickerStyle.GRID}, defaultStyle = PickerStyle.LIST)
 * public String reward = "minecraft:diamond";
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Picker {

    /** Styles the player can choose ({@code GRID}, {@code LIST}, {@code DETAILED}); empty = all of them. */
    PickerStyle[] styles() default {};

    /** Style shown first; {@code AUTO} = grid for items, blocks and the other registries, list for mods. */
    PickerStyle defaultStyle() default PickerStyle.AUTO;
}

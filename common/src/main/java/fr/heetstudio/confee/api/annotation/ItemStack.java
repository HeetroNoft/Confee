package fr.heetstudio.confee.api.annotation;

import fr.heetstudio.confee.api.OnFileError;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A whole item stack (item + count + extra data), stored as an SNBT string in the format of the Minecraft version:
 * {@code {id:"minecraft:diamond",Count:3b,tag:{...}}} up to 1.20.4, {@code {id:"minecraft:diamond",count:3,components:{...}}}
 * from 1.20.5. Both forms are read on every version, and a plain item ID ({@code minecraft:diamond}) means one item.
 * On a {@code String} or a {@code List<String>}.
 *
 * <p>The screen shows the item field (icon, suggestions by name, item picker), a count field and an extra data field
 * checked as SNBT. When the file is loaded only the syntax is checked (items of other mods may not be registered
 * yet); the screen also checks that the item exists and the count fits its stack.</p>
 *
 * <pre>{@code
 * @ItemStack(onFileError = OnFileError.KEEP)
 * public List<String> loot = new ArrayList<>(List.of("{id:\"minecraft:diamond\",Count:3b}"));
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ItemStack {

    /** Shows the count field (else the count is always 1). */
    boolean count() default true;

    /** Shows the extra data field (NBT tag up to 1.20.4, components from 1.20.5). */
    boolean components() default true;

    /** Highest count: {@code 0} = the max stack size of the item (64, 16, 1...), {@code -1} = no limit. */
    int maxCount() default 0;

    /** What to do with a stack of the file that cannot be read. */
    OnFileError onFileError() default OnFileError.USE_DEFAULT;
}

package fr.heetstudio.confee.api.annotation;

import fr.heetstudio.confee.api.ConfigFormat;
import fr.heetstudio.confee.api.ConfigScope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a config class (spec 6.1). Its public, non-static, non-transient fields become options;
 * their initial values are the defaults. The class needs a no-argument constructor.
 *
 * <pre>{@code
 * @Config(modId = "examplemod")
 * public class ExampleConfig {
 *     @Comment("Show the mod HUD")
 *     public boolean showHud = true;
 * }
 *
 * public static final ConfeeConfig<ExampleConfig> CONFIG = Confee.register(ExampleConfig.class);
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Config {

    String modId();

    /** Empty → {@code config/<modId>.toml}; otherwise {@code config/<modId>-<name>.toml}. */
    String name() default "";

    ConfigScope scope() default ConfigScope.COMMON;

    ConfigFormat format() default ConfigFormat.TOML;

    /**
     * Writes a {@code Default: <value>} comment under each option of the file (maps, lists of objects and multi-line
     * texts excepted).
     */
    boolean writeDefaults() default false;

    /**
     * Files your mod wrote before using Confee (or under another name), relative to the {@code config/} folder, e.g.
     * {@code "ExampleMod-common.toml"} or {@code "examplemod/old.toml"}, in the config's format. When the Confee file
     * does not exist yet, the first legacy file found is moved to it: its values are kept, it gets Confee's comments
     * (the comments Confee did not write are dropped, unknown keys are kept). A file whose name only differs by its
     * case is renamed too.
     */
    String[] legacyFiles() default {};
}

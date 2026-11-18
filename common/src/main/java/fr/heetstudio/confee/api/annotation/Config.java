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
}

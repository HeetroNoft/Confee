package fr.heetstudio.confee.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Replaces the widget of the option by your own (client-only class with a public no-argument constructor).
 * See {@link fr.heetstudio.confee.api.client.OptionWidget}.
 * <pre>{@code
 * @Widget(TemperatureWidget.class)
 * public int temperature = 19;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Widget {

    @SuppressWarnings("rawtypes")
    Class<? extends fr.heetstudio.confee.api.client.OptionWidget> value();
}

package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.core.OptionType;
import fr.heetstudio.confee.ui.Labels;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.ValueText;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import fr.heetstudio.confee.ui.widget.DropdownWidget;
import fr.heetstudio.confee.ui.widget.TextFieldWidget;
import fr.heetstudio.confee.ui.widget.ToggleWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Widget for one scalar value (map value, field of an object): toggle, dropdown or text/number field.
 * The value is only pushed when it is valid; {@link #error()} tells why it is not.
 * <p>Internal: may change without notice.</p>
 */
final class ScalarEditor {

    final ConfeeWidget widget;
    @Nullable
    final TextFieldWidget field;

    @SuppressWarnings({"unchecked", "rawtypes"})
    ScalarEditor(Ui ui, Component label, OptionType type, @Nullable Class<? extends Enum<?>> enumClass,
                 Supplier<Object> value, Consumer<Object> onChange, Function<ValueText.Problem, Component> problemText,
                 DropdownWidget.Opener opener) {
        switch (type) {
            case BOOLEAN -> {
                widget = new ToggleWidget(ui, Component.empty(), () -> Boolean.TRUE.equals(value.get()),
                        v -> onChange.accept(v));
                field = null;
            }
            case ENUM -> {
                List<Object> values = Arrays.asList((Object[]) enumClass.getEnumConstants());
                widget = new DropdownWidget(ui, label, values, (Function<Object, Component>) Labels::enumValue,
                        value, (Consumer) onChange, opener);
                field = null;
            }
            default -> {
                Predicate<String> filter = switch (type) {
                    case INT, LONG -> s -> ValueText.isNumberText(s, false);
                    case FLOAT, DOUBLE -> s -> ValueText.isNumberText(s, true);
                    default -> null;
                };
                Object initial = value.get();
                String text = initial instanceof Double || initial instanceof Float ? ValueText.number(initial)
                        : String.valueOf(initial);
                TextFieldWidget[] holder = new TextFieldWidget[1];
                holder[0] = new TextFieldWidget(ui, label, text, 32767, filter, s -> {
                    ValueText.Parsed parsed = ValueText.parseScalar(type, s);
                    if (parsed.ok()) {
                        holder[0].setError(null);
                        onChange.accept(parsed.value());
                    } else {
                        holder[0].setError(problemText.apply(parsed.problem()));
                    }
                });
                widget = holder[0];
                field = holder[0];
            }
        }
    }

    @Nullable
    Component error() {
        return field == null ? null : field.error();
    }

    int height() {
        return widget instanceof ToggleWidget ? ToggleWidget.KNOB : 18;
    }
}

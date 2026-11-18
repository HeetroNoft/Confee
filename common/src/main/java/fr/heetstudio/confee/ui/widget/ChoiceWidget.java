package fr.heetstudio.confee.ui.widget;

import fr.heetstudio.confee.ui.Ui;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Common base of the dropdown and the segmented buttons: one value among a fixed list.
 * <p>Internal: may change without notice.</p>
 */
public abstract class ChoiceWidget<T> extends ConfeeWidget {

    protected final Component label;
    protected final List<T> values;
    protected final Function<T, Component> names;
    protected final Supplier<T> value;
    protected final Consumer<T> onChange;

    protected ChoiceWidget(Ui ui, int height, Component label, List<T> values, Function<T, Component> names,
                           Supplier<T> value, Consumer<T> onChange) {
        super(ui, 100, height);
        this.label = label;
        this.values = List.copyOf(values);
        this.names = names;
        this.value = value;
        this.onChange = onChange;
    }

    public List<T> values() {
        return values;
    }

    public Component nameOf(T v) {
        return names.apply(v);
    }

    public T current() {
        return value.get();
    }

    public int currentIndex() {
        return Math.max(0, values.indexOf(value.get()));
    }

    public void choose(T v) {
        if (!Objects.equals(v, value.get())) {
            onChange.accept(v);
        }
    }

    @Override
    public Component narration() {
        return Component.translatable("confee.narration.choice", label, names.apply(value.get()),
                currentIndex() + 1, values.size());
    }
}

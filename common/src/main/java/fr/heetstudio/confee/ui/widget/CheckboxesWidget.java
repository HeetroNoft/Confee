package fr.heetstudio.confee.ui.widget;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Several values of an enum as check boxes, in columns when the row is wide. Keyboard: arrows move, Space/Enter
 * toggles.
 * <p>Internal: may change without notice.</p>
 *
 * @param <E> enum type
 */
public class CheckboxesWidget<E> extends ConfeeWidget {

    private static final int ITEM_H = 14;
    private static final int BOX = 10;
    private static final int COLUMN_MIN = 110;

    private final Component label;
    private final List<E> values;
    private final Function<E, Component> names;
    private final Supplier<List<E>> getter;
    private final Consumer<List<E>> setter;
    private int cursor;

    public CheckboxesWidget(Ui ui, Component label, List<E> values, Function<E, Component> names,
                            Supplier<List<E>> getter, Consumer<List<E>> setter) {
        super(ui, 100, ITEM_H);
        this.label = label;
        this.values = List.copyOf(values);
        this.names = names;
        this.getter = getter;
        this.setter = setter;
    }

    private int columns(int w) {
        return Math.max(1, Math.min(3, w / COLUMN_MIN));
    }

    @Override
    public int heightFor(int w) {
        int columns = columns(w);
        return (values.size() + columns - 1) / columns * ITEM_H;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        Theme t = ui.theme();
        int columns = columns(width);
        int colW = width / columns;
        List<E> checked = getter.get();
        for (int i = 0; i < values.size(); i++) {
            int cx = x + (i % columns) * colW;
            int cy = y + (i / columns) * ITEM_H;
            boolean on = checked.contains(values.get(i));
            boolean hover = enabled && mouseX >= cx && mouseX < cx + colW && mouseY >= cy && mouseY < cy + ITEM_H;
            int by = cy + (ITEM_H - BOX) / 2;
            int boxColor = !enabled ? Draw.mix(t.panelBgDeep(), t.panelBg(), 0.5f) : on ? t.accent() : t.panelBgDeep();
            Draw.rect(g, cx, by, BOX, BOX, boxColor);
            Draw.outline(g, cx, by, BOX, BOX, hover ? t.accentLight() : on ? t.accentDark() : t.textDisabled());
            if (on) {
                Icon.CHECK.drawCentered(g, cx, by, BOX, BOX, enabled ? t.onAccentText() : t.textDisabled());
            }
            Draw.textFit(g, ui.font(), names.apply(values.get(i)), cx + BOX + 4, cy + (ITEM_H - 8) / 2, colW - BOX - 8,
                    enabled ? t.textOnDark() : t.textDisabled());
            if (focused && i == cursor) {
                Draw.focusRing(g, cx - 1, cy, colW - 2, ITEM_H, t.accent());
            }
        }
    }

    private void toggle(int index) {
        ui.click();
        List<E> next = new ArrayList<>(getter.get());
        E value = values.get(index);
        if (!next.remove(value)) {
            next.add(value);
        }
        // declaration order
        next.sort((a, b) -> Integer.compare(values.indexOf(a), values.indexOf(b)));
        setter.accept(next);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!enabled || !contains(mx, my)) {
            return false;
        }
        int columns = columns(width);
        int colW = width / columns;
        int index = (int) ((my - y) / ITEM_H) * columns + (int) Math.min(columns - 1, (mx - x) / colW);
        if (index >= 0 && index < values.size()) {
            cursor = index;
            toggle(index);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!enabled) {
            return false;
        }
        int columns = columns(width);
        switch (key) {
            case GLFW.GLFW_KEY_RIGHT -> cursor = Math.min(values.size() - 1, cursor + 1);
            case GLFW.GLFW_KEY_LEFT -> cursor = Math.max(0, cursor - 1);
            case GLFW.GLFW_KEY_DOWN -> cursor = Math.min(values.size() - 1, cursor + columns);
            case GLFW.GLFW_KEY_UP -> cursor = Math.max(0, cursor - columns);
            case GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> toggle(cursor);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public Component narration() {
        E value = values.get(cursor);
        boolean on = getter.get().contains(value);
        return Component.translatable(on ? "confee.narration.checkbox_on" : "confee.narration.checkbox_off",
                label, names.apply(value));
    }
}

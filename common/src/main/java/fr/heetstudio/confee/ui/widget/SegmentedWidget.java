package fr.heetstudio.confee.ui.widget;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Segmented buttons (capture 5 "Game mode" / "Difficulty"), for enums of 2 to 4 values. The active segment uses the
 * accent color with a short light line under its text. ←/→ select the previous / next value.
 * <p>Internal: may change without notice.</p>
 */
public class SegmentedWidget<T> extends ChoiceWidget<T> {

    public static final int HEIGHT = 18;
    private int pressedIndex = -1;

    public SegmentedWidget(Ui ui, Component label, List<T> values, Function<T, Component> names, Supplier<T> value,
                           Consumer<T> onChange) {
        super(ui, HEIGHT, label, values, names, value, onChange);
    }

    private int segmentX(int i) {
        return x + Math.round(i * width / (float) values.size());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        Theme t = ui.theme();
        int current = currentIndex();
        for (int i = 0; i < values.size(); i++) {
            int sx = segmentX(i);
            int sw = segmentX(i + 1) - sx + (i < values.size() - 1 ? 1 : 0); // segments share their 1 px border
            boolean active = i == current;
            boolean hover = enabled && contains(mouseX, mouseY) && mouseX >= sx && mouseX < sx + sw;
            int face;
            int shadow;
            int text;
            if (!enabled) {
                face = Draw.mix(active ? t.accent() : t.buttonFace(), t.panelBg(), 0.55f);
                shadow = Draw.mix(t.buttonShadow(), t.panelBg(), 0.55f);
                text = t.textDisabled();
            } else if (active) {
                face = hover ? t.accentLight() : t.accent();
                shadow = t.accentDark();
                text = t.onAccentText();
            } else {
                face = hover ? t.buttonFaceHover() : t.buttonFace();
                shadow = t.buttonShadow();
                text = t.textOnLight();
            }
            boolean pressed = pressedIndex == i && hover;
            Draw.bevel(g, sx, y, sw, height, face, shadow, t.outline(), pressed);
            String name = Draw.fit(ui.font(), names.apply(values.get(i)).getString(), sw - 6);
            int textY = y + (height - 2 - 8) / 2 + (pressed ? 1 : 0);
            Draw.textCentered(g, ui.font(), name, sx + sw / 2, textY, text);
            if (active && enabled) {
                int lineW = Math.min(ui.font().width(name), sw - 10);
                Draw.rect(g, sx + (sw - lineW) / 2, y + height - 5 + (pressed ? 1 : 0), lineW, 1, t.onAccentText());
            }
        }
        if (focused) {
            Draw.focusRing(g, x, y, width, height, t.accent());
        }
    }

    private int indexAt(double mx) {
        for (int i = 0; i < values.size(); i++) {
            if (mx >= segmentX(i) && mx < segmentX(i + 1)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !enabled || !contains(mx, my)) {
            return false;
        }
        pressedIndex = indexAt(mx);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (pressedIndex < 0) {
            return false;
        }
        int index = contains(mx, my) ? indexAt(mx) : -1;
        if (index == pressedIndex) {
            ui.click();
            choose(values.get(index));
        }
        pressedIndex = -1;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!enabled) {
            return false;
        }
        int direction = key == GLFW.GLFW_KEY_RIGHT ? 1 : key == GLFW.GLFW_KEY_LEFT ? -1 : 0;
        if (direction == 0) {
            return false;
        }
        int next = currentIndex() + direction;
        if (next >= 0 && next < values.size()) {
            ui.click();
            choose(values.get(next));
        }
        return true;
    }

    @Override
    @Nullable
    public Component tooltip(double mouseX, double mouseY) {
        int i = indexAt(mouseX);
        if (i < 0) {
            return null;
        }
        Component name = names.apply(values.get(i));
        int sw = segmentX(i + 1) - segmentX(i);
        return ui.font().width(name) > sw - 6 ? name : null;
    }
}

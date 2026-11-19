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
 * accent color with a short light line under its text. ←/→ select the previous / next value. With the mouse (or a
 * reset), the active face slides to the new segment in 100 ms with an ease-out; with the keyboard it moves at once.
 * <p>Internal: may change without notice.</p>
 */
public class SegmentedWidget<T> extends ChoiceWidget<T> {

    public static final int HEIGHT = 18;
    private static final long SLIDE_MS = 100;
    private int pressedIndex = -1;
    /** Index shown by the last frame, and where the slide started from. */
    private int shownIndex = -1;
    private int fromIndex;
    private long changedAt = -SLIDE_MS;
    private boolean instant;

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
        if (current != shownIndex) {
            fromIndex = shownIndex < 0 || instant ? current : shownIndex;
            shownIndex = current;
            changedAt = ui.now();
            instant = false;
        }
        float progress = Draw.easeOut((ui.now() - changedAt) / (float) SLIDE_MS);
        if (enabled && progress < 1 && fromIndex != current) {
            renderSliding(g, t, mouseX, mouseY, current, progress);
            return;
        }
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

    /** Mid-slide frame: every segment drawn as inactive, then the accent face between the old and the new segment. */
    private void renderSliding(GuiGraphics g, Theme t, int mouseX, int mouseY, int current, float progress) {
        int[] xs = new int[values.size()];
        int[] ws = new int[values.size()];
        String[] names = new String[values.size()];
        for (int i = 0; i < values.size(); i++) {
            xs[i] = segmentX(i);
            ws[i] = segmentX(i + 1) - xs[i] + (i < values.size() - 1 ? 1 : 0);
            names[i] = Draw.fit(ui.font(), this.names.apply(values.get(i)).getString(), ws[i] - 6);
            boolean hover = contains(mouseX, mouseY) && mouseX >= xs[i] && mouseX < xs[i] + ws[i];
            Draw.bevel(g, xs[i], y, ws[i], height, hover ? t.buttonFaceHover() : t.buttonFace(), t.buttonShadow(),
                    t.outline(), false);
        }
        int ax = Math.round(xs[fromIndex] + (xs[current] - xs[fromIndex]) * progress);
        int aw = Math.round(ws[fromIndex] + (ws[current] - ws[fromIndex]) * progress);
        Draw.bevel(g, ax, y, aw, height, t.accent(), t.accentDark(), t.outline(), false);
        int textY = y + (height - 2 - 8) / 2;
        for (int i = 0; i < values.size(); i++) {
            // text over the accent face turns light, in proportion of the overlap
            int overlap = Math.max(0, Math.min(ax + aw, xs[i] + ws[i]) - Math.max(ax, xs[i]));
            float onAccent = Math.min(1, overlap / (float) Math.max(1, ws[i]));
            Draw.textCentered(g, ui.font(), names[i], xs[i] + ws[i] / 2, textY,
                    Draw.mix(t.textOnLight(), t.onAccentText(), onAccent));
        }
        int fromLine = Math.min(ui.font().width(names[fromIndex]), ws[fromIndex] - 10);
        int toLine = Math.min(ui.font().width(names[current]), ws[current] - 10);
        int lineW = Math.round(fromLine + (toLine - fromLine) * progress);
        Draw.rect(g, ax + (aw - lineW) / 2, y + height - 5, lineW, 1, t.onAccentText());
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
            instant = true; // keyboard: no slide, the next press must show at once
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

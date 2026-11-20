package fr.heetstudio.confee.ui.widget;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.ValueText;
import fr.heetstudio.confee.ui.render.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Bedrock slider (spec 10.3, capture 2). Track over the full width: light up to the knob, dark after it;
 * ticks when there are 10 steps or fewer. The label "Name: value" is drawn above by the row.
 * Drag, click on the track, ←/→ = one step, Shift = 10 steps. Values are rounded to the step.
 * <p>Internal: may change without notice.</p>
 */
public class SliderWidget extends ConfeeWidget {

    public static final int HEIGHT = 16;
    private static final int TRACK_H = 10;
    private static final int KNOB_W = 8;

    private final double min;
    private final double max;
    private final double step;
    private final DoubleSupplier value;
    private final DoubleConsumer onChange;
    private final Supplier<Component> narration;
    private boolean dragging;

    public SliderWidget(Ui ui, double min, double max, double step, DoubleSupplier value, DoubleConsumer onChange,
                        Supplier<Component> narration) {
        super(ui, 100, HEIGHT);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = value;
        this.onChange = onChange;
        this.narration = narration;
    }

    private double fraction() {
        return max <= min ? 0 : (value.getAsDouble() - min) / (max - min);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        Theme t = ui.theme();
        int trackY = y + (HEIGHT - TRACK_H) / 2;
        int usable = width - KNOB_W;
        int knobX = x + (int) Math.round(fraction() * usable);
        int light = enabled ? t.buttonFace() : Draw.mix(t.buttonFace(), t.panelBg(), 0.6f);
        int dark = t.panelBgDeep();

        Draw.outline(g, x, trackY, width, TRACK_H, t.outline());
        Draw.rect(g, x + 1, trackY + 1, knobX + KNOB_W / 2 - x - 1, TRACK_H - 2, light);
        Draw.rect(g, knobX + KNOB_W / 2, trackY + 1, x + width - 1 - (knobX + KNOB_W / 2), TRACK_H - 2, dark);

        // ticks (spec: when the number of steps is small)
        if (step > 0 && max > min) {
            int steps = (int) Math.round((max - min) / step);
            if (steps >= 2 && steps <= 10) {
                for (int i = 1; i < steps; i++) {
                    int tickX = x + KNOB_W / 2 + (int) Math.round(i / (double) steps * usable);
                    int color = tickX < knobX + KNOB_W / 2 ? t.buttonShadow() : t.toggleOffTrack();
                    Draw.rect(g, tickX, trackY + 3, 1, TRACK_H - 6, enabled ? color : t.textDisabled());
                }
            }
        }

        boolean hover = hovered(mouseX, mouseY) || dragging;
        int face = !enabled ? Draw.mix(t.buttonFace(), t.panelBg(), 0.55f) : hover ? t.buttonFaceHover() : t.buttonFace();
        Draw.bevel(g, knobX, y, KNOB_W, HEIGHT, face, t.buttonShadow(), t.outline(), false);
        if (focused) {
            Draw.focusRing(g, x, y, width, HEIGHT, t.accent());
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !enabled || !contains(mx, my)) {
            return false;
        }
        dragging = true;
        setFromMouse(mx);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) {
            return false;
        }
        setFromMouse(mx);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!dragging) {
            return false;
        }
        dragging = false;
        ui.click();
        return true;
    }

    private void setFromMouse(double mx) {
        double f = (mx - x - KNOB_W / 2.0) / (width - KNOB_W);
        set(min + Math.max(0, Math.min(1, f)) * (max - min));
    }

    private void set(double raw) {
        double snapped = ValueText.snap(raw, min, max, step);
        if (snapped != value.getAsDouble()) {
            onChange.accept(snapped);
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!enabled) {
            return false;
        }
        int direction = key == GLFW.GLFW_KEY_RIGHT ? 1 : key == GLFW.GLFW_KEY_LEFT ? -1 : 0;
        if (direction == 0) {
            if (key == GLFW.GLFW_KEY_HOME) {
                set(min);
                return true;
            }
            if (key == GLFW.GLFW_KEY_END) {
                set(max);
                return true;
            }
            return false;
        }
        double s = step > 0 ? step : (max - min) / 100;
        set(value.getAsDouble() + direction * s * (net.minecraft.client.Minecraft.getInstance().hasShiftDown() ? 10 : 1));
        return true;
    }

    @Override
    public Component narration() {
        return narration.get();
    }
}

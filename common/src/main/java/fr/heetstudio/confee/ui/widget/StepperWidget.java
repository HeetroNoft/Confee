package fr.heetstudio.confee.ui.widget;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;

/**
 * Number field between a − and a + button. Up/Down arrows step too; Shift = 10 steps.
 * <p>Internal: may change without notice.</p>
 */
public class StepperWidget extends ConfeeWidget {

    private static final int BUTTON = TextFieldWidget.HEIGHT;
    private static final int GAP = 2;

    private final TextFieldWidget field;
    private final IntConsumer stepBy;
    private int pressed;
    private long pressedAt;

    /** @param stepBy called with the number of steps (negative = down) */
    public StepperWidget(Ui ui, TextFieldWidget field, IntConsumer stepBy) {
        super(ui, 100, TextFieldWidget.HEIGHT);
        this.field = field;
        this.stepBy = stepBy;
    }

    public TextFieldWidget field() {
        return field;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        field.setBounds(x + BUTTON + GAP, y, width - 2 * (BUTTON + GAP), height);
        field.setClipFrom(this);
        field.setEnabled(enabled);
        field.render(g, mouseX, mouseY, delta);
        button(g, x, -1, Icon.MINUS, mouseX, mouseY);
        button(g, x + width - BUTTON, 1, Icon.PLUS, mouseX, mouseY);
    }

    private void button(GuiGraphics g, int bx, int direction, Icon icon, int mouseX, int mouseY) {
        Theme t = ui.theme();
        boolean hover = enabled && mouseX >= bx && mouseX < bx + BUTTON && mouseY >= y && mouseY < y + height;
        boolean down = pressed == direction && ui.now() - pressedAt < 90;
        int face = !enabled ? Draw.mix(t.buttonFace(), t.panelBg(), 0.55f) : hover ? t.buttonFaceHover() : t.buttonFace();
        Draw.bevel(g, bx, y, BUTTON, height, face, t.buttonShadow(), t.outline(), down);
        icon.drawCentered(g, bx, y + (down ? 1 : 0), BUTTON, height - 2, enabled ? t.textOnLight() : t.textDisabled());
    }

    private void step(int direction) {
        ui.click();
        pressed = direction;
        pressedAt = ui.now();
        stepBy.accept(direction * (Screen.hasShiftDown() ? 10 : 1));
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        field.setFocused(focused);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!enabled || !contains(mx, my)) {
            return false;
        }
        if (mx < x + BUTTON) {
            step(-1);
            return true;
        }
        if (mx >= x + width - BUTTON) {
            step(1);
            return true;
        }
        return field.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!enabled) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            step(key == GLFW.GLFW_KEY_UP ? 1 : -1);
            return true;
        }
        return field.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return field.charTyped(c, modifiers);
    }

    @Override
    public boolean capturesText() {
        return true;
    }

    @Override
    public void tick() {
        field.tick();
    }

    @Override
    @Nullable
    public Component tooltip(double mouseX, double mouseY) {
        if (mouseX < x + BUTTON) {
            return Component.translatable("confee.tooltip.step_down");
        }
        if (mouseX >= x + width - BUTTON) {
            return Component.translatable("confee.tooltip.step_up");
        }
        return field.tooltip(mouseX, mouseY);
    }

    @Override
    public Component narration() {
        return field.narration();
    }
}

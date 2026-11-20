package fr.heetstudio.confee.testmod;

import fr.heetstudio.confee.api.client.OptionWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

/**
 * Example of a custom widget (client only): a thermometer bar from -20 to 50 °C. Click or drag to set,
 * arrows to step.
 */
public class TemperatureWidget extends OptionWidget<Integer> {

    private static final int MIN = -20;
    private static final int MAX = 50;

    @Override
    public int height(int width) {
        return 24;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY, float delta) {
        int value = value();
        float f = (value - MIN) / (float) (MAX - MIN);
        int barY = y + 12;
        g.fill(x, barY, x + width, barY + 10, theme().panelBgDeep());
        int filled = Math.round((width - 2) * f);
        // blue → red gradient following the temperature
        int cold = 0xFF2D7DD2;
        int hot = 0xFFE5533D;
        g.fillGradient(x + 1, barY + 1, x + 1 + filled, barY + 9, cold, f > 0.5f ? hot : cold);
        g.outline(x, barY, width, 10, enabled() ? theme().outline() : theme().textDisabled());
        int zero = x + 1 + Math.round((width - 2) * (-MIN / (float) (MAX - MIN)));
        g.fill(zero, barY - 2, zero + 1, barY + 12, theme().textOnDark());
        g.text(font(), value + " °C", x, y, enabled() ? theme().textOnDark() : theme().textDisabled(), false);
    }

    private void setFromMouse(double mouseX) {
        float f = (float) Math.max(0, Math.min(1, (mouseX - x()) / Math.max(1, width())));
        setValue(Math.round(MIN + f * (MAX - MIN)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        playClick();
        setFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        setFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_LEFT) {
            setValue(Math.max(MIN, Math.min(MAX, value() + (key == GLFW.GLFW_KEY_RIGHT ? 1 : -1))));
            return true;
        }
        return false;
    }
}

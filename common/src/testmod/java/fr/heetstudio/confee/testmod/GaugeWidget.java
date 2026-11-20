package fr.heetstudio.confee.testmod;

import fr.heetstudio.confee.api.client.OptionWidget;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * Example of a custom widget for any bounded integer (client only): a row of cells, click to choose.
 * Registered by the test mod for a Forge config of another mod ("confee_rawmod:client", zoom).
 */
public class GaugeWidget extends OptionWidget<Integer> {

    private int min() {
        return minimum() == null ? 0 : minimum().intValue();
    }

    private int max() {
        return maximum() == null ? 10 : maximum().intValue();
    }

    @Override
    public int height(int width) {
        return 16;
    }

    @Override
    public void render(GuiGraphics g, int x, int y, int width, int height, int mouseX, int mouseY, float delta) {
        int cells = max() - min() + 1;
        int cellW = Math.max(4, (width - 40) / cells);
        for (int i = 0; i < cells; i++) {
            int cx = x + i * cellW;
            boolean on = min() + i <= value();
            g.fill(cx, y + 2, cx + cellW - 2, y + height - 2, on ? theme().accent() : theme().panelBgDeep());
            g.submitOutline(cx, y + 2, cellW - 2, height - 4, theme().outline());
        }
        g.drawString(font(), "x" + value(), x + cells * cellW + 6, y + 4, theme().textOnDark(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cells = max() - min() + 1;
        int cellW = Math.max(4, (width() - 40) / cells);
        int index = (int) ((mouseX - x()) / cellW);
        if (index >= 0 && index < cells) {
            playClick();
            setValue(min() + index);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_LEFT) {
            setValue(Math.max(min(), Math.min(max(), value() + (key == GLFW.GLFW_KEY_RIGHT ? 1 : -1))));
            return true;
        }
        return false;
    }
}

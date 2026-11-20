package fr.heetstudio.confee.testmod;

import fr.heetstudio.confee.api.client.OptionWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

/**
 * Example of a custom widget for a number of another mod's file (client only): ten cells for a weight from 0 to 20,
 * click or ←/→ to choose. Numbers of other mods' files are {@code Long}. Registered by the test mod for
 * {@code config/confee_rawmod/spawns.json} ("spawns.[n].weight").
 */
public class WeightWidget extends OptionWidget<Long> {

    private static final int CELLS = 10;
    private static final long STEP = 2;

    @Override
    public int height(int width) {
        return 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY, float delta) {
        int cellW = Math.max(4, (width - 40) / CELLS);
        for (int i = 0; i < CELLS; i++) {
            int cx = x + i * cellW;
            boolean on = (i + 1) * STEP <= value();
            g.fill(cx, y + 2, cx + cellW - 2, y + height - 2, on ? theme().accent() : theme().panelBgDeep());
            g.outline(cx, y + 2, cellW - 2, height - 4, theme().outline());
        }
        g.text(font(), String.valueOf(value()), x + CELLS * cellW + 6, y + 4, theme().textOnDark(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cellW = Math.max(4, (width() - 40) / CELLS);
        int index = (int) ((mouseX - x()) / cellW);
        if (index >= 0 && index < CELLS) {
            playClick();
            setValue((index + 1) * STEP);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_LEFT) {
            setValue(Math.max(0, Math.min(CELLS * STEP, value() + (key == GLFW.GLFW_KEY_RIGHT ? 1 : -1))));
            return true;
        }
        return false;
    }
}

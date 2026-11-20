package fr.heetstudio.confee.ui.widget;

import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntSupplier;

/**
 * Small square button showing an icon (reset ↺, remove ✕, move ▲▼, back ‹). No face; a light square on hover.
 * <p>Internal: may change without notice.</p>
 */
public class IconButton extends ConfeeWidget {

    private final Icon icon;
    private final Component label;
    private final Runnable onPress;
    private final IntSupplier color;

    public IconButton(Ui ui, Icon icon, Component label, int size, IntSupplier color, Runnable onPress) {
        super(ui, size, size);
        this.icon = icon;
        this.label = label;
        this.onPress = onPress;
        this.color = color;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        boolean hover = hovered(mouseX, mouseY);
        int c = enabled ? color.getAsInt() : ui.theme().textDisabled();
        if (hover || focused) {
            Draw.rect(g, x, y, width, height, Draw.withAlpha(c, 0.18f));
        }
        icon.drawCentered(g, x, y, width, height, c);
        if (focused) {
            Draw.focusRing(g, x, y, width, height, ui.theme().accent());
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && enabled && contains(mx, my)) {
            ui.click();
            onPress.run();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (enabled && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)) {
            ui.click();
            onPress.run();
            return true;
        }
        return false;
    }

    @Override
    public Component tooltip(double mouseX, double mouseY) {
        return label;
    }

    @Override
    public Component narration() {
        return label;
    }
}

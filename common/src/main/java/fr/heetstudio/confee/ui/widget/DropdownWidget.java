package fr.heetstudio.confee.ui.widget;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Dropdown (spec 10.3, capture 2 "UI Profile"): full width light box, value on the left, ▼ on the right.
 * Opening shows a popup list over the screen (see {@code DropdownPopup}).
 * <p>Internal: may change without notice.</p>
 */
public class DropdownWidget<T> extends ChoiceWidget<T> {

    public static final int HEIGHT = 18;

    /** Asks the screen to open the popup list for this dropdown. */
    public interface Opener {
        void open(DropdownWidget<?> dropdown);
    }

    private final Opener opener;
    private boolean open;
    private boolean mouseDown;

    public DropdownWidget(Ui ui, Component label, List<T> values, Function<T, Component> names, Supplier<T> value,
                          Consumer<T> onChange, Opener opener) {
        super(ui, HEIGHT, label, values, names, value, onChange);
        this.opener = opener;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    /** Untyped access for the popup. */
    @SuppressWarnings("unchecked")
    public void chooseIndex(int index) {
        choose(values.get(index));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        Theme t = ui.theme();
        boolean hover = hovered(mouseX, mouseY) || open;
        int face = !enabled ? Draw.mix(t.buttonFace(), t.panelBg(), 0.55f) : hover ? t.buttonFaceHover() : t.buttonFace();
        int shadow = !enabled ? Draw.mix(t.buttonShadow(), t.panelBg(), 0.55f) : t.buttonShadow();
        boolean pressed = mouseDown && hover;
        Draw.bevel(g, x, y, width, height, face, shadow, t.outline(), pressed);
        int text = enabled ? t.textOnLight() : t.textDisabled();
        int offset = pressed ? 1 : 0;
        Draw.textFit(g, ui.font(), names.apply(value.get()), x + 6, y + (height - 2 - 8) / 2 + offset, width - 24, text);
        Icon.CHEVRON_DOWN.draw(g, x + width - 13, y + (height - 2 - 4) / 2 + offset, text);
        if (focused) {
            Draw.focusRing(g, x, y, width, height, t.accent());
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !enabled || !contains(mx, my)) {
            return false;
        }
        mouseDown = true;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!mouseDown) {
            return false;
        }
        mouseDown = false;
        if (contains(mx, my)) {
            ui.click();
            opener.open(this);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!enabled) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            ui.click();
            opener.open(this);
            return true;
        }
        return false;
    }
}

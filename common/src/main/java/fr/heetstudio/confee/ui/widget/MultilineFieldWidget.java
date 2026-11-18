package fr.heetstudio.confee.ui.widget;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Multi-line text area in Confee's style (dark field, light outline, accent when focused). Editing, caret, selection
 * and scrolling come from the vanilla {@link MultiLineEditBox}.
 * <p>Internal: may change without notice.</p>
 */
public class MultilineFieldWidget extends ConfeeWidget {

    public static final int HEIGHT = 58;
    /** Room under the box for the character counter (only with a length limit). */
    private static final int COUNTER_H = 11;

    private final Box box;
    private final Component label;
    private Consumer<String> listener;
    @Nullable
    private Component error;

    /** The vanilla box with Confee's background and border. */
    private final class Box extends MultiLineEditBox {
        Box(Component label) {
            super(ui.font(), 0, 0, 100, HEIGHT, Component.empty(), label);
        }

        @Override
        protected void renderBackground(GuiGraphics g) {
            Theme t = ui.theme();
            int border = error != null ? t.error() : focused ? t.accent() : t.textDisabled();
            if (!enabled) {
                border = Draw.mix(t.textDisabled(), t.panelBg(), 0.5f);
            }
            Draw.rect(g, getX(), getY(), getWidth(), getHeight(), enabled ? t.panelBgDeep() : Draw.mix(t.panelBgDeep(), t.panelBg(), 0.5f));
            Draw.outline(g, getX(), getY(), getWidth(), getHeight(), border);
        }
    }

    public MultilineFieldWidget(Ui ui, Component label, String text, int maxLength, Consumer<String> onEdit) {
        super(ui, 100, maxLength < Integer.MAX_VALUE ? HEIGHT + COUNTER_H : HEIGHT);
        this.label = label;
        this.box = new Box(label);
        // the vanilla box draws a "length/limit" counter under itself only when a limit is set
        box.setCharacterLimit(maxLength);
        box.setValue(text);
        this.listener = onEdit;
        box.setValueListener(onEdit);
    }

    public String text() {
        return box.getValue();
    }

    public void setTextSilently(String text) {
        if (!text.equals(box.getValue())) {
            box.setValueListener(s -> { });
            box.setValue(text);
            box.setValueListener(listener);
        }
    }

    public void setResponder(Consumer<String> onEdit) {
        this.listener = onEdit;
        box.setValueListener(onEdit);
    }

    public void setError(@Nullable Component error) {
        this.error = error;
    }

    @Nullable
    public Component error() {
        return error;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        box.setX(x);
        box.setY(y);
        box.setWidth(width);
        box.active = enabled;
        box.render(g, mouseX, mouseY, delta);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        box.setFocused(focused);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!enabled || !contains(mx, my)) {
            return false;
        }
        box.setFocused(true);
        box.mouseClicked(mx, my, button);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return box.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return box.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!enabled) {
            return false;
        }
        // Tab moves the focus; Escape closes the screen
        if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }
        return box.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return enabled && box.charTyped(c, modifiers);
    }

    @Override
    public boolean capturesText() {
        return true;
    }

    @Override
    public void tick() {
        box.tick();
    }

    @Override
    @Nullable
    public Component tooltip(double mouseX, double mouseY) {
        return error;
    }

    @Override
    public Component narration() {
        return Component.translatable("confee.narration.field", label, Component.literal(box.getValue()));
    }
}

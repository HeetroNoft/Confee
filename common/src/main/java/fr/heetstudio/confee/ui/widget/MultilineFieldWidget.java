package fr.heetstudio.confee.ui.widget;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

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

    private final MultiLineEditBox box;
    private final Component label;
    private Consumer<String> listener;
    @Nullable
    private Component error;

    public MultilineFieldWidget(Ui ui, Component label, String text, int maxLength, Consumer<String> onEdit) {
        super(ui, 100, maxLength < Integer.MAX_VALUE ? HEIGHT + COUNTER_H : HEIGHT);
        this.label = label;
        // the vanilla box without its background: Confee draws its own (render)
        this.box = MultiLineEditBox.builder().setPlaceholder(Component.empty()).setShowBackground(false)
                .build(ui.font(), 100, HEIGHT, label);
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
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        box.setX(x);
        box.setY(y);
        box.setWidth(width);
        box.active = enabled;
        Theme t = ui.theme();
        int border = error != null ? t.error() : focused ? t.accent() : t.textDisabled();
        if (!enabled) {
            border = Draw.mix(t.textDisabled(), t.panelBg(), 0.5f);
        }
        Draw.rect(g, x, y, width, HEIGHT, enabled ? t.panelBgDeep() : Draw.mix(t.panelBgDeep(), t.panelBg(), 0.5f));
        Draw.outline(g, x, y, width, HEIGHT, border);
        box.extractRenderState(g, mouseX, mouseY, delta);
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
        box.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mx, my, new net.minecraft.client.input.MouseButtonInfo(button, 0)), false);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return box.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(mx, my, new net.minecraft.client.input.MouseButtonInfo(button, 0)), dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        // the vanilla box always takes the wheel, even when its text fits or it is already at the top / bottom: the
        // wheel then goes to the option panel, so the screen keeps scrolling when the mouse passes over the box
        double before = box.scrollAmount();
        box.mouseScrolled(mx, my, 0, delta);
        return box.scrollAmount() != before;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!enabled) {
            return false;
        }
        // Tab moves the focus; Escape closes the screen
        if (key == InputConstants.KEY_TAB || key == InputConstants.KEY_ESCAPE) {
            return false;
        }
        return box.keyPressed(new net.minecraft.client.input.KeyEvent(key, scanCode, modifiers));
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return enabled && box.charTyped(new net.minecraft.client.input.CharacterEvent(c));
    }

    @Override
    public boolean capturesText() {
        return true;
    }

    @Override
    public void tick() {
        // the vanilla cursor blinks on its own since 1.20.2
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

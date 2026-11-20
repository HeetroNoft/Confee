package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.widget.ButtonWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog (spec 10.4 C): centered {@code panelBg} panel with outline, title, text and 2–3 buttons; the main action
 * uses the primary style. The screen behind is darkened. Fades and rises in 120 ms.
 * Escape = the cancel action. Tab / Shift+Tab / ←/→ move between buttons.
 * <p>Internal: may change without notice.</p>
 */
public final class Modal implements Overlay {

    private static final long OPEN_MS = 120;
    private static final int PAD = 10;

    private final Ui ui;
    private final Component title;
    private final Component message;
    private final List<ButtonWidget> buttons = new ArrayList<>();
    private final Runnable onCancel;
    private int focus;
    private long openedAt = -1;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private List<FormattedCharSequence> lines = List.of();

    /**
     * @param onCancel run on Escape
     */
    public Modal(Ui ui, Component title, Component message, Runnable onCancel) {
        this.ui = ui;
        this.title = title;
        this.message = message;
        this.onCancel = onCancel;
    }

    /** Adds a button; buttons are laid out right to left in the order they are added (first = rightmost). */
    public Modal button(Component label, boolean primary, Runnable action) {
        ButtonWidget button = new ButtonWidget(ui, label, primary ? ButtonWidget.Style.PRIMARY : ButtonWidget.Style.NORMAL, action);
        buttons.add(button);
        if (primary) {
            focus = buttons.size() - 1;
        }
        return this;
    }

    private void layout(int screenW, int screenH) {
        // wide enough for every button at its natural width, within the screen
        int buttonsW = 4 * (buttons.size() - 1);
        for (ButtonWidget button : buttons) {
            buttonsW += Math.max(button.preferredWidth(), 56);
        }
        pw = Math.min(Math.max(280, buttonsW + 2 * PAD), screenW - 24);
        lines = ui.font().split(message, pw - 2 * PAD);
        int buttonsH = 20;
        ph = PAD + 9 + 6 + lines.size() * 10 + 10 + buttonsH + PAD;
        px = (screenW - pw) / 2;
        py = Math.max(4, (screenH - ph) / 2);
        int bx = px + pw - PAD;
        int available = pw - 2 * PAD - 4 * (buttons.size() - 1);
        int maxEach = available / Math.max(1, buttons.size());
        boolean fits = buttonsW <= pw - 2 * PAD;
        for (ButtonWidget button : buttons) {
            int natural = Math.max(button.preferredWidth(), 56);
            int bw = fits ? natural : Math.min(natural, maxEach);
            bx -= bw;
            button.setBounds(bx, py + ph - PAD - buttonsH, bw, buttonsH);
            bx -= 4;
        }
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).setFocused(i == focus);
        }
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (openedAt < 0) {
            openedAt = ui.now();
        }
        Theme t = ui.theme();
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        layout(screenW, screenH);
        float p = Draw.easeOut((ui.now() - openedAt) / (float) OPEN_MS);
        g.pose().pushMatrix();
        // drawn above what is already on screen (no depth since 1.21.6)
        g.nextStratum();
        Draw.rect(g, 0, 0, screenW, screenH, Draw.withAlpha(0xB0000000, p));
        g.pose().translate(0, Math.round((1 - p) * 6));
        Draw.rect(g, px, py, pw, ph, t.panelBg());
        Draw.outline(g, px, py, pw, ph, t.outline());
        Draw.rect(g, px + 1, py + 1, pw - 2, 1, Draw.mix(t.panelBg(), t.textOnDark(), 0.08f));
        Draw.textFit(g, ui.font(), title, px + PAD, py + PAD, pw - 2 * PAD, t.textOnDark());
        int ly = py + PAD + 9 + 6;
        for (FormattedCharSequence line : lines) {
            Draw.text(g, ui.font(), line, px + PAD, ly, Draw.mix(t.textOnDark(), t.panelBg(), 0.2f));
            ly += 10;
        }
        int my = mouseY - Math.round((1 - p) * 6);
        for (ButtonWidget button : buttons) {
            button.render(g, mouseX, my, delta);
        }
        g.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (ButtonWidget b : buttons) {
            if (b.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        for (ButtonWidget b : buttons) {
            b.mouseReleased(mx, my, button);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                ui.click();
                onCancel.run();
            }
            case GLFW.GLFW_KEY_TAB -> moveFocus((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 1 : -1);
            case GLFW.GLFW_KEY_LEFT -> moveFocus(1);
            case GLFW.GLFW_KEY_RIGHT -> moveFocus(-1);
            default -> {
                if (!buttons.isEmpty()) {
                    buttons.get(focus).keyPressed(key, scanCode, modifiers);
                }
            }
        }
        return true;
    }

    /** Buttons are stored right to left: +1 moves left. */
    private void moveFocus(int direction) {
        if (!buttons.isEmpty()) {
            focus = Math.floorMod(focus + direction, buttons.size());
        }
    }

    @Override
    public Component narration() {
        return Component.translatable("confee.narration.modal", title, message,
                buttons.isEmpty() ? Component.empty() : buttons.get(focus).narration());
    }

    @Override
    @Nullable
    public Component tooltip(double mx, double my) {
        return null;
    }
}

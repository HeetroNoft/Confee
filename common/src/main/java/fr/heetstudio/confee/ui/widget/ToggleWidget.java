package fr.heetstudio.confee.ui.widget;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Bedrock toggle (spec 10.3, capture 1): a track twice as wide as the knob. OFF = knob left, gray "O" on the right;
 * ON = accent "I" on the left, knob right. The label sits on the right and is clickable too.
 * The knob slides in 100 ms with an ease-out.
 * <p>Internal: may change without notice.</p>
 */
public class ToggleWidget extends ConfeeWidget {

    public static final int KNOB = 14;
    public static final int TRACK = KNOB * 2;
    private static final long ANIMATION_MS = 100;

    private final BooleanSupplier value;
    private final Consumer<Boolean> onChange;
    private Component label;
    private boolean shown;
    private long changedAt = -ANIMATION_MS;

    public ToggleWidget(Ui ui, Component label, BooleanSupplier value, Consumer<Boolean> onChange) {
        super(ui, TRACK, KNOB);
        this.label = label;
        this.value = value;
        this.onChange = onChange;
        this.shown = value.getAsBoolean();
    }

    public void setLabel(Component label) {
        this.label = label;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        Theme t = ui.theme();
        boolean on = value.getAsBoolean();
        if (on != shown) {
            // value changed from outside (reset): animate too
            shown = on;
            changedAt = ui.now();
        }
        float progress = Draw.easeOut((ui.now() - changedAt) / (float) ANIMATION_MS);
        float position = on ? progress : 1 - progress; // 0 = left (off), 1 = right (on)
        int knobX = x + Math.round(position * KNOB);
        boolean hover = hovered(mouseX, mouseY);

        // track: accent part grows with the knob, off part on the right
        Draw.outline(g, x, y, TRACK, KNOB, t.outline());
        int accentW = knobX - x;
        int accent = enabled ? t.accent() : Draw.mix(t.accent(), t.panelBg(), 0.6f);
        int off = enabled ? t.toggleOffTrack() : Draw.mix(t.toggleOffTrack(), t.panelBg(), 0.6f);
        Draw.rect(g, x + 1, y + 1, accentW, KNOB - 2, accent);
        Draw.rect(g, knobX + KNOB - 1, y + 1, x + TRACK - 1 - (knobX + KNOB - 1), KNOB - 2, off);
        if (accentW > Icon.TOGGLE_ON.width() + 4) {
            Icon.TOGGLE_ON.drawCentered(g, x + 1, y, KNOB - 1, KNOB, t.onAccentText());
        }
        int offAreaX = knobX + KNOB;
        if (x + TRACK - offAreaX > Icon.TOGGLE_OFF.width() + 4) {
            Icon.TOGGLE_OFF.drawCentered(g, x + KNOB, y, KNOB - 1, KNOB, Draw.mix(t.toggleOffTrack(), t.outline(), 0.55f));
        }

        // knob
        int face = !enabled ? Draw.mix(t.buttonFace(), t.panelBg(), 0.55f) : hover ? t.buttonFaceHover() : t.buttonFace();
        Draw.bevel(g, knobX, y, KNOB, KNOB, face, t.buttonShadow(), t.outline(), false);

        // label on the right
        int labelX = x + TRACK + 6;
        Draw.text(g, ui.font(), label, labelX, y + (KNOB - 8) / 2, enabled ? t.textOnDark() : t.textDisabled());
        if (focused) {
            Draw.focusRing(g, x, y, TRACK, KNOB, t.accent());
        }
    }

    /** The label is part of the clickable area. */
    @Override
    public boolean contains(double mx, double my) {
        int saved = width;
        width = TRACK + 6 + ui.font().width(label);
        boolean inside = super.contains(mx, my);
        width = saved;
        return inside;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && enabled && contains(mx, my)) {
            toggle();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!enabled) {
            return false;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) {
            toggle();
            return true;
        }
        return false;
    }

    private void toggle() {
        ui.click();
        onChange.accept(!value.getAsBoolean());
    }

    @Override
    public Component narration() {
        return Component.translatable("confee.narration.toggle", label,
                Component.translatable(value.getAsBoolean() ? "confee.toggle.on" : "confee.toggle.off"));
    }
}

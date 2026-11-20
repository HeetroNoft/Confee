package fr.heetstudio.confee.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Key field: shows the key name; click (or Enter) then press a key or a mouse button to change it.
 * Escape cancels, Delete/Backspace clears ({@code key.keyboard.unknown}).
 * <p>Internal: may change without notice.</p>
 */
public class KeybindWidget extends ConfeeWidget {

    public static final String UNBOUND = "key.keyboard.unknown";

    private final Component label;
    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private boolean listening;

    public KeybindWidget(Ui ui, Component label, Supplier<String> getter, Consumer<String> setter) {
        super(ui, 100, 20);
        this.label = label;
        this.getter = getter;
        this.setter = setter;
    }

    /** Localized name of a key ({@code key.keyboard.g} → "G"), or the raw text when it is not a key. */
    public static Component keyName(String name) {
        try {
            return InputConstants.getKey(name).getDisplayName();
        } catch (IllegalArgumentException e) {
            return Component.literal(name);
        }
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        Theme t = ui.theme();
        boolean hover = hovered(mouseX, mouseY);
        int face;
        int text;
        if (!enabled) {
            face = Draw.mix(t.buttonFace(), t.panelBg(), 0.55f);
            text = t.textDisabled();
        } else if (listening) {
            face = t.accent();
            text = t.onAccentText();
        } else {
            face = hover ? t.buttonFaceHover() : t.buttonFace();
            text = t.textOnLight();
        }
        Draw.bevel(g, x, y, width, height, face, listening ? t.accentDark() : t.buttonShadow(), t.outline(), false);
        Component shown = listening ? Component.translatable("confee.keybind.listening")
                : UNBOUND.equals(getter.get()) ? Component.translatable("confee.keybind.none") : keyName(getter.get());
        String s = Draw.fit(ui.font(), shown.getString(), width - 10);
        Draw.textCentered(g, ui.font(), listening ? "> " + s + " <" : s, x + width / 2, y + (height - 2 - 8) / 2, text);
        if (focused) {
            Draw.focusRing(g, x, y, width, height, listening ? t.textOnDark() : t.accent());
        }
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            listening = false;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!enabled || !contains(mx, my)) {
            if (listening && enabled) {
                // a click elsewhere while waiting: that mouse button
                setter.accept(InputConstants.Type.MOUSE.getOrCreate(button).getName());
                listening = false;
            }
            return false;
        }
        if (listening) {
            setter.accept(InputConstants.Type.MOUSE.getOrCreate(button).getName());
            listening = false;
        } else {
            ui.click();
            listening = true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!enabled) {
            return false;
        }
        if (listening) {
            if (key != InputConstants.KEY_ESCAPE) {
                setter.accept(InputConstants.getKey(new net.minecraft.client.input.KeyEvent(key, scanCode, 0)).getName());
            }
            listening = false;
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) {
            ui.click();
            listening = true;
            return true;
        }
        if (key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE) {
            setter.accept(UNBOUND);
            return true;
        }
        return false;
    }

    @Override
    public boolean capturesText() {
        return listening;
    }

    @Override
    @Nullable
    public Component tooltip(double mouseX, double mouseY) {
        return listening ? null : Component.translatable("confee.tooltip.keybind");
    }

    @Override
    public Component narration() {
        return Component.translatable("confee.narration.keybind", label, keyName(getter.get()));
    }
}

package fr.heetstudio.confee.ui.widget;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Bedrock button (spec 10.3): light face, 1 px outline, 2 px bottom strip; pressed = strip gone, content 1 px lower.
 * {@link Style#PRIMARY} uses the accent color. {@link Style#NAVIGATION} adds a "›" chevron (navigation row).
 * <p>Internal: may change without notice.</p>
 */
public class ButtonWidget extends ConfeeWidget {

    public enum Style { NORMAL, PRIMARY, NAVIGATION }

    private static final long PRESS_FLASH_MS = 90;

    private Component label;
    private final Style style;
    private final Runnable onPress;
    private boolean selected;
    private boolean mouseDown;
    private long keyPressedAt = -1;
    @Nullable
    private Component disabledReason;
    @Nullable
    private Component tooltip;
    /** Item ID or texture drawn on the left (16 px), or null. */
    @Nullable
    private String icon;

    public ButtonWidget(Ui ui, Component label, Style style, Runnable onPress) {
        super(ui, ui.font().width(label) + 20, 20);
        this.label = label;
        this.style = style;
        this.onPress = onPress;
    }

    public void setLabel(Component label) {
        this.label = label;
    }

    public Component label() {
        return label;
    }

    /** Selected state (active category of the sidebar): accent face. */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setDisabledReason(@Nullable Component reason) {
        this.disabledReason = reason;
    }

    public void setTooltip(@Nullable Component tooltip) {
        this.tooltip = tooltip;
    }

    /** Icon on the left: an item ID or a texture (see {@code ItemIcons.drawIcon}). */
    public void setIcon(@Nullable String icon) {
        this.icon = icon;
    }

    /** Width that fits the label. */
    public int preferredWidth() {
        return ui.font().width(label) + 20 + (style == Style.NAVIGATION ? 12 : 0) + (icon != null ? 20 : 0);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        Theme t = ui.theme();
        boolean hover = hovered(mouseX, mouseY);
        boolean pressed = enabled && ((mouseDown && hover) || (keyPressedAt >= 0 && ui.now() - keyPressedAt < PRESS_FLASH_MS));
        boolean accent = style == Style.PRIMARY || selected;
        int face;
        int shadow;
        int textColor;
        if (!enabled) {
            face = Draw.mix(t.buttonFace(), t.panelBg(), 0.55f);
            shadow = Draw.mix(t.buttonShadow(), t.panelBg(), 0.55f);
            textColor = t.textDisabled();
        } else if (accent) {
            face = hover ? t.accentLight() : t.accent();
            shadow = t.accentDark();
            textColor = t.onAccentText();
        } else {
            face = hover ? t.buttonFaceHover() : t.buttonFace();
            shadow = t.buttonShadow();
            textColor = t.textOnLight();
        }
        Draw.bevel(g, x, y, width, height, face, shadow, t.outline(), pressed);
        int offset = pressed ? 1 : 0;
        int textY = y + (height - 2 - 8) / 2 + offset;
        int iconW = icon != null && height >= 18 ? 20 : 0;
        if (iconW > 0) {
            fr.heetstudio.confee.ui.ItemIcons.drawIcon(g, icon, x + 4, y + (height - 2 - 16) / 2 + offset, 16);
        }
        if (style == Style.NAVIGATION) {
            Draw.textFit(g, ui.font(), label, x + 8 + iconW, textY, width - 28 - iconW, textColor);
            Icon.CHEVRON_RIGHT.draw(g, x + width - 12, y + (height - 2 - 7) / 2 + offset, textColor);
        } else if (iconW > 0) {
            // icon + left-aligned text (sidebar categories)
            Draw.textFit(g, ui.font(), label, x + 4 + iconW, textY, width - 8 - iconW, textColor);
            if (selected && enabled) {
                int lineW = Math.min(ui.font().width(label), width - 8 - iconW);
                Draw.rect(g, x + 4 + iconW, y + height - 5 + offset, lineW, 1, t.onAccentText());
            }
        } else {
            String text = Draw.fit(ui.font(), label.getString(), width - 8);
            Draw.textCentered(g, ui.font(), text, x + width / 2, textY, textColor);
            if (selected && enabled) {
                // Bedrock marks the active item with a short light line under its text
                int lineW = Math.min(ui.font().width(text), width - 16);
                Draw.rect(g, x + (width - lineW) / 2, y + height - 5 + offset, lineW, 1, t.onAccentText());
            }
        }
        if (focused) {
            Draw.focusRing(g, x, y, width, height, accent ? t.textOnDark() : t.accent());
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT || !enabled || !contains(mx, my)) {
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
        if (enabled && contains(mx, my)) {
            press();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (enabled && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE)) {
            keyPressedAt = ui.now();
            press();
            return true;
        }
        return false;
    }

    protected void press() {
        ui.click();
        onPress.run();
    }

    @Override
    @Nullable
    public Component tooltip(double mouseX, double mouseY) {
        if (!enabled && disabledReason != null) {
            return disabledReason;
        }
        if (tooltip != null) {
            return tooltip;
        }
        // full text when the label is cut
        return ui.font().width(label) > width - 8 ? label : null;
    }

    @Override
    public Component narration() {
        return label;
    }
}

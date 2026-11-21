package fr.heetstudio.confee.api.client;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.widget.ButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws parts of Confee's look in your own widgets ({@link OptionWidget}) and previews, so they match the screen and
 * follow the theme. <b>Client only.</b> Text is drawn without shadow, like everywhere in Confee.
 *
 * <pre>{@code
 * // in OptionWidget#render
 * boolean hover = mouseX >= bx && mouseX < bx + 20 && mouseY >= y && mouseY < y + 20;
 * ConfeeDraw.button(g, theme(), bx, y, 20, 20, Component.literal("×"), hover, false, enabled());
 * }</pre>
 */
public final class ConfeeDraw {

    private ConfeeDraw() {
    }

    /**
     * Bedrock button: light face, outline, darker bottom strip; the label is centered and cut with "..." to fit.
     *
     * @param pressed true while the mouse button is held on it (the face moves down by 1 pixel)
     */
    public static void button(GuiGraphics g, Theme theme, int x, int y, int width, int height, Component label,
                              boolean hovered, boolean pressed, boolean enabled) {
        draw(g, theme, x, y, width, height, label, hovered, pressed, enabled, false);
    }

    /** Same as {@link #button}, with the accent color (main action, like "Save"). */
    public static void accentButton(GuiGraphics g, Theme theme, int x, int y, int width, int height, Component label,
                                    boolean hovered, boolean pressed, boolean enabled) {
        draw(g, theme, x, y, width, height, label, hovered, pressed, enabled, true);
    }

    private static void draw(GuiGraphics g, Theme theme, int x, int y, int width, int height, Component label,
                             boolean hovered, boolean pressed, boolean enabled, boolean accent) {
        int[] colors = ButtonWidget.colors(theme, enabled, accent, hovered && enabled);
        boolean down = pressed && enabled;
        Draw.bevel(g, x, y, width, height, colors[0], colors[1], theme.outline(), down);
        Font font = font();
        String text = Draw.fit(font, label.getString(), width - 6);
        Draw.textCentered(g, font, text, x + width / 2, y + (height - 2 - 8) / 2 + (down ? 1 : 0), colors[2]);
    }

    /** Text without shadow; returns the x after the text. */
    public static int text(GuiGraphics g, Component text, int x, int y, int color) {
        return Draw.text(g, font(), text, x, y, color);
    }

    /** Text without shadow, cut with "..." so it fits in {@code maxWidth}. */
    public static void textFit(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        Draw.textFit(g, font(), text, x, y, maxWidth, color);
    }

    /** Keyboard focus ring: 1 pixel of accent color around the area. */
    public static void focusRing(GuiGraphics g, Theme theme, int x, int y, int width, int height) {
        Draw.focusRing(g, x, y, width, height, theme.accent());
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}

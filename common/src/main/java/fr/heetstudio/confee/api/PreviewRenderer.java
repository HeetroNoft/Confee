package fr.heetstudio.confee.api;

/**
 * Draws a live preview of an option in the preview column. Client only: implementations may use client classes, they
 * are only created when the screen shows the preview.
 *
 * <p>Implement one of the two {@code render} methods. The one with a {@link PreviewContext} also gets the pending
 * values of the other options of the config and the theme of the screen.</p>
 *
 * <pre>{@code
 * public class HudPreview implements PreviewRenderer {
 *     public void render(GuiGraphics g, int x, int y, int width, int height, Object value,
 *                        PreviewContext context, float delta) {
 *         int opacity = (int) ((Double) value * 255);
 *         int color = Boolean.TRUE.equals(context.value("hud.blue")) ? 0x2D7DD2 : 0xD22D2D;
 *         g.fill(x, y, x + width, y + height, opacity << 24 | color);
 *     }
 * }
 * }</pre>
 */
public interface PreviewRenderer {

    /**
     * Draws the preview without context. Kept for the renderers written before {@link PreviewContext}; new
     * renderers implement the other {@code render} method.
     *
     * @param g      a {@code net.minecraft.client.gui.GuiGraphics}; drawing is clipped to the box
     * @param value  the value being edited (not saved yet), of the option's type
     * @param delta  partial tick, for animations
     */
    default void render(net.minecraft.client.gui.GuiGraphics g, int x, int y, int width, int height, Object value, float delta) {
    }

    /**
     * Draws the preview. By default calls {@link #render(net.minecraft.client.gui.GuiGraphics, int, int, int, int, Object, float)}.
     *
     * @param g       a {@code net.minecraft.client.gui.GuiGraphics}; drawing is clipped to the box
     * @param value   the value being edited (not saved yet), of the option's type
     * @param context pending values of the other options of the config, theme of the screen
     * @param delta   partial tick, for animations
     */
    default void render(net.minecraft.client.gui.GuiGraphics g, int x, int y, int width, int height, Object value,
                        PreviewContext context, float delta) {
        render(g, x, y, width, height, value, delta);
    }

    /** Height of the drawing box for a given width (default: 3/4 of the width). */
    default int height(int width) {
        return width * 3 / 4;
    }
}

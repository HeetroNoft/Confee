package fr.heetstudio.confee.api;

/**
 * Draws a live preview of an option in the preview column. Client only: implementations may use client classes, they
 * are only created when the screen shows the preview.
 *
 * <pre>{@code
 * public class HudPreview implements PreviewRenderer {
 *     public void render(GuiGraphicsExtractor g, int x, int y, int width, int height, Object value, float delta) {
 *         int opacity = (int) ((Double) value * 255);
 *         g.fill(x, y, x + width, y + height, opacity << 24 | 0x2D7DD2);
 *     }
 * }
 * }</pre>
 */
public interface PreviewRenderer {

    /**
     * @param g      a {@code net.minecraft.client.gui.GuiGraphicsExtractor}; drawing is clipped to the box
     * @param value  the value being edited (not saved yet), of the option's type
     * @param delta  partial tick, for animations
     */
    void render(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, int width, int height, Object value, float delta);

    /** Height of the drawing box for a given width (default: 3/4 of the width). */
    default int height(int width) {
        return width * 3 / 4;
    }
}

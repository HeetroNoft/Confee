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

    /**
     * Repeats a texture over an area (a stone brick wall...), clipped to it.
     *
     * @param texture a block or item texture of the atlas ({@code minecraft:block/stone_bricks}) or a texture file
     *                ending with {@code .png} ({@code examplemod:textures/gui/tile.png})
     * @return false when the texture does not exist
     */
    public static boolean tiled(GuiGraphics g, String texture, int x, int y, int width, int height) {
        try {
            return fr.heetstudio.confee.ui.render.Tiles.tiled(g, texture, x, y, width, height);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Draws a mob standing in a box (centered, feet at the bottom): its model, like in the inventory, when a world is
     * loaded; else a front view drawn from its skin for humanoid mobs (zombie, husk, drowned, skeleton, stray, wither
     * skeleton, player). With an outline, the front view is used: a 1 pixel line around the silhouette, like the
     * Glowing effect.
     *
     * @param entityId     e.g. {@code minecraft:zombie}
     * @param outlineColor ARGB color of the outline, or {@code 0} for none
     * @return false when nothing could be drawn (unknown mob, or no world for a non-humanoid mob)
     */
    public static boolean entity(GuiGraphics g, String entityId, int x, int y, int width, int height, int outlineColor) {
        return entity(g, entityId, x, y, width, height, outlineColor, false);
    }

    /**
     * Same as {@link #entity(GuiGraphics, String, int, int, int, int, int)}; with {@code outlineOnly}, only the outline is
     * drawn (the mob hidden behind blocks, seen through them like with Glowing).
     */
    public static boolean entity(GuiGraphics g, String entityId, int x, int y, int width, int height, int outlineColor,
                                 boolean outlineOnly) {
        try {
            return fr.heetstudio.confee.ui.render.MobDrawing.draw(g, entityId, x, y, width, height, outlineColor, outlineOnly);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
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

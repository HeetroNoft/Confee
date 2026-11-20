package fr.heetstudio.confee.ui.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Flat drawing primitives (spec 10.1: widgets are drawn by code so the accent color can be changed).
 * <p>Internal: may change without notice.</p>
 */
public final class Draw {

    private static final String ELLIPSIS = "...";

    private Draw() {
    }

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) {
            g.fill(x, y, x + w, y + h, color);
        }
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) {
            g.submitOutline(x, y, w, h, color);
        }
    }

    /**
     * Bedrock "3D" block (spec 10.3): 1 px outline, flat face, 2 px darker strip at the bottom.
     * Pressed: no strip (the caller moves the content down by 1 px).
     */
    public static void bevel(GuiGraphics g, int x, int y, int w, int h, int face, int shadow, int outline, boolean pressed) {
        outline(g, x, y, w, h, outline);
        if (pressed) {
            rect(g, x + 1, y + 1, w - 2, h - 2, face);
        } else {
            rect(g, x + 1, y + 1, w - 2, h - 4, face);
            rect(g, x + 1, y + h - 3, w - 2, 2, shadow);
        }
    }

    /** Keyboard focus ring (spec 10.3): 1 px around the widget. */
    public static void focusRing(GuiGraphics g, int x, int y, int w, int h, int color) {
        outline(g, x - 1, y - 1, w + 2, h + 2, color);
    }

    public static int text(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
        return x + font.width(text);
    }

    public static int text(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
        return x + font.width(text);
    }

    public static int text(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
        return x + font.width(text);
    }

    /** Draws the text cut with "..." so it fits in {@code maxWidth}. */
    public static void textFit(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color) {
        text(g, font, fit(font, text.getString(), maxWidth), x, y, color);
    }

    public static void textCentered(GuiGraphics g, Font font, String text, int centerX, int y, int color) {
        text(g, font, text, centerX - font.width(text) / 2, y, color);
    }

    public static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int room = maxWidth - font.width(ELLIPSIS);
        return room <= 0 ? "" : font.plainSubstrByWidth(text, room) + ELLIPSIS;
    }

    /** Linear blend of two ARGB colors, {@code t} in [0, 1]. */
    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    public static int withAlpha(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * Math.max(0, Math.min(1, alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Cubic ease-out, {@code t} in [0, 1]. */
    public static float easeOut(float t) {
        t = Math.max(0, Math.min(1, t));
        float u = 1 - t;
        return 1 - u * u * u;
    }
}

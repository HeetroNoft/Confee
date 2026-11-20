package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.widget.ButtonWidget;
import fr.heetstudio.confee.ui.widget.TextFieldWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Visual color picker: saturation/brightness square, hue bar, optional alpha bar, hex field, old/new swatches.
 * The color is applied live; "Cancel" (or Escape) puts the old one back.
 * <p>Internal: may change without notice.</p>
 */
final class ColorPickerPopup implements Overlay {

    private static final int PAD = 8;
    private static final int SQUARE = 110;
    private static final int BAR = 12;

    private final Ui ui;
    private final Component title;
    private final int initial;
    private final boolean alpha;
    private final IntConsumer onChange;
    private final Runnable onClose;
    private final TextFieldWidget hex;
    private final ButtonWidget ok;
    private final ButtonWidget cancel;
    private float hue;
    private float sat;
    private float val;
    private int alphaValue;
    /** 0 = none, 1 = square, 2 = hue, 3 = alpha. */
    private int dragging;
    private int px;
    private int py;
    private int pw;
    private int ph;

    ColorPickerPopup(Ui ui, Component title, int argb, boolean alpha, IntConsumer onChange, Runnable onClose) {
        this.ui = ui;
        this.title = title;
        this.initial = argb;
        this.alpha = alpha;
        this.onChange = onChange;
        this.onClose = onClose;
        setFrom(argb);
        this.hex = new TextFieldWidget(ui, title, hexText(argb), alpha ? 9 : 7, null, this::hexEdited);
        hex.setFocused(true);
        this.ok = new ButtonWidget(ui, Component.translatable("confee.button.ok"), ButtonWidget.Style.PRIMARY, onClose);
        this.cancel = new ButtonWidget(ui, Component.translatable("confee.button.cancel"), ButtonWidget.Style.NORMAL, this::cancel);
    }

    private void cancel() {
        onChange.accept(initial);
        onClose.run();
    }

    // ------------------------------------------------------------------ color math

    private void setFrom(int argb) {
        float[] hsv = toHsv((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
        if (hsv[1] > 0 && hsv[2] > 0) {
            hue = hsv[0];
        }
        sat = hsv[1];
        val = hsv[2];
        alphaValue = alpha ? argb >>> 24 : 0xFF;
    }

    private int color() {
        return (alphaValue << 24) | (hsvToRgb(hue, sat, val) & 0xFFFFFF);
    }

    static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float hp = (h % 1f) * 6f;
        float x = c * (1 - Math.abs(hp % 2 - 1));
        float r = 0, g = 0, b = 0;
        switch ((int) hp) {
            case 0 -> { r = c; g = x; }
            case 1 -> { r = x; g = c; }
            case 2 -> { g = c; b = x; }
            case 3 -> { g = x; b = c; }
            case 4 -> { r = x; b = c; }
            default -> { r = c; b = x; }
        }
        float m = v - c;
        return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    static float[] toHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float d = max - min;
        float h;
        if (d == 0) {
            h = 0;
        } else if (max == rf) {
            h = ((gf - bf) / d) % 6;
        } else if (max == gf) {
            h = (bf - rf) / d + 2;
        } else {
            h = (rf - gf) / d + 4;
        }
        h /= 6f;
        if (h < 0) {
            h += 1;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    private String hexText(int argb) {
        return alpha ? String.format(Locale.ROOT, "#%08X", argb) : String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }

    private void changed() {
        int c = color();
        hex.setTextSilently(hexText(c));
        hex.setResponder(this::hexEdited);
        hex.setError(null);
        onChange.accept(c);
    }

    private void hexEdited(String text) {
        String h = text.trim().startsWith("#") ? text.trim().substring(1) : text.trim();
        if ((h.length() == 6 || (alpha && h.length() == 8)) && h.matches("[0-9a-fA-F]+")) {
            long v = Long.parseLong(h, 16);
            int argb = h.length() == 6 ? (int) (0xFF000000L | v) : (int) v;
            setFrom(argb);
            hex.setError(null);
            onChange.accept(color());
        } else {
            hex.setError(Component.translatable(alpha ? "confee.error.color_alpha" : "confee.error.color"));
        }
    }

    // ------------------------------------------------------------------ layout / render

    private void layout(int screenW, int screenH) {
        pw = PAD + SQUARE + 6 + BAR + (alpha ? 6 + BAR : 0) + 10 + 90 + PAD;
        ph = PAD + 9 + 6 + SQUARE + 10 + 20 + PAD;
        px = (screenW - pw) / 2;
        py = Math.max(4, (screenH - ph) / 2);
    }

    private int squareX() {
        return px + PAD;
    }

    private int barsY() {
        return py + PAD + 15;
    }

    private int hueX() {
        return squareX() + SQUARE + 6;
    }

    private int alphaX() {
        return hueX() + BAR + 6;
    }

    private int sideX() {
        return (alpha ? alphaX() : hueX()) + BAR + 10;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        layout(screenW, screenH);
        g.pose().pushMatrix();
        // drawn above what is already on screen (no depth since 1.21.6)
        g.nextStratum();
        Draw.rect(g, 0, 0, screenW, screenH, 0xB0000000);
        Draw.rect(g, px + 2, py + 2, pw, ph, 0x80000000);
        Draw.rect(g, px, py, pw, ph, t.panelBg());
        Draw.outline(g, px, py, pw, ph, t.outline());
        Draw.textFit(g, ui.font(), title, px + PAD, py + PAD, pw - 2 * PAD, t.textOnDark());

        int sy = barsY();
        // saturation (left → right) / brightness (top → bottom)
        int sx = squareX();
        for (int i = 0; i < SQUARE; i++) {
            int top = hsvToRgb(hue, i / (float) (SQUARE - 1), 1f);
            g.fillGradient(sx + i, sy, sx + i + 1, sy + SQUARE, top, 0xFF000000);
        }
        Draw.outline(g, sx - 1, sy - 1, SQUARE + 2, SQUARE + 2, t.outline());
        int mx = sx + Math.round(sat * (SQUARE - 1));
        int my = sy + Math.round((1 - val) * (SQUARE - 1));
        Draw.outline(g, mx - 2, my - 2, 5, 5, val > 0.5f ? 0xFF000000 : 0xFFFFFFFF);

        // hue bar
        int hx = hueX();
        for (int s = 0; s < 6; s++) {
            int y0 = sy + s * SQUARE / 6;
            int y1 = sy + (s + 1) * SQUARE / 6;
            g.fillGradient(hx, y0, hx + BAR, y1, hsvToRgb(s / 6f, 1, 1), hsvToRgb((s + 1) / 6f % 1f + (s == 5 ? 0.9999f : 0), 1, 1));
        }
        Draw.outline(g, hx - 1, sy - 1, BAR + 2, SQUARE + 2, t.outline());
        int hy = sy + Math.round(hue * (SQUARE - 1));
        Draw.rect(g, hx - 2, hy - 1, BAR + 4, 3, 0xFFFFFFFF);
        Draw.outline(g, hx - 2, hy - 1, BAR + 4, 3, 0xFF000000);

        // alpha bar
        if (alpha) {
            int ax = alphaX();
            TextFieldWidget.checker(g, ax, sy, BAR, SQUARE);
            int rgb = color() & 0xFFFFFF;
            g.fillGradient(ax, sy, ax + BAR, sy + SQUARE, 0xFF000000 | rgb, rgb);
            Draw.outline(g, ax - 1, sy - 1, BAR + 2, SQUARE + 2, t.outline());
            int ay = sy + Math.round((1 - alphaValue / 255f) * (SQUARE - 1));
            Draw.rect(g, ax - 2, ay - 1, BAR + 4, 3, 0xFFFFFFFF);
            Draw.outline(g, ax - 2, ay - 1, BAR + 4, 3, 0xFF000000);
        }

        // old / new swatches and hex field
        int rx = sideX();
        int rw = px + pw - PAD - rx;
        Draw.text(g, ui.font(), Component.translatable("confee.color.new"), rx, sy, ui.secondaryText());
        TextFieldWidget.checker(g, rx, sy + 11, rw, 22);
        Draw.rect(g, rx, sy + 11, rw, 22, color());
        Draw.text(g, ui.font(), Component.translatable("confee.color.old"), rx, sy + 38, ui.secondaryText());
        TextFieldWidget.checker(g, rx, sy + 49, rw, 22);
        Draw.rect(g, rx, sy + 49, rw, 22, initial);
        Draw.outline(g, rx, sy + 11, rw, 22, t.outline());
        Draw.outline(g, rx, sy + 49, rw, 22, t.outline());
        hex.setBounds(rx, sy + SQUARE - TextFieldWidget.HEIGHT, rw, TextFieldWidget.HEIGHT);
        hex.render(g, mouseX, mouseY, delta);

        int by = py + ph - PAD - 20;
        int okW = Math.max(56, ok.preferredWidth());
        int cancelW = Math.max(56, cancel.preferredWidth());
        ok.setBounds(px + pw - PAD - okW, by, okW, 20);
        cancel.setBounds(ok.x - 4 - cancelW, by, cancelW, 20);
        cancel.render(g, mouseX, mouseY, delta);
        ok.render(g, mouseX, mouseY, delta);
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ input

    private void drag(double mx, double my) {
        int sy = barsY();
        float fy = (float) Math.max(0, Math.min(1, (my - sy) / (SQUARE - 1)));
        switch (dragging) {
            case 1 -> {
                sat = (float) Math.max(0, Math.min(1, (mx - squareX()) / (SQUARE - 1)));
                val = 1 - fy;
            }
            case 2 -> hue = Math.min(0.9999f, fy);
            case 3 -> alphaValue = Math.round((1 - fy) * 255);
            default -> {
                return;
            }
        }
        changed();
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x - 2 && mx < x + w + 2 && my >= y - 2 && my < y + h + 2;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int sy = barsY();
        if (in(mx, my, squareX(), sy, SQUARE, SQUARE)) {
            dragging = 1;
        } else if (in(mx, my, hueX(), sy, BAR, SQUARE)) {
            dragging = 2;
        } else if (alpha && in(mx, my, alphaX(), sy, BAR, SQUARE)) {
            dragging = 3;
        } else {
            dragging = 0;
            if (ok.mouseClicked(mx, my, button) || cancel.mouseClicked(mx, my, button)) {
                return true;
            }
            hex.mouseClicked(mx, my, button);
            if (mx < px || my < py || mx >= px + pw || my >= py + ph) {
                // click outside: keep the new color
                onClose.run();
            }
            return true;
        }
        drag(mx, my);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        drag(mx, my);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = 0;
        ok.mouseReleased(mx, my, button);
        cancel.mouseReleased(mx, my, button);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            ui.click();
            cancel();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            ui.click();
            onClose.run();
            return true;
        }
        hex.keyPressed(key, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        hex.charTyped(c, modifiers);
        return true;
    }

    @Override
    public Component narration() {
        return Component.translatable("confee.narration.color_picker", title, Component.literal(hexText(color())));
    }

    @Override
    @Nullable
    public Component tooltip(double mx, double my) {
        return hex.tooltip(mx, my);
    }
}

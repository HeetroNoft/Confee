package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Base of Confee screens: keyboard focus (Tab / Shift+Tab), mouse routing with drag capture, overlays
 * (dropdown popup, modal), delayed tooltips and narration of the focused widget (spec 10.5).
 * <p>Internal: may change without notice.</p>
 */
public abstract class ConfeeScreen extends Screen {

    protected final Screen parent;
    protected Ui ui;
    @Nullable
    protected Overlay overlay;
    @Nullable
    protected ConfeeWidget focus;
    @Nullable
    private ConfeeWidget dragTarget;
    @Nullable
    private Object hoverKey;
    private long hoverSince;
    /** When the tooltip that is (or was last) on screen appeared, and when it was last drawn. */
    private long tooltipShownAt = Long.MIN_VALUE / 2;
    private long tooltipLastDrawn = Long.MIN_VALUE / 2;

    protected ConfeeScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected abstract Theme theme();

    /** Interactive widgets currently on screen, in keyboard focus order. */
    protected abstract List<ConfeeWidget> widgets();

    /** Draws everything except overlays and tooltips. */
    protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta);

    /** Tooltip for non-widget areas (labels, icons), or null. Returns a key identifying the hovered area. */
    @Nullable
    protected TooltipTarget tooltipTarget(double mx, double my) {
        return null;
    }

    /**
     * A hovered area with a tooltip.
     *
     * @param anchor {x, y, width, height} of the hovered element: the tooltip is placed next to it, never over it
     *               (null = next to the mouse)
     */
    public record TooltipTarget(Object key, Component text, @Nullable int[] anchor) {
        public TooltipTarget(Object key, Component text) {
            this(key, text, null);
        }
    }

    /** Area the tooltip of an element should stay in ({x0, y0, x1, y1}); default: the whole screen. */
    protected int[] tooltipRegion(@Nullable int[] anchor) {
        return new int[]{0, 0, width, height};
    }

    @Override
    protected void init() {
        ui = new Ui(font, theme());
        addWidget(new NarrationAdapter());
        build();
    }

    /** Creates widgets; called on open and on every resize. */
    protected abstract void build();

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (focus != null && !widgets().contains(focus)) {
            focus = null;
        }
        // nothing under an overlay or under a floating list (suggestions) is hovered
        boolean covered = overlay != null || (focus != null && focus.floatingContains(mouseX, mouseY));
        int mx = covered ? -10_000 : mouseX;
        int my = covered ? -10_000 : mouseY;
        renderContent(g, mx, my, delta);
        if (focus != null && overlay == null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            focus.renderFloating(g, mouseX, mouseY, delta);
            g.pose().popPose();
        }
        if (overlay != null) {
            overlay.render(g, mouseX, mouseY, delta);
        }
        renderTooltip(g, mouseX, mouseY);
    }

    private void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        TooltipTarget target = null;
        if (overlay != null) {
            Component text = overlay.tooltip(mouseX, mouseY);
            target = text == null ? null : new TooltipTarget(overlay, text);
        } else if (focus != null && focus.floatingContains(mouseX, mouseY)) {
            target = null;
        } else {
            for (ConfeeWidget w : widgets()) {
                if (w.contains(mouseX, mouseY)) {
                    Component text = w.tooltip(mouseX, mouseY);
                    target = text == null ? null : new TooltipTarget(w, text, new int[]{w.x, w.y, w.width, w.height});
                    break;
                }
            }
            if (target == null) {
                target = tooltipTarget(mouseX, mouseY);
            }
        }
        Object key = target == null ? null : target.key();
        long now = ui.now();
        if (key == null || !key.equals(hoverKey)) {
            hoverKey = key;
            // moving from one tooltip to the next: no delay and no fade (the player is already reading tooltips)
            boolean warm = now - tooltipLastDrawn <= Ui.TOOLTIP_WARM_MS;
            hoverSince = warm ? now - Ui.TOOLTIP_DELAY_MS : now;
            tooltipShownAt = warm ? now - Ui.TOOLTIP_FADE_MS : now + Ui.TOOLTIP_DELAY_MS;
        }
        if (target == null || now - hoverSince < Ui.TOOLTIP_DELAY_MS) {
            return;
        }
        tooltipLastDrawn = now;
        float alpha = Draw.easeOut((now - tooltipShownAt) / (float) Ui.TOOLTIP_FADE_MS);
        drawTooltip(g, target.text(), mouseX, mouseY, target.anchor(), alpha);
    }

    private static final int TIP_PAD_X = 6;
    private static final int TIP_PAD_Y = 5;
    private static final int TIP_GAP = 3;

    /**
     * Bedrock tooltip: opaque dark box, light 1 px border, drop shadow. Placed under (else above) the hovered element
     * so it never hides it, inside {@link #tooltipRegion}, and away from an open suggestion list.
     */
    protected void drawTooltip(GuiGraphics g, Component text, int mouseX, int mouseY, @Nullable int[] anchor,
                               float alpha) {
        Theme t = ui.theme();
        int[] region = tooltipRegion(anchor);
        int maxText = Math.max(60, Math.min(200, region[2] - region[0] - 2 * TIP_PAD_X - 8));
        List<FormattedCharSequence> lines = font.split(text, maxText);
        int textW = 0;
        for (FormattedCharSequence line : lines) {
            textW = Math.max(textW, font.width(line));
        }
        int w = textW + 2 * TIP_PAD_X;
        int h = lines.size() * 10 - 2 + 2 * TIP_PAD_Y;
        int[] avoid = focus != null ? focus.floatingBounds() : null;
        int[] pos = place(anchor, region, avoid, mouseX, mouseY, w, h);
        if (pos == null) {
            return;
        }
        int x = pos[0];
        int y = pos[1];
        g.pose().pushPose();
        g.pose().translate(0, 0, 800);
        // fades in (opacity only, no movement); text alpha stays above the 4/255 the font treats as opaque
        Draw.rect(g, x + 2, y + 2, w, h, Draw.withAlpha(0x80000000, alpha));
        Draw.rect(g, x, y, w, h, Draw.withAlpha(Draw.mix(t.panelBgDeep(), 0xFF000000, 0.35f), alpha));
        Draw.outline(g, x, y, w, h, Draw.withAlpha(Draw.mix(t.textOnDark(), t.panelBgDeep(), 0.45f), alpha));
        int ly = y + TIP_PAD_Y;
        for (FormattedCharSequence line : lines) {
            Draw.text(g, font, line, x + TIP_PAD_X, ly, Draw.withAlpha(t.textOnDark(), Math.max(alpha, 0.05f)));
            ly += 10;
        }
        g.pose().popPose();
    }

    /** Top-left corner of the tooltip, or null when it can only cover the open suggestion list. */
    @Nullable
    private int[] place(@Nullable int[] anchor, int[] region, @Nullable int[] avoid, int mouseX, int mouseY, int w, int h) {
        int x = Math.max(region[0] + 2, Math.min(region[2] - 2 - w, mouseX - 8));
        if (anchor == null) {
            int y = mouseY + 12;
            if (y + h > height - 2) {
                y = Math.max(2, mouseY - 4 - h);
            }
            return new int[]{Math.max(2, Math.min(width - 2 - w, mouseX + 10)), y};
        }
        int below = anchor[1] + anchor[3] + TIP_GAP;
        int above = anchor[1] - TIP_GAP - h;
        int top = Math.max(2, region[1] + 2);
        int bottom = Math.min(height - 2, region[3] - 2);
        for (int y : new int[]{below, above}) {
            if (y >= top && y + h <= bottom && !intersects(x, y, w, h, avoid)) {
                return new int[]{x, y};
            }
        }
        // outside the region but still on screen
        for (int y : new int[]{below, above}) {
            if (y >= 2 && y + h <= height - 2 && !intersects(x, y, w, h, avoid)) {
                return new int[]{x, y};
            }
        }
        return null;
    }

    private static boolean intersects(int x, int y, int w, int h, @Nullable int[] r) {
        return r != null && x < r[0] + r[2] && x + w > r[0] && y < r[1] + r[3] && y + h > r[1];
    }

    // ------------------------------------------------------------------ overlays

    public void openOverlay(Overlay newOverlay) {
        closeOverlay();
        overlay = newOverlay;
        dragTarget = null;
        triggerImmediateNarration(false);
    }

    public void closeOverlay() {
        if (overlay != null) {
            Overlay old = overlay;
            overlay = null;
            old.closed();
        }
    }

    // ------------------------------------------------------------------ focus

    protected void setFocus(@Nullable ConfeeWidget widget) {
        if (focus == widget) {
            return;
        }
        if (focus != null) {
            focus.setFocused(false);
        }
        focus = widget;
        if (widget != null) {
            widget.setFocused(true);
            onFocused(widget);
        }
    }

    /** Internal (used by the test mod): the widget holding the keyboard focus, or null. */
    @Nullable
    public ConfeeWidget focusedWidget() {
        return focus;
    }

    /** Hook: e.g. scroll the panel so the focused widget is visible. */
    protected void onFocused(ConfeeWidget widget) {
    }

    private void moveFocus(boolean backwards) {
        List<ConfeeWidget> list = widgets().stream().filter(ConfeeWidget::isFocusable).toList();
        if (list.isEmpty()) {
            return;
        }
        int index = focus == null ? -1 : list.indexOf(focus);
        int next = index < 0 ? (backwards ? list.size() - 1 : 0) : Math.floorMod(index + (backwards ? -1 : 1), list.size());
        setFocus(list.get(next));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overlay != null) {
            return overlay.mouseClicked(mx, my, button);
        }
        if (focus != null && focus.floatingClicked(mx, my, button)) {
            return true;
        }
        for (ConfeeWidget w : widgets()) {
            if (w.contains(mx, my) && w.mouseClicked(mx, my, button)) {
                dragTarget = w;
                setFocus(w.isFocusable() ? w : null);
                return true;
            }
        }
        setFocus(null);
        return mouseClickedOutside(mx, my, button);
    }

    /** Click that hit no widget (scrollbars...). */
    protected boolean mouseClickedOutside(double mx, double my, int button) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (overlay != null) {
            return overlay.mouseReleased(mx, my, button);
        }
        ConfeeWidget target = dragTarget;
        dragTarget = null;
        if (target != null) {
            return target.mouseReleased(mx, my, button);
        }
        return mouseReleasedOutside(mx, my, button);
    }

    protected boolean mouseReleasedOutside(double mx, double my, int button) {
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (overlay != null) {
            return overlay.mouseDragged(mx, my, button, dx, dy);
        }
        if (dragTarget != null) {
            return dragTarget.mouseDragged(mx, my, button, dx, dy);
        }
        return mouseDraggedOutside(mx, my, button, dx, dy);
    }

    protected boolean mouseDraggedOutside(double mx, double my, int button, double dx, double dy) {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (overlay != null) {
            return overlay.mouseScrolled(mx, my, delta);
        }
        if (focus != null && focus.floatingScrolled(mx, my, delta)) {
            return true;
        }
        for (ConfeeWidget w : widgets()) {
            if (w.contains(mx, my) && w.mouseScrolled(mx, my, delta)) {
                return true;
            }
        }
        return scrolled(mx, my, delta);
    }

    protected boolean scrolled(double mx, double my, double delta) {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (overlay != null) {
            return overlay.keyPressed(key, scanCode, modifiers);
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (key == GLFW.GLFW_KEY_TAB && !ctrl) {
            moveFocus((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        if (focus != null && focus.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            requestClose();
            return true;
        }
        return shortcut(key, modifiers, focus != null && focus.capturesText());
    }

    /** Screen shortcuts, after the focused widget had the key. */
    protected boolean shortcut(int key, int modifiers, boolean typing) {
        return false;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (overlay != null) {
            return overlay.charTyped(c, modifiers);
        }
        return focus != null && focus.charTyped(c, modifiers);
    }

    @Override
    public void tick() {
        for (ConfeeWidget w : widgets()) {
            w.tick();
        }
    }

    // ------------------------------------------------------------------ closing

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /** Escape / back arrow. Screens with unsaved changes ask first. */
    protected void requestClose() {
        onClose();
    }

    @Override
    public void onClose() {
        closeOverlay();
        minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------ narration (spec 10.5)

    /** Lets the vanilla narrator read the focused (or hovered) Confee widget. */
    private final class NarrationAdapter implements GuiEventListener, NarratableEntry {

        @Override
        public NarrationPriority narrationPriority() {
            if (overlay != null || focus != null) {
                return NarrationPriority.FOCUSED;
            }
            return NarrationPriority.NONE;
        }

        @Override
        public void updateNarration(NarrationElementOutput output) {
            if (overlay != null) {
                output.add(NarratedElementType.TITLE, overlay.narration());
            } else if (focus != null) {
                output.add(NarratedElementType.TITLE, focus.narration());
            }
        }

        @Override
        public void setFocused(boolean focused) {
        }

        @Override
        public boolean isFocused() {
            return false;
        }
    }
}

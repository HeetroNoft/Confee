package fr.heetstudio.confee.ui.widget;

import fr.heetstudio.confee.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Base of Confee widgets (spec 10.3). Four states: normal, hover, keyboard focus, disabled.
 * Positions are set by the screen layout on every frame.
 * <p>Internal: may change without notice.</p>
 */
public abstract class ConfeeWidget {

    protected final Ui ui;
    public int x;
    public int y;
    public int width;
    public int height;
    protected boolean focused;
    protected boolean enabled = true;
    protected boolean visible = true;
    /** Visible area when the widget is inside a scrolling panel ({@code null} = whole screen). */
    @Nullable
    private int[] clip;

    protected ConfeeWidget(Ui ui, int width, int height) {
        this.ui = ui;
        this.width = width;
        this.height = height;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public abstract void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta);

    /** Text read by the narrator when the widget is focused or hovered. */
    public abstract Component narration();

    /** Tooltip under the mouse, or null. */
    @Nullable
    public Component tooltip(double mouseX, double mouseY) {
        return null;
    }

    // ------------------------------------------------------------------ state

    public boolean isFocusable() {
        return visible && enabled;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setClip(@Nullable int[] clip) {
        this.clip = clip;
    }

    /** Same visible area as another widget (for widgets drawn inside another one). */
    public void setClipFrom(ConfeeWidget other) {
        this.clip = other.clip;
    }

    /** Height for a given width (widgets whose height depends on the width override it). */
    public int heightFor(int width) {
        return height;
    }

    /** Whether the point is on the widget and inside its visible area. */
    public boolean contains(double mx, double my) {
        if (!visible) {
            return false;
        }
        if (clip != null && (mx < clip[0] || my < clip[1] || mx >= clip[2] || my >= clip[3])) {
            return false;
        }
        return mx >= x && my >= y && mx < x + width && my < y + height;
    }

    /** Height of the visible area (the scrolling panel), or of the screen when the widget is not clipped. */
    public int visibleHeight() {
        return clip != null ? clip[3] - clip[1] : ui.screenHeight();
    }

    /** Part to scroll into view when the widget gets the focus: {@code {x, y, width, height}} (default: all of it). */
    public int[] revealBounds() {
        return new int[]{x, y, width, height};
    }

    /** Error drawn under the widget by its row (a mod's own widget), or null; it blocks "Save". */
    @Nullable
    public Component widgetError() {
        return null;
    }

    /** Fully inside its visible area (used to scroll a focused widget into view). */
    public boolean isFullyVisible() {
        return clip == null || (y >= clip[1] && y + height <= clip[3]);
    }

    protected boolean hovered(int mouseX, int mouseY) {
        return enabled && contains(mouseX, mouseY);
    }

    // ------------------------------------------------------------------ input (return true when handled)

    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        return false;
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char c, int modifiers) {
        return false;
    }

    /** True for text inputs: the screen then leaves letter shortcuts to the widget. */
    public boolean capturesText() {
        return false;
    }

    public void tick() {
    }

    /** Content drawn above everything while the widget is focused (suggestion list...). */
    public void renderFloating(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
    }

    /** Click on the floating content; true when handled. */
    public boolean floatingClicked(double mx, double my, int button) {
        return false;
    }

    /** Whether the point is on the floating content (the content under it is then not hovered). */
    public boolean floatingContains(double mx, double my) {
        int[] r = floatingBounds();
        return r != null && mx >= r[0] && my >= r[1] && mx < r[0] + r[2] && my < r[1] + r[3];
    }

    /** {x, y, width, height} of the floating content when shown, else null. */
    @Nullable
    public int[] floatingBounds() {
        return null;
    }

    /** Mouse wheel on the floating content; true when handled. */
    public boolean floatingScrolled(double mx, double my, double delta) {
        return false;
    }
}

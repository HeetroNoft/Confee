package fr.heetstudio.confee.api.client;

import fr.heetstudio.confee.api.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Your own widget for an option, drawn under its name in place of Confee's widget. <b>Client only.</b>
 * Use it with {@code @Widget(MyWidget.class)} (the class needs a public no-argument constructor) or register it from
 * your client initializer with {@link ConfeeWidgets}.
 *
 * <p>The value you read and write is the pending value of the screen: it is saved with "Save" and dropped with
 * "Cancel", like the other options. Values are validated (bounds, allowed values...) before being kept.</p>
 *
 * <pre>{@code
 * public class TemperatureWidget extends OptionWidget<Integer> {
 *     public void render(GuiGraphics g, int x, int y, int width, int height, int mouseX, int mouseY, float delta) {
 *         int filled = width * value() / 100;
 *         g.fill(x, y, x + filled, y + height, theme().accent());
 *         g.drawString(font(), value() + " °C", x + 4, y + 6, theme().textOnDark());
 *     }
 *     public boolean mouseClicked(double mx, double my, int button) {
 *         setValue((int) ((mx - x()) * 100 / width()));
 *         return true;
 *     }
 * }
 * }</pre>
 *
 * @param <T> value type of the option (Boolean, Integer, String, your enum, List...)
 */
public abstract class OptionWidget<T> {

    private Context<T> context;
    private int x;
    private int y;
    private int width;
    private int height;

    /** Height for a given width, in GUI pixels (default 20). */
    public int height(int width) {
        return 20;
    }

    /** Draws the widget in its box. Coordinates are screen coordinates. */
    public abstract void render(GuiGraphics g, int x, int y, int width, int height, int mouseX, int mouseY, float delta);

    /** Click inside the box; return true when handled (the widget then gets the keyboard focus). */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    /** Key pressed while focused (Tab and Escape are kept by the screen). */
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char c, int modifiers) {
        return false;
    }

    /** Whether the widget takes the keyboard focus (Tab). Default: true. */
    public boolean focusable() {
        return true;
    }

    /** Text read by the narrator (default: the value). */
    public Component narration() {
        return Component.literal(String.valueOf(value()));
    }

    /**
     * Error to show under the widget, or null when the widget is valid (default). While it is not null, the error is
     * drawn in red under the widget like for Confee's fields, and "Save" is disabled.
     * <p>Example: {@code return parsed ? null : Component.translatable("examplemod.error.bad_entry");}</p>
     */
    @org.jetbrains.annotations.Nullable
    public Component error() {
        return null;
    }

    /**
     * True while the widget is typing text (a text field of yours has the focus): the screen then leaves plain keys
     * ({@code [}, {@code ]}, Page Up/Down...) to the widget. Shortcuts with Ctrl (Ctrl+Tab, Ctrl+F) still work: let
     * {@link #keyPressed} return false for them. Default false.
     */
    public boolean capturesText() {
        return false;
    }

    /**
     * Part of the widget to scroll into view when it gets the keyboard focus, as {@code {x, y, width, height}} in
     * screen coordinates, or null for the whole widget (default). Useful for a widget taller than the panel: return
     * the focused line, so the panel does not scroll to the bottom of the widget. See also {@link #revealFocus()}.
     */
    @org.jetbrains.annotations.Nullable
    public int[] focusBounds() {
        return null;
    }

    /**
     * Draws content above the rest of the screen (a suggestion list, a popup) while the widget has the focus. It is
     * not clipped to the panel and covers the options below. Declare its area with {@link #overlayBounds()}.
     */
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float delta) {
    }

    /**
     * Area of the content drawn by {@link #renderOverlay}, as {@code {x, y, width, height}} in screen coordinates,
     * or null when nothing is shown (default). Clicks and the mouse wheel on it go to {@link #overlayClicked} and
     * {@link #overlayScrolled}, and the options under it are not hovered.
     */
    @org.jetbrains.annotations.Nullable
    public int[] overlayBounds() {
        return null;
    }

    /** Click on the overlay area; return true when handled. */
    public boolean overlayClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /** Mouse wheel on the overlay area; return true when handled. */
    public boolean overlayScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    // ------------------------------------------------------------------ helpers for subclasses

    /** Pending value of the option. */
    protected final T value() {
        return context.get();
    }

    /** Sets the pending value; returns false when it is refused (wrong type, out of range...). */
    protected final boolean setValue(T value) {
        return context.set(value);
    }

    protected final T defaultValue() {
        return context.defaultValue();
    }

    /** Lower bound of a number option ({@code @Range}), or null. */
    @org.jetbrains.annotations.Nullable
    protected final Number minimum() {
        return context.minimum();
    }

    /** Upper bound of a number option ({@code @Range}), or null. */
    @org.jetbrains.annotations.Nullable
    protected final Number maximum() {
        return context.maximum();
    }

    /** Colors of the screen. */
    protected final Theme theme() {
        return context.theme();
    }

    protected final Font font() {
        return context.font();
    }

    /** Name of the option. */
    protected final Component label() {
        return context.label();
    }

    protected final boolean focused() {
        return context.focused();
    }

    /** False when the option is grayed out ({@code @EnabledIf}, read-only). */
    protected final boolean enabled() {
        return context.enabled();
    }

    /** Vanilla click sound. */
    protected final void playClick() {
        context.click();
    }

    /**
     * Height of the visible part of the options panel, in GUI pixels. Keep a tall widget below it (and scroll its
     * content yourself) so it always fits on the screen.
     */
    protected final int viewportHeight() {
        return context.viewportHeight();
    }

    /** Scrolls the panel so that {@link #focusBounds()} (or the whole widget) is visible, e.g. after a key moved the focused line. */
    protected final void revealFocus() {
        context.reveal();
    }

    protected final int x() {
        return x;
    }

    protected final int y() {
        return y;
    }

    protected final int width() {
        return width;
    }

    protected final int height() {
        return height;
    }

    // ------------------------------------------------------------------ internal

    /** Internal: set by Confee. */
    public final void bind(Context<T> context) {
        this.context = context;
    }

    /** Internal: set by Confee before each frame. */
    public final void place(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** Internal: link between the widget and Confee's screen. */
    public interface Context<T> {
        T get();

        boolean set(T value);

        T defaultValue();

        @org.jetbrains.annotations.Nullable
        Number minimum();

        @org.jetbrains.annotations.Nullable
        Number maximum();

        Theme theme();

        Font font();

        Component label();

        boolean focused();

        boolean enabled();

        void click();

        /** Height of the visible part of the options panel. */
        default int viewportHeight() {
            return 200;
        }

        /** Scrolls the panel to the focused part of the widget. */
        default void reveal() {
        }
    }
}

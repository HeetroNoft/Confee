package fr.heetstudio.confee.api.client;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Suggestions;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.widget.TextFieldWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Confee's text field for your own widgets ({@link OptionWidget}): theme colors, red border and message on error,
 * suggestion list with icons. It takes the same {@code int} key and mouse events as {@link OptionWidget} on every
 * Minecraft version. <b>Client only.</b>
 *
 * <p>Forward the events of your widget to it, and draw its suggestion list from {@link OptionWidget#renderOverlay}
 * so it covers the next options:</p>
 *
 * <pre>{@code
 * private final ConfeeTextField name = new ConfeeTextField(Component.literal("Name"));
 *
 * public void render(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY, float delta) {
 *     name.setBounds(x, y, width);
 *     name.render(g, theme(), mouseX, mouseY, delta);
 * }
 * public void renderOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) { name.renderSuggestions(g, mouseX, mouseY, delta); }
 * public int[] overlayBounds() { return name.suggestionBounds(); }
 * public boolean overlayClicked(double mx, double my, int button) { return name.suggestionsClicked(mx, my, button); }
 * public boolean mouseClicked(double mx, double my, int button) { name.setFocused(name.contains(mx, my)); return name.mouseClicked(mx, my, button); }
 * public boolean keyPressed(int key, int scanCode, int modifiers) { return name.keyPressed(key, scanCode, modifiers); }
 * public boolean charTyped(char c, int modifiers) { return name.charTyped(c, modifiers); }
 * public boolean capturesText() { return name.isFocused(); }
 * }</pre>
 */
public final class ConfeeTextField {

    /** Height of the field, in GUI pixels. */
    public static final int HEIGHT = TextFieldWidget.HEIGHT;

    private final Component label;
    private Theme theme = Theme.DEFAULT;
    private TextFieldWidget field;
    private String text = "";
    @Nullable
    private Consumer<String> responder;
    @Nullable
    private Predicate<String> filter;
    @Nullable
    private Component hint;
    @Nullable
    private Component error;
    @Nullable
    private Function<String, List<String>> suggester;
    @Nullable
    private String iconRegistry;
    private boolean focused;
    private boolean enabled = true;
    private int x;
    private int y;
    private int width = 100;
    private long lastTick;

    /** @param label read by the narrator */
    public ConfeeTextField(Component label) {
        this.label = label;
        rebuild();
    }

    /** The field is created again when the theme changes (each screen has its own). */
    private void rebuild() {
        Ui ui = new Ui(Minecraft.getInstance().font, theme);
        field = new TextFieldWidget(ui, label, text, 32767, filter, value -> {
            text = value;
            if (responder != null) {
                responder.accept(value);
            }
        });
        if (hint != null) {
            field.setHint(hint);
        }
        field.setError(error);
        field.setSuggester(suggester);
        field.setIconRegistry(iconRegistry);
        field.setFocused(focused);
        field.setEnabled(enabled);
        field.setBounds(x, y, width, HEIGHT);
        if (focused) {
            field.refreshSuggestions(); // the list of the previous field is not lost
        }
    }

    // ------------------------------------------------------------------ content

    public String text() {
        return text;
    }

    /** Sets the text without calling the responder. */
    public void setText(String value) {
        text = value == null ? "" : value;
        field.setTextSilently(text);
    }

    /** Called with the new text each time the player edits it. */
    public void setResponder(@Nullable Consumer<String> responder) {
        this.responder = responder;
    }

    /** Characters allowed while typing, e.g. {@code s -> s.chars().allMatch(Character::isDigit)}. */
    public void setFilter(@Nullable Predicate<String> filter) {
        this.filter = filter;
        rebuild();
    }

    /** Greyed text shown while the field is empty and not focused. */
    public void setHint(@Nullable Component hint) {
        this.hint = hint;
        rebuild();
    }

    /** Error: red border, message under the field ({@link #ERROR_HEIGHT} more pixels), or null. */
    public void setError(@Nullable Component error) {
        this.error = error;
        field.setError(error);
    }

    /** Extra height taken by the error message under the field. */
    public static final int ERROR_HEIGHT = 11;

    @Nullable
    public Component error() {
        return error;
    }

    /** Fixed suggestions, filtered while typing (forgiving match, "dmd" finds "diamond"). */
    public void setSuggestions(Collection<String> values) {
        List<String> all = List.copyOf(values);
        setSuggester(typed -> Suggestions.filter(all, typed, Suggestions.MAX));
    }

    /** Suggestions computed from the typed text, or null for none. */
    public void setSuggester(@Nullable Function<String, List<String>> suggester) {
        this.suggester = suggester;
        field.setSuggester(suggester);
    }

    /**
     * Suggests the entries of a registry ({@code minecraft:item}, {@code minecraft:entity_type}...), found by ID or
     * by their name in the player's language, with their icons.
     */
    public void setRegistry(@Nullable String registry) {
        this.iconRegistry = registry;
        field.setIconRegistry(registry);
        setSuggester(registry == null ? null : Suggestions.forSources(registry, false, null, "ConfeeTextField"));
    }

    // ------------------------------------------------------------------ state

    public void setBounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        field.setBounds(x, y, width, HEIGHT);
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEIGHT;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
        field.setFocused(focused);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        field.setEnabled(enabled);
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the field with the theme of your widget ({@code theme()}). */
    public void render(GuiGraphicsExtractor g, Theme theme, int mouseX, int mouseY, float delta) {
        if (theme != this.theme) {
            this.theme = theme;
            rebuild();
        }
        long now = System.currentTimeMillis();
        if (now - lastTick >= 50) {
            lastTick = now;
            field.tick(); // caret blink
        }
        field.render(g, mouseX, mouseY, delta);
        if (error != null) {
            ConfeeDraw.textFit(g, error, x, y + HEIGHT + 2, width, theme.error());
        }
    }

    /** Draws the suggestion list (call it from {@link OptionWidget#renderOverlay}). */
    public void renderSuggestions(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        field.renderFloating(g, mouseX, mouseY, delta);
    }

    /** Area of the suggestion list, {@code {x, y, width, height}}, or null when it is closed. */
    @Nullable
    public int[] suggestionBounds() {
        return field.floatingBounds();
    }

    public boolean suggestionsClicked(double mouseX, double mouseY, int button) {
        return field.floatingClicked(mouseX, mouseY, button);
    }

    public boolean suggestionsScrolled(double mouseX, double mouseY, double delta) {
        return field.floatingScrolled(mouseX, mouseY, delta);
    }

    // ------------------------------------------------------------------ input (same events as OptionWidget)

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return field.mouseClicked(mouseX, mouseY, button);
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return focused && field.keyPressed(key, scanCode, modifiers);
    }

    public boolean charTyped(char c, int modifiers) {
        return focused && field.charTyped(c, modifiers);
    }
}

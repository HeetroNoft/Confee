package fr.heetstudio.confee.ui.widget;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.ItemIcons;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Dark text field (spec 10.3): {@code panelBgDeep} background, light 1 px outline, accent when focused,
 * error color + message under the field when the text is invalid. Text editing is delegated to the vanilla
 * {@link EditBox} (caret, selection, clipboard), drawn without its own border.
 * <p>Options: color swatch on the right (click = color picker), picker button on the right (item grid), item icon
 * on the left, unit suffix, and a scrolling suggestion list (Up/Down, Tab/Enter, Escape; Down opens it).</p>
 * <p>Internal: may change without notice.</p>
 */
public class TextFieldWidget extends ConfeeWidget {

    public static final int HEIGHT = 18;
    private static final int SIDE_BUTTON = HEIGHT;
    private static final int VISIBLE_SUGGESTIONS = 6;

    private final EditBox box;
    private final Component label;
    private Consumer<String> responder;
    @Nullable
    private Component error;
    @Nullable
    private Component disabledReason;
    @Nullable
    private Component suffix;
    @Nullable
    private Icon leadingIcon;
    @Nullable
    private Integer swatchColor;
    private boolean showSwatch;
    @Nullable
    private Runnable swatchAction;
    @Nullable
    private Runnable pickerAction;
    @Nullable
    private Component pickerTooltip;
    /** Registry whose entry icons are drawn (item, entity...), or null. */
    @Nullable
    private String iconRegistry;

    // suggestions
    @Nullable
    private Function<String, List<String>> suggester;
    @Nullable
    private Component persistentHint;
    private List<String> suggestions = List.of();
    private int selectedSuggestion = -1;
    private int suggestionScroll;

    public TextFieldWidget(Ui ui, Component label, String text, int maxLength, @Nullable Predicate<String> filter,
                           Consumer<String> onEdit) {
        super(ui, 100, HEIGHT);
        this.label = label;
        this.box = new EditBox(ui.font(), 0, 0, 100, 9, label);
        box.setBordered(false);
        box.setMaxLength(maxLength);
        box.setTextColor(ui.theme().textOnDark());
        box.setTextColorUneditable(ui.theme().textDisabled());
        box.setValue(text);
        box.moveCursorToStart(false);
        if (filter != null) {
            box.setFilter(filter);
        }
        this.responder = onEdit;
        box.setResponder(onEdit);
    }

    public void setHint(Component hint) {
        box.setHint(hint);
    }

    /** Hint of an empty field that stays visible while typing (vanilla hints hide on focus). */
    public void setPersistentHint(@Nullable Component hint) {
        this.persistentHint = hint;
    }

    public void setLeadingIcon(@Nullable Icon icon) {
        this.leadingIcon = icon;
    }

    /** Shows a color preview square on the right. */
    public void setSwatch(@Nullable Integer argb) {
        this.showSwatch = true;
        this.swatchColor = argb;
    }

    /** Click on the color square (opens the color picker). */
    public void setSwatchAction(@Nullable Runnable action) {
        this.swatchAction = action;
    }

    /** Square button with a grid icon on the right (opens a picker). */
    public void setPickerAction(@Nullable Runnable action, @Nullable Component tooltip) {
        this.pickerAction = action;
        this.pickerTooltip = tooltip;
    }

    /** Icons of this registry drawn on the left of the text and of each suggestion (see {@code RegistryIcons}). */
    public void setIconRegistry(@Nullable String registry) {
        this.iconRegistry = fr.heetstudio.confee.ui.RegistryIcons.hasIcons(registry) ? registry : null;
    }

    /** Text shown at the right end of the field, e.g. a unit. */
    public void setSuffix(@Nullable Component suffix) {
        this.suffix = suffix;
    }

    /** Suggestions shown under the field while typing (Up/Down, Tab or Enter to accept). */
    public void setSuggester(@Nullable Function<String, List<String>> suggester) {
        this.suggester = suggester;
    }

    public void setDisabledReason(@Nullable Component reason) {
        this.disabledReason = reason;
    }

    public void setError(@Nullable Component error) {
        this.error = error;
    }

    @Nullable
    public Component error() {
        return error;
    }

    public String text() {
        return box.getValue();
    }

    /** Replaces the text without notifying the edit callback. */
    public void setTextSilently(String text) {
        if (!text.equals(box.getValue())) {
            box.setResponder(s -> { });
            box.setValue(text);
            box.moveCursorToStart(false);
            box.setResponder(responder);
        }
    }

    /** Replaces the text as if typed (the edit callback runs). */
    public void setTextAndNotify(String text) {
        box.setValue(text);
        box.moveCursorToEnd(false);
    }

    public void setResponder(Consumer<String> onEdit) {
        this.responder = onEdit;
        box.setResponder(onEdit);
    }

    private boolean hasSideButton() {
        return showSwatch || pickerAction != null;
    }

    private int fieldWidth() {
        return hasSideButton() ? width - SIDE_BUTTON - 4 : width;
    }

    private int rowHeight() {
        return iconRegistry != null ? 18 : 12;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        Theme t = ui.theme();
        int fw = fieldWidth();
        int border = error != null ? t.error() : focused ? t.accent() : t.textDisabled();
        if (!enabled) {
            border = Draw.mix(t.textDisabled(), t.panelBg(), 0.5f);
        }
        Draw.rect(g, x, y, fw, height, enabled ? t.panelBgDeep() : Draw.mix(t.panelBgDeep(), t.panelBg(), 0.5f));
        Draw.outline(g, x, y, fw, height, border);
        int textX = x + 5;
        if (leadingIcon != null) {
            leadingIcon.draw(g, x + 5, y + (height - leadingIcon.height()) / 2, t.textDisabled());
            textX += leadingIcon.width() + 4;
        }
        if (iconRegistry != null) {
            if (!fr.heetstudio.confee.ui.RegistryIcons.draw(g, iconRegistry, box.getValue().trim(), x + 2, y + 1, 16)) {
                Draw.outline(g, x + 4, y + 3, 12, 12, Draw.mix(t.textDisabled(), t.panelBgDeep(), 0.4f));
            }
            textX = x + 21;
        }
        int suffixW = suffix == null ? 0 : ui.font().width(suffix) + 6;
        if (suffix != null) {
            Draw.text(g, ui.font(), suffix, x + fw - 5 - ui.font().width(suffix), y + (height - 8) / 2, ui.secondaryText());
        }
        box.setX(textX);
        box.setY(y + (height - 8) / 2);
        box.setWidth(x + fw - 5 - textX - suffixW);
        box.setEditable(enabled);
        box.render(g, mouseX, mouseY, delta);
        if (persistentHint != null && box.getValue().isEmpty()) {
            // after the cursor when focused (the vanilla hint is drawn when not focused)
            if (focused) {
                Draw.textFit(g, ui.font(), persistentHint, textX + 6, y + (height - 8) / 2, x + fw - 11 - textX - suffixW, t.textDisabled());
            }
        }

        int sx = x + width - SIDE_BUTTON;
        if (showSwatch) {
            boolean hover = swatchAction != null && enabled && mouseX >= sx && mouseX < sx + SIDE_BUTTON && mouseY >= y && mouseY < y + height;
            Draw.outline(g, sx, y, SIDE_BUTTON, height, hover ? t.accent() : t.outline());
            if (swatchColor == null) {
                Draw.rect(g, sx + 1, y + 1, SIDE_BUTTON - 2, height - 2, t.panelBgDeep());
            } else {
                checker(g, sx + 1, y + 1, SIDE_BUTTON - 2, height - 2);
                Draw.rect(g, sx + 1, y + 1, SIDE_BUTTON - 2, height - 2, swatchColor);
            }
        } else if (pickerAction != null) {
            boolean hover = enabled && mouseX >= sx && mouseX < sx + SIDE_BUTTON && mouseY >= y && mouseY < y + height;
            int face = !enabled ? Draw.mix(t.buttonFace(), t.panelBg(), 0.55f) : hover ? t.buttonFaceHover() : t.buttonFace();
            Draw.bevel(g, sx, y, SIDE_BUTTON, height, face, t.buttonShadow(), t.outline(), false);
            Icon.GRID.drawCentered(g, sx, y, SIDE_BUTTON, height - 2, enabled ? t.textOnLight() : t.textDisabled());
        }
    }

    /** Light/dark squares behind translucent colors. */
    public static void checker(GuiGraphics g, int x, int y, int w, int h) {
        for (int cy = 0; cy < h; cy += 4) {
            for (int cx = 0; cx < w; cx += 4) {
                boolean light = ((cx + cy) / 4) % 2 == 0;
                Draw.rect(g, x + cx, y + cy, Math.min(4, w - cx), Math.min(4, h - cy), light ? 0xFFCCCCCC : 0xFF888888);
            }
        }
    }

    // ------------------------------------------------------------------ suggestions

    private void updateSuggestions(boolean open) {
        if (suggester == null || !focused) {
            suggestions = List.of();
            return;
        }
        String text = box.getValue();
        if (text.isEmpty() && !open) {
            suggestions = List.of();
            return;
        }
        List<String> found = suggester.apply(text);
        suggestions = found.size() == 1 && found.get(0).equals(text) ? List.of() : found;
        selectedSuggestion = suggestions.isEmpty() ? -1 : Math.min(selectedSuggestion, suggestions.size() - 1);
        suggestionScroll = 0;
        keepSelectionVisible();
    }

    private void keepSelectionVisible() {
        if (selectedSuggestion < 0) {
            return;
        }
        if (selectedSuggestion < suggestionScroll) {
            suggestionScroll = selectedSuggestion;
        } else if (selectedSuggestion >= suggestionScroll + VISIBLE_SUGGESTIONS) {
            suggestionScroll = selectedSuggestion - VISIBLE_SUGGESTIONS + 1;
        }
    }

    private int listHeight() {
        return Math.min(VISIBLE_SUGGESTIONS, suggestions.size()) * rowHeight() + 2;
    }

    /** Under the field, or above it when the list would leave the window. */
    private int suggestionsY() {
        int sh = listHeight();
        int below = y + height + 1;
        int screenH = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return below + sh > screenH && y - 1 - sh >= 0 ? y - 1 - sh : below;
    }

    @Override
    public void renderFloating(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (suggestions.isEmpty() || !focused) {
            return;
        }
        Theme t = ui.theme();
        int sx = x;
        int sy = suggestionsY();
        int sw = fieldWidth();
        int sh = listHeight();
        int rowH = rowHeight();
        // drop shadow, then the list
        Draw.rect(g, sx + 2, sy + 2, sw, sh, 0x66000000);
        Draw.rect(g, sx, sy, sw, sh, t.panelBgDeep());
        Draw.outline(g, sx, sy, sw, sh, t.outline());
        int count = Math.min(VISIBLE_SUGGESTIONS, suggestions.size());
        boolean scrollable = suggestions.size() > VISIBLE_SUGGESTIONS;
        int textRight = sw - (scrollable ? 8 : 4);
        for (int i = 0; i < count; i++) {
            int index = suggestionScroll + i;
            int iy = sy + 1 + i * rowH;
            boolean hover = mouseX >= sx && mouseX < sx + sw && mouseY >= iy && mouseY < iy + rowH;
            boolean selected = index == selectedSuggestion;
            if (selected || hover) {
                Draw.rect(g, sx + 1, iy, sw - 2, rowH, selected ? t.accent() : Draw.mix(t.panelBgDeep(), t.textOnDark(), 0.1f));
            }
            String value = suggestions.get(index);
            int tx = sx + 5;
            if (iconRegistry != null) {
                if (value.startsWith("#")) {
                    Draw.text(g, ui.font(), "#", sx + 7, iy + 5, ui.secondaryText());
                } else {
                    fr.heetstudio.confee.ui.RegistryIcons.draw(g, iconRegistry, value, sx + 2, iy + 1, 16);
                }
                tx = sx + 21;
            }
            Draw.textFit(g, ui.font(), Component.literal(value), tx, iy + (rowH - 8) / 2, textRight - (tx - sx),
                    selected ? t.onAccentText() : t.textOnDark());
        }
        if (scrollable) {
            int trackH = sh - 2;
            int handleH = Math.max(8, trackH * VISIBLE_SUGGESTIONS / suggestions.size());
            int maxScroll = suggestions.size() - VISIBLE_SUGGESTIONS;
            int handleY = sy + 1 + (trackH - handleH) * suggestionScroll / Math.max(1, maxScroll);
            Draw.rect(g, sx + sw - 4, sy + 1, 3, trackH, Draw.mix(t.panelBgDeep(), t.textOnDark(), 0.08f));
            Draw.rect(g, sx + sw - 4, handleY, 3, handleH, t.buttonFace());
        }
    }

    @Override
    @Nullable
    public int[] floatingBounds() {
        if (suggestions.isEmpty() || !focused) {
            return null;
        }
        return new int[]{x, suggestionsY(), fieldWidth() + 2, listHeight() + 2};
    }

    @Override
    public boolean floatingClicked(double mx, double my, int button) {
        if (!floatingContains(mx, my)) {
            return false;
        }
        int index = suggestionScroll + (int) ((my - suggestionsY() - 1) / rowHeight());
        if (index >= 0 && index < suggestions.size()) {
            accept(suggestions.get(index));
        }
        return true;
    }

    @Override
    public boolean floatingScrolled(double mx, double my, double delta) {
        if (!floatingContains(mx, my)) {
            return false;
        }
        int maxScroll = Math.max(0, suggestions.size() - VISIBLE_SUGGESTIONS);
        suggestionScroll = Math.max(0, Math.min(maxScroll, suggestionScroll - (int) Math.signum(delta)));
        return true;
    }

    private void accept(String suggestion) {
        ui.click();
        box.setValue(suggestion);
        box.moveCursorToEnd(false);
        suggestions = List.of();
        selectedSuggestion = -1;
    }

    // ------------------------------------------------------------------ input

    @Override
    public void setFocused(boolean focused) {
        boolean gained = focused && !this.focused;
        super.setFocused(focused);
        box.setFocused(focused);
        if (gained) {
            // keyboard focus: caret at the end, ready to type (a mouse click then places it precisely)
            box.moveCursorToEnd(false);
        }
        if (!focused) {
            suggestions = List.of();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!enabled || !contains(mx, my)) {
            return false;
        }
        if (mx >= x + fieldWidth()) {
            Runnable action = showSwatch ? swatchAction : pickerAction;
            if (action != null && mx >= x + width - SIDE_BUTTON) {
                ui.click();
                action.run();
                return true;
            }
            return false;
        }
        box.setFocused(true);
        box.mouseClicked(mx, my, button);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!enabled) {
            return false;
        }
        if (!suggestions.isEmpty()) {
            switch (key) {
                case GLFW.GLFW_KEY_DOWN -> {
                    selectedSuggestion = Math.min(suggestions.size() - 1, selectedSuggestion + 1);
                    keepSelectionVisible();
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    selectedSuggestion = Math.max(0, selectedSuggestion - 1);
                    keepSelectionVisible();
                    return true;
                }
                case GLFW.GLFW_KEY_PAGE_DOWN -> {
                    selectedSuggestion = Math.min(suggestions.size() - 1, selectedSuggestion + VISIBLE_SUGGESTIONS);
                    keepSelectionVisible();
                    return true;
                }
                case GLFW.GLFW_KEY_PAGE_UP -> {
                    selectedSuggestion = Math.max(0, selectedSuggestion - VISIBLE_SUGGESTIONS);
                    keepSelectionVisible();
                    return true;
                }
                case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (selectedSuggestion >= 0 || key == GLFW.GLFW_KEY_TAB) {
                        accept(suggestions.get(Math.max(0, selectedSuggestion)));
                        return true;
                    }
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    suggestions = List.of();
                    return true;
                }
                default -> {
                }
            }
        } else if (suggester != null && key == GLFW.GLFW_KEY_DOWN) {
            // Down opens the list, even when the field is empty
            updateSuggestions(true);
            if (!suggestions.isEmpty()) {
                selectedSuggestion = 0;
                return true;
            }
        }
        boolean handled = box.keyPressed(key, scanCode, modifiers);
        if (handled) {
            updateSuggestions(false);
        }
        return handled;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        boolean handled = enabled && box.charTyped(c, modifiers);
        if (handled) {
            selectedSuggestion = -1;
            updateSuggestions(false);
        }
        return handled;
    }

    @Override
    public boolean capturesText() {
        return true;
    }

    @Override
    public void tick() {
        // the vanilla cursor blinks on its own since 1.20.2
    }

    @Override
    @Nullable
    public Component tooltip(double mouseX, double mouseY) {
        if (!enabled && disabledReason != null) {
            return disabledReason;
        }
        if (pickerAction != null && !showSwatch && mouseX >= x + width - SIDE_BUTTON) {
            return pickerTooltip;
        }
        if (showSwatch && swatchAction != null && mouseX >= x + width - SIDE_BUTTON) {
            return Component.translatable("confee.tooltip.color_picker");
        }
        return error;
    }

    @Override
    public Component narration() {
        Component value = Component.literal(box.getValue());
        return error == null ? Component.translatable("confee.narration.field", label, value)
                : Component.translatable("confee.narration.field_error", label, value, error);
    }
}

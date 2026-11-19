package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.api.PickerStyle;
import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Suggestions;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import fr.heetstudio.confee.ui.widget.TextFieldWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Picker of IDs with icons (items, blocks, entities, mods...) and a search field (ID or name). Three styles, chosen by
 * the dev ({@code @Picker}) and switched by the player with the buttons of the header (or Tab): a grid of icons, a
 * list (icon, name, ID) and a detailed list (plus the description of mods). Click, or arrows + Enter, to choose;
 * Escape closes.
 * <p>Internal: may change without notice.</p>
 */
final class ItemPickerPopup implements Overlay {

    private static final int PAD = 8;
    private static final int CELL = 20;
    private static final int ROW_H = 20;
    private static final int DETAILED_H = 30;
    private static final int STYLE_BUTTON = 14;

    private record Entry(String id, String name, String initials, @Nullable String description) {
    }

    /** Styles the dev allows, the one to open with, and what to do when the player switches. */
    record Styles(List<PickerStyle> allowed, PickerStyle initial, Consumer<PickerStyle> onChange) {

        /** Styles of the option (the dev's choice) opening on the player's last choice for this kind of entry. */
        static Styles of(@Nullable fr.heetstudio.confee.core.Option<?> option, String registry) {
            PickerStyle remembered = fr.heetstudio.confee.ConfeeSettings.rememberedPickerStyle(registry);
            return new Styles(fr.heetstudio.confee.core.Pickers.allowed(option),
                    fr.heetstudio.confee.core.Pickers.initial(option, registry, remembered),
                    style -> fr.heetstudio.confee.ConfeeSettings.rememberPickerStyle(registry, style));
        }
    }

    private final String registry;
    private final Ui ui;
    private final Component title;
    private final Consumer<String> onPick;
    private final Runnable onClose;
    private final TextFieldWidget search;
    private final List<Entry> all = new ArrayList<>();
    private final Styles styles;
    private PickerStyle style;
    private List<Entry> shown;
    private int selected;
    private int scrollRow;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int columns;
    private int visibleRows;
    private int cellW;
    private int cellH;

    /**
     * @param hidden IDs not offered (already in the list being edited)
     */
    ItemPickerPopup(Ui ui, Component title, String registry, String current, java.util.Set<String> hidden,
                    Styles styles, Consumer<String> onPick, Runnable onClose) {
        this.ui = ui;
        this.title = title;
        this.onPick = onPick;
        this.onClose = onClose;
        this.registry = registry;
        this.styles = styles;
        this.style = styles.initial();
        boolean mods = fr.heetstudio.confee.core.PseudoRegistries.isModList(registry);
        for (String id : fr.heetstudio.confee.ui.RegistryEntries.ids(registry)) {
            if (hidden.contains(id) && !id.equals(current)) {
                continue;
            }
            Component name = fr.heetstudio.confee.ui.RegistryIcons.name(registry, id);
            String path = id.substring(id.indexOf(':') + 1);
            String description = mods ? fr.heetstudio.confee.platform.Services.PLATFORM.getModDescription(id).orElse(null) : null;
            all.add(new Entry(id, name == null ? path : name.getString(),
                    path.substring(0, Math.min(2, path.length())).toUpperCase(Locale.ROOT), description));
        }
        this.shown = all;
        this.search = new TextFieldWidget(ui, Component.translatable("confee.picker.search"), "", 64, null, this::filter);
        search.setHint(Component.translatable("confee.picker.search"));
        search.setLeadingIcon(Icon.SEARCH);
        search.setFocused(true);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(current)) {
                selected = i;
                break;
            }
        }
    }

    private void filter(String text) {
        String q = text.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            shown = all;
        } else {
            record Scored(Entry entry, int score) {
            }
            List<Scored> scored = new ArrayList<>();
            for (Entry e : all) {
                int byId = Suggestions.score(e.id(), q);
                String name = e.name().toLowerCase(Locale.ROOT);
                int byName = name.startsWith(q) ? 1 : name.contains(q) ? 3 : -1;
                int score = byId < 0 ? byName : byName < 0 ? byId : Math.min(byId, byName);
                if (score >= 0) {
                    scored.add(new Scored(e, score));
                }
            }
            scored.sort(Comparator.comparingInt(Scored::score));
            shown = scored.stream().map(Scored::entry).toList();
        }
        selected = 0;
        scrollRow = 0;
    }

    // ------------------------------------------------------------------ layout

    private void layout(int screenW, int screenH) {
        pw = Math.min(screenW - 20, 9 * CELL * 2 + 2 * PAD);
        ph = Math.min(screenH - 20, 260);
        px = (screenW - pw) / 2;
        py = Math.max(4, (screenH - ph) / 2);
        int innerW = pw - 2 * PAD - 6;
        int innerH = ph - (PAD + 9 + 6 + TextFieldWidget.HEIGHT + 6) - PAD;
        switch (style) {
            case LIST -> {
                cellW = innerW;
                cellH = ROW_H;
            }
            case DETAILED -> {
                cellW = innerW;
                cellH = DETAILED_H;
            }
            default -> {
                cellW = CELL;
                cellH = CELL;
            }
        }
        columns = Math.max(1, innerW / cellW);
        visibleRows = Math.max(1, innerH / cellH);
    }

    private int gridX() {
        return px + PAD + (pw - 2 * PAD - 6 - columns * cellW) / 2;
    }

    private int gridY() {
        return py + PAD + 9 + 6 + TextFieldWidget.HEIGHT + 6;
    }

    private int rows() {
        return (shown.size() + columns - 1) / columns;
    }

    private void keepSelectedVisible() {
        int row = selected / Math.max(1, columns);
        if (row < scrollRow) {
            scrollRow = row;
        } else if (row >= scrollRow + visibleRows) {
            scrollRow = row - visibleRows + 1;
        }
    }

    /** X of the style switch button {@code i}, left of the result count. */
    private int styleButtonX(int i, int countWidth) {
        int n = styles.allowed().size();
        return px + pw - PAD - countWidth - 6 - (n - i) * (STYLE_BUTTON + 2);
    }

    private int countWidth() {
        return ui.font().width(Component.translatable("confee.picker.count", shown.size()));
    }

    @Nullable
    private PickerStyle styleButtonAt(double mx, double my) {
        if (styles.allowed().size() < 2 || my < py + PAD - 3 || my >= py + PAD - 3 + STYLE_BUTTON) {
            return null;
        }
        int cw = countWidth();
        for (int i = 0; i < styles.allowed().size(); i++) {
            int bx = styleButtonX(i, cw);
            if (mx >= bx && mx < bx + STYLE_BUTTON) {
                return styles.allowed().get(i);
            }
        }
        return null;
    }

    private void setStyle(PickerStyle next) {
        if (next == style) {
            return;
        }
        style = next;
        styles.onChange().accept(next);
        scrollRow = 0;
        // the layout is computed on the next frame; keep the selection in view then
        pendingReveal = true;
    }

    private boolean pendingReveal = true;

    private static Icon iconOf(PickerStyle s) {
        return switch (s) {
            case LIST -> Icon.LIST;
            case DETAILED -> Icon.DETAILED;
            default -> Icon.GRID;
        };
    }

    private static Component nameOf(PickerStyle s) {
        return Component.translatable("confee.picker.style." + s.name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        layout(screenW, screenH);
        if (pendingReveal) {
            keepSelectedVisible();
            pendingReveal = false;
        }
        scrollRow = Math.max(0, Math.min(Math.max(0, rows() - visibleRows), scrollRow));
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        Draw.rect(g, 0, 0, screenW, screenH, 0xB0000000);
        Draw.rect(g, px + 2, py + 2, pw, ph, 0x80000000);
        Draw.rect(g, px, py, pw, ph, t.panelBg());
        Draw.outline(g, px, py, pw, ph, t.outline());
        int cw = countWidth();
        int switchW = styles.allowed().size() < 2 ? 0 : styles.allowed().size() * (STYLE_BUTTON + 2) + 6;
        Draw.textFit(g, ui.font(), title, px + PAD, py + PAD, pw - 2 * PAD - cw - switchW - 8, t.textOnDark());
        Component count = Component.translatable("confee.picker.count", shown.size());
        Draw.text(g, ui.font(), count, px + pw - PAD - cw, py + PAD, ui.secondaryText());
        if (styles.allowed().size() > 1) {
            for (int i = 0; i < styles.allowed().size(); i++) {
                PickerStyle s = styles.allowed().get(i);
                int bx = styleButtonX(i, cw);
                int by = py + PAD - 3;
                boolean hover = mouseX >= bx && mouseX < bx + STYLE_BUTTON && mouseY >= by && mouseY < by + STYLE_BUTTON;
                if (s == style) {
                    Draw.rect(g, bx, by, STYLE_BUTTON, STYLE_BUTTON, t.accent());
                } else if (hover) {
                    Draw.rect(g, bx, by, STYLE_BUTTON, STYLE_BUTTON, Draw.mix(t.panelBg(), t.textOnDark(), 0.15f));
                }
                iconOf(s).drawCentered(g, bx, by, STYLE_BUTTON, STYLE_BUTTON,
                        s == style ? t.onAccentText() : t.textOnDark());
            }
        }
        search.setBounds(px + PAD, py + PAD + 15, pw - 2 * PAD, TextFieldWidget.HEIGHT);
        search.render(g, mouseX, mouseY, delta);

        int gx = gridX();
        int gy = gridY();
        Draw.rect(g, px + PAD, gy - 2, pw - 2 * PAD, visibleRows * cellH + 4, t.panelBgDeep());
        if (shown.isEmpty()) {
            Draw.textCentered(g, ui.font(), Component.translatable("confee.picker.none").getString(), px + pw / 2, gy + 10, ui.secondaryText());
        }
        for (int r = 0; r < visibleRows; r++) {
            for (int c = 0; c < columns; c++) {
                int index = (scrollRow + r) * columns + c;
                if (index >= shown.size()) {
                    break;
                }
                int cx = gx + c * cellW;
                int cy = gy + r * cellH;
                boolean hover = mouseX >= cx && mouseX < cx + cellW && mouseY >= cy && mouseY < cy + cellH;
                boolean isSelected = index == selected;
                if (isSelected) {
                    Draw.rect(g, cx, cy, cellW, cellH, t.accent());
                } else if (hover) {
                    Draw.rect(g, cx, cy, cellW, cellH, Draw.mix(t.panelBgDeep(), t.textOnDark(), 0.15f));
                }
                Entry entry = shown.get(index);
                if (style == PickerStyle.GRID) {
                    icon(g, entry, cx + 2, cy + 2, 16, isSelected ? t.onAccentText() : ui.secondaryText());
                } else {
                    row(g, t, entry, cx, cy, isSelected);
                }
            }
        }
        // scrollbar
        if (rows() > visibleRows) {
            int trackX = px + pw - PAD - 4;
            int trackH = visibleRows * cellH;
            int handleH = Math.max(10, trackH * visibleRows / rows());
            int handleY = gy + (trackH - handleH) * scrollRow / Math.max(1, rows() - visibleRows);
            Draw.rect(g, trackX, gy, 3, trackH, Draw.mix(t.panelBgDeep(), t.textOnDark(), 0.08f));
            Draw.rect(g, trackX, handleY, 3, handleH, t.buttonFace());
        }
        g.pose().popPose();
    }

    /** The icon, or the first letters of the entry when it has none (an entity without spawn egg, a mod without logo). */
    private void icon(GuiGraphics g, Entry entry, int x, int y, int size, int initialsColor) {
        if (!fr.heetstudio.confee.ui.RegistryIcons.draw(g, registry, entry.id(), x, y, size)) {
            Draw.textCentered(g, ui.font(), entry.initials(), x + size / 2, y + (size - 8) / 2, initialsColor);
        }
    }

    /** One entry of the list styles: icon, name and ID (right), then the description on a second line (detailed). */
    private void row(GuiGraphics g, Theme t, Entry entry, int x, int y, boolean selected) {
        int text = selected ? t.onAccentText() : t.textOnDark();
        int secondary = selected ? Draw.mix(t.onAccentText(), t.accent(), 0.25f) : ui.secondaryText();
        // detailed rows: the icon takes the whole height of the row (2 px padding)
        int iconSize = style == PickerStyle.DETAILED ? cellH - 4 : 16;
        icon(g, entry, x + 2, y + 2, iconSize, secondary);
        int textX = x + 2 + iconSize + 6;
        int textW = cellW - (textX - x) - 4;
        boolean sameAsName = entry.id().equalsIgnoreCase(entry.name());
        if (style == PickerStyle.DETAILED) {
            String second = entry.description() != null ? entry.description().lines().findFirst().orElse("")
                    : sameAsName ? "" : entry.id();
            String idRight = entry.description() != null && !sameAsName ? entry.id() : "";
            line(g, entry.name(), idRight, textX, y + 3, textW, text, secondary);
            if (!second.isEmpty()) {
                Draw.textFit(g, ui.font(), Component.literal(second), textX, y + 16, textW, secondary);
            }
        } else {
            line(g, entry.name(), sameAsName ? "" : entry.id(), textX, y + 6, textW, text, secondary);
        }
    }

    /** Name on the left, ID on the right; the ID gives way to the name when the row is too narrow. */
    private void line(GuiGraphics g, String name, String id, int x, int y, int w, int nameColor, int idColor) {
        int idW = id.isEmpty() ? 0 : Math.min(ui.font().width(id), w / 2);
        Draw.textFit(g, ui.font(), Component.literal(name), x, y, w - (idW > 0 ? idW + 8 : 0), nameColor);
        if (idW > 0) {
            String fitted = Draw.fit(ui.font(), id, idW);
            Draw.text(g, ui.font(), Component.literal(fitted), x + w - ui.font().width(fitted), y, idColor);
        }
    }

    // ------------------------------------------------------------------ input

    @Nullable
    private Integer indexAt(double mx, double my) {
        int gx = gridX();
        int gy = gridY();
        if (mx < gx || my < gy || mx >= gx + columns * cellW || my >= gy + visibleRows * cellH) {
            return null;
        }
        int index = (scrollRow + (int) ((my - gy) / cellH)) * columns + (int) ((mx - gx) / cellW);
        return index < shown.size() ? index : null;
    }

    private void pick(int index) {
        ui.click();
        onPick.accept(shown.get(index).id());
        onClose.run();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        PickerStyle switchTo = styleButtonAt(mx, my);
        if (switchTo != null) {
            ui.click();
            setStyle(switchTo);
            return true;
        }
        Integer index = indexAt(mx, my);
        if (index != null) {
            pick(index);
            return true;
        }
        if (mx < px || my < py || mx >= px + pw || my >= py + ph) {
            onClose.run();
            return true;
        }
        search.mouseClicked(mx, my, button);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int step = style == PickerStyle.GRID ? 2 : 3;
        scrollRow = Math.max(0, Math.min(Math.max(0, rows() - visibleRows), scrollRow - (int) Math.signum(delta) * step));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                ui.click();
                onClose.run();
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (!shown.isEmpty()) {
                    pick(Math.min(selected, shown.size() - 1));
                }
            }
            case GLFW.GLFW_KEY_TAB -> {
                // next allowed style (Shift: previous)
                List<PickerStyle> allowed = styles.allowed();
                if (allowed.size() > 1) {
                    int i = allowed.indexOf(style);
                    int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? allowed.size() - 1 : 1;
                    ui.click();
                    setStyle(allowed.get((Math.max(0, i) + step) % allowed.size()));
                }
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (columns > 1) {
                    move(1);
                } else {
                    search.keyPressed(key, scanCode, modifiers);
                }
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (columns > 1) {
                    move(-1);
                } else {
                    search.keyPressed(key, scanCode, modifiers);
                }
            }
            case GLFW.GLFW_KEY_DOWN -> move(columns);
            case GLFW.GLFW_KEY_UP -> move(-columns);
            case GLFW.GLFW_KEY_PAGE_DOWN -> move(columns * visibleRows);
            case GLFW.GLFW_KEY_PAGE_UP -> move(-columns * visibleRows);
            default -> search.keyPressed(key, scanCode, modifiers);
        }
        return true;
    }

    private void move(int delta) {
        if (shown.isEmpty()) {
            return;
        }
        selected = Math.max(0, Math.min(shown.size() - 1, selected + delta));
        keepSelectedVisible();
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        search.charTyped(c, modifiers);
        return true;
    }

    @Override
    public Component narration() {
        if (shown.isEmpty()) {
            return Component.translatable("confee.picker.none");
        }
        Entry e = shown.get(Math.min(selected, shown.size() - 1));
        return Component.translatable("confee.narration.picker", title, Component.literal(e.name()));
    }

    @Override
    @Nullable
    public Component tooltip(double mx, double my) {
        PickerStyle button = styleButtonAt(mx, my);
        if (button != null) {
            return nameOf(button).copy().append(Component.literal("\n").append(Component.translatable("confee.picker.style.switch"))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        Integer index = indexAt(mx, my);
        if (index == null) {
            return null;
        }
        Entry e = shown.get(index);
        if (style == PickerStyle.GRID) {
            return Component.literal(e.name()).append(Component.literal("\n" + e.id()).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        // the rows already show the name and the ID: the tooltip gives the whole description, when it is cut
        return e.description() == null ? null : Component.literal(e.description());
    }
}

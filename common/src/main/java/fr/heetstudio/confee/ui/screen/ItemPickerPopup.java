package fr.heetstudio.confee.ui.screen;

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
 * Grid of item / block icons with a search field (ID or translated name). Click, or arrows + Enter, to choose;
 * Escape closes. Hover shows the name and the ID.
 * <p>Internal: may change without notice.</p>
 */
final class ItemPickerPopup implements Overlay {

    private static final int PAD = 8;
    private static final int CELL = 20;

    private record Entry(String id, String name, String initials) {
    }

    private final String registry;

    private final Ui ui;
    private final Component title;
    private final Consumer<String> onPick;
    private final Runnable onClose;
    private final TextFieldWidget search;
    private final List<Entry> all = new ArrayList<>();
    private List<Entry> shown;
    private int selected;
    private int scrollRow;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int columns;
    private int visibleRows;

    ItemPickerPopup(Ui ui, Component title, String registry, String current, Consumer<String> onPick, Runnable onClose) {
        this(ui, title, registry, current, java.util.Set.of(), onPick, onClose);
    }

    /** @param hidden IDs not offered (already in the list being edited) */
    ItemPickerPopup(Ui ui, Component title, String registry, String current, java.util.Set<String> hidden,
                    Consumer<String> onPick, Runnable onClose) {
        this.ui = ui;
        this.title = title;
        this.onPick = onPick;
        this.onClose = onClose;
        this.registry = registry;
        for (String id : fr.heetstudio.confee.ui.RegistryEntries.ids(registry)) {
            if (hidden.contains(id) && !id.equals(current)) {
                continue;
            }
            Component name = fr.heetstudio.confee.ui.RegistryIcons.name(registry, id);
            String path = id.substring(id.indexOf(':') + 1);
            all.add(new Entry(id, name == null ? path : name.getString(),
                    path.substring(0, Math.min(2, path.length())).toUpperCase(Locale.ROOT)));
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

    private void layout(int screenW, int screenH) {
        pw = Math.min(screenW - 20, 9 * CELL * 2 + 2 * PAD);
        ph = Math.min(screenH - 20, 260);
        px = (screenW - pw) / 2;
        py = Math.max(4, (screenH - ph) / 2);
        columns = Math.max(1, (pw - 2 * PAD - 6) / CELL);
        visibleRows = Math.max(1, (ph - (PAD + 9 + 6 + TextFieldWidget.HEIGHT + 6) - PAD) / CELL);
    }

    private int gridX() {
        return px + PAD + (pw - 2 * PAD - 6 - columns * CELL) / 2;
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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        layout(screenW, screenH);
        scrollRow = Math.max(0, Math.min(Math.max(0, rows() - visibleRows), scrollRow));
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        Draw.rect(g, 0, 0, screenW, screenH, 0xB0000000);
        Draw.rect(g, px + 2, py + 2, pw, ph, 0x80000000);
        Draw.rect(g, px, py, pw, ph, t.panelBg());
        Draw.outline(g, px, py, pw, ph, t.outline());
        Draw.textFit(g, ui.font(), title, px + PAD, py + PAD, pw - 2 * PAD - 60, t.textOnDark());
        Component count = Component.translatable("confee.picker.count", shown.size());
        Draw.text(g, ui.font(), count, px + pw - PAD - ui.font().width(count), py + PAD, ui.secondaryText());
        search.setBounds(px + PAD, py + PAD + 15, pw - 2 * PAD, TextFieldWidget.HEIGHT);
        search.render(g, mouseX, mouseY, delta);

        int gx = gridX();
        int gy = gridY();
        Draw.rect(g, px + PAD, gy - 2, pw - 2 * PAD, visibleRows * CELL + 4, t.panelBgDeep());
        if (shown.isEmpty()) {
            Draw.textCentered(g, ui.font(), Component.translatable("confee.picker.none").getString(), px + pw / 2, gy + 10, ui.secondaryText());
        }
        for (int r = 0; r < visibleRows; r++) {
            for (int c = 0; c < columns; c++) {
                int index = (scrollRow + r) * columns + c;
                if (index >= shown.size()) {
                    break;
                }
                int cx = gx + c * CELL;
                int cy = gy + r * CELL;
                boolean hover = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
                if (index == selected) {
                    Draw.rect(g, cx, cy, CELL, CELL, t.accent());
                } else if (hover) {
                    Draw.rect(g, cx, cy, CELL, CELL, Draw.mix(t.panelBgDeep(), t.textOnDark(), 0.15f));
                }
                Entry entry = shown.get(index);
                if (!fr.heetstudio.confee.ui.RegistryIcons.draw(g, registry, entry.id(), cx + 2, cy + 2, 16)) {
                    // no icon (an entity without spawn egg...): its first letters
                    Draw.textCentered(g, ui.font(), entry.initials(), cx + CELL / 2, cy + 6, ui.secondaryText());
                }
            }
        }
        // scrollbar
        if (rows() > visibleRows) {
            int trackX = px + pw - PAD - 4;
            int trackH = visibleRows * CELL;
            int handleH = Math.max(10, trackH * visibleRows / rows());
            int handleY = gy + (trackH - handleH) * scrollRow / Math.max(1, rows() - visibleRows);
            Draw.rect(g, trackX, gy, 3, trackH, Draw.mix(t.panelBgDeep(), t.textOnDark(), 0.08f));
            Draw.rect(g, trackX, handleY, 3, handleH, t.buttonFace());
        }
        g.pose().popPose();
    }

    @Nullable
    private Integer indexAt(double mx, double my) {
        int gx = gridX();
        int gy = gridY();
        if (mx < gx || my < gy || mx >= gx + columns * CELL || my >= gy + visibleRows * CELL) {
            return null;
        }
        int index = (scrollRow + (int) ((my - gy) / CELL)) * columns + (int) ((mx - gx) / CELL);
        return index < shown.size() ? index : null;
    }

    private void pick(int index) {
        ui.click();
        onPick.accept(shown.get(index).id());
        onClose.run();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
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
        scrollRow = Math.max(0, Math.min(Math.max(0, rows() - visibleRows), scrollRow - (int) Math.signum(delta) * 2));
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
            case GLFW.GLFW_KEY_RIGHT -> move(1);
            case GLFW.GLFW_KEY_LEFT -> move(-1);
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
        Integer index = indexAt(mx, my);
        if (index == null) {
            return null;
        }
        Entry e = shown.get(index);
        return Component.literal(e.name()).append(Component.literal("\n" + e.id()).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}

package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.widget.DropdownWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Popup list of a dropdown (spec 10.3): over the screen, scrollable when long, current value highlighted with the
 * accent. Escape or a click outside closes it. ↑/↓ move, Enter selects. Opens with a short slide + fade (90 ms).
 * <p>Internal: may change without notice.</p>
 */
final class DropdownPopup implements Overlay {

    private static final int ITEM_H = 16;
    private static final int MAX_VISIBLE = 8;
    private static final long OPEN_MS = 90;

    private final Ui ui;
    private final DropdownWidget<?> dropdown;
    private final Runnable close;
    private final int screenHeight;
    private int x;
    private int y;
    private int w;
    private int h;
    private boolean above;
    private final long openedAt;
    private int highlighted;
    private int scroll;

    DropdownPopup(Ui ui, DropdownWidget<?> dropdown, int screenHeight, Runnable close) {
        this.ui = ui;
        this.dropdown = dropdown;
        this.close = close;
        this.screenHeight = screenHeight;
        int count = dropdown.values().size();
        int visible = Math.min(count, MAX_VISIBLE);
        this.highlighted = dropdown.currentIndex();
        this.scroll = Math.max(0, Math.min(highlighted - visible / 2, count - visible));
        this.openedAt = ui.now();
        dropdown.setOpen(true);
        place();
    }

    /** Follows the dropdown, which may move (scrolled into view after keyboard focus). */
    private void place() {
        w = dropdown.width;
        h = visibleCount() * ITEM_H + 2;
        x = dropdown.x;
        int below = dropdown.y + dropdown.height + 1;
        above = below + h > screenHeight - 4 && dropdown.y - h - 1 >= 4;
        y = above ? dropdown.y - h - 1 : Math.min(below, Math.max(4, screenHeight - 4 - h));
    }

    private int visibleCount() {
        return Math.min(dropdown.values().size(), MAX_VISIBLE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        place();
        Theme t = ui.theme();
        float p = Draw.easeOut((ui.now() - openedAt) / (float) OPEN_MS);
        int slide = Math.round((1 - p) * 4) * (above ? 1 : -1);
        g.pose().pushPose();
        g.pose().translate(0, slide, 400);
        int bg = Draw.withAlpha(t.panelBg(), p);
        Draw.rect(g, x, y, w, h, bg);
        Draw.outline(g, x, y, w, h, Draw.withAlpha(t.outline(), p));
        int current = dropdown.currentIndex();
        int count = dropdown.values().size();
        for (int i = 0; i < visibleCount(); i++) {
            int index = scroll + i;
            int iy = y + 1 + i * ITEM_H;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= iy && mouseY < iy + ITEM_H;
            if (hover) {
                highlighted = index;
            }
            int text = t.textOnDark();
            if (index == current) {
                Draw.rect(g, x + 1, iy, w - 2, ITEM_H, Draw.withAlpha(t.accent(), p));
                text = t.onAccentText();
            } else if (index == highlighted) {
                Draw.rect(g, x + 1, iy, w - 2, ITEM_H, Draw.withAlpha(Draw.mix(t.panelBg(), t.textOnDark(), 0.12f), p));
            }
            if (index == highlighted && index != current) {
                Draw.rect(g, x + 1, iy, 2, ITEM_H, Draw.withAlpha(t.accent(), p));
            }
            Draw.textFit(g, ui.font(), nameAt(index), x + 7, iy + 4,
                    w - 14 - (count > MAX_VISIBLE ? 4 : 0), Draw.withAlpha(text, Math.max(p, 0.05f)));
        }
        if (count > MAX_VISIBLE) {
            int trackH = h - 2;
            int handleH = Math.max(8, trackH * MAX_VISIBLE / count);
            int handleY = y + 1 + (trackH - handleH) * scroll / (count - MAX_VISIBLE);
            Draw.rect(g, x + w - 4, y + 1, 3, trackH, t.panelBgDeep());
            Draw.rect(g, x + w - 4, handleY, 3, handleH, t.buttonFace());
        }
        g.pose().popPose();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Component nameAt(int index) {
        return ((DropdownWidget) dropdown).nameOf(dropdown.values().get(index));
    }

    private boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!inside(mx, my)) {
            // a click outside (including on the dropdown itself) only closes the list
            close.run();
            return true;
        }
        int index = scroll + (int) ((my - y - 1) / ITEM_H);
        if (index >= 0 && index < dropdown.values().size()) {
            ui.click();
            dropdown.chooseIndex(index);
            close.run();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = dropdown.values().size() - visibleCount();
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        int count = dropdown.values().size();
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> close.run();
            case GLFW.GLFW_KEY_UP -> move(-1, count);
            case GLFW.GLFW_KEY_DOWN -> move(1, count);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                ui.click();
                dropdown.chooseIndex(highlighted);
                close.run();
            }
            default -> {
                // other keys are swallowed while the list is open
            }
        }
        return true;
    }

    private void move(int direction, int count) {
        highlighted = Math.max(0, Math.min(count - 1, highlighted + direction));
        if (highlighted < scroll) {
            scroll = highlighted;
        } else if (highlighted >= scroll + visibleCount()) {
            scroll = highlighted - visibleCount() + 1;
        }
    }

    @Override
    public Component narration() {
        return Component.translatable("confee.narration.choice_item", nameAt(highlighted), highlighted + 1,
                dropdown.values().size());
    }

    @Override
    public void closed() {
        dropdown.setOpen(false);
    }
}

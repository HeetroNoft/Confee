package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import fr.heetstudio.confee.ui.widget.ButtonWidget;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import fr.heetstudio.confee.ui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One block of the scrolling panel of the config screen.
 * <p>Internal: may change without notice.</p>
 */
abstract class Row {

    protected final Ui ui;
    protected int x;
    protected int y;
    protected int width;
    protected int height;

    Row(Ui ui) {
        this.ui = ui;
    }

    /** Places the row (and its widgets) and returns its height. */
    abstract int layout(int x, int y, int width);

    abstract void render(GuiGraphics g, int mouseX, int mouseY, float delta);

    List<ConfeeWidget> widgets() {
        return List.of();
    }

    @Nullable
    ConfeeScreen.TooltipTarget tooltip(double mx, double my) {
        return null;
    }

    /** Hidden by a condition: takes no room and has no visible widget. */
    boolean hidden() {
        return false;
    }

    /** Whether one of its fields shows an error (blocks saving). */
    boolean hasError() {
        return false;
    }

    /** Called every frame before layout: refresh texts from the edited values. */
    void sync() {
    }

    // ------------------------------------------------------------------ simple rows

    /** Category title (search results) or breadcrumb of a sub-section, with an optional back button. */
    static final class Header extends Row {
        private final Component text;
        @Nullable
        private final ButtonWidget back;

        Header(Ui ui, Component text, @Nullable Runnable onBack) {
            super(ui);
            this.text = text;
            this.back = onBack == null ? null : new BackButton(ui, text, onBack);
        }

        @Override
        int layout(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
            if (back != null) {
                back.setBounds(x, y, width, 20);
                return height = 20;
            }
            return height = 12;
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
            if (back != null) {
                back.render(g, mouseX, mouseY, delta);
                return;
            }
            Theme t = ui.theme();
            Draw.textFit(g, ui.font(), text, x, y + 1, width, ui.secondaryText());
            Draw.rect(g, x, y + 11, width, 1, Draw.mix(t.panelBg(), t.textOnDark(), 0.12f));
        }

        @Override
        List<ConfeeWidget> widgets() {
            return back == null ? List.of() : List.of(back);
        }
    }

    /** "‹ Section" row that goes back up one level. */
    private static final class BackButton extends ButtonWidget {
        BackButton(Ui ui, Component label, Runnable onPress) {
            super(ui, label, Style.NORMAL, onPress);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
            Theme t = ui.theme();
            boolean hover = hovered(mouseX, mouseY);
            if (hover || focused) {
                Draw.rect(g, x, y, width, height, Draw.mix(t.panelBg(), t.textOnDark(), 0.08f));
            }
            Icon.BACK.draw(g, x + 3, y + (height - 7) / 2, t.textOnDark());
            Draw.textFit(g, ui.font(), label(), x + 14, y + (height - 8) / 2, width - 16, t.textOnDark());
            Draw.rect(g, x, y + height - 1, width, 1, Draw.mix(t.panelBg(), t.textOnDark(), 0.12f));
            if (focused) {
                Draw.focusRing(g, x, y, width, height, t.accent());
            }
        }
    }

    /** Informative text (empty file, no search result). */
    static final class Message extends Row {
        private final Component text;
        private List<FormattedCharSequence> lines = List.of();

        Message(Ui ui, Component text) {
            super(ui);
            this.text = text;
        }

        @Override
        int layout(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
            lines = ui.font().split(text, width);
            return height = lines.size() * 10 + 8;
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
            int ly = y + 4;
            for (FormattedCharSequence line : lines) {
                Draw.text(g, ui.font(), line, x, ly, ui.secondaryText());
                ly += 10;
            }
        }
    }

    /** Information banner (spec 8.3) with an action button on the right. */
    static final class Banner extends Row {
        private final Component text;
        @Nullable
        private final ButtonWidget button;
        private List<FormattedCharSequence> lines = List.of();

        Banner(Ui ui, Component text, @Nullable ButtonWidget button) {
            super(ui);
            this.text = text;
            this.button = button;
        }

        @Override
        int layout(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
            int bw = button == null ? 0 : Math.min(button.preferredWidth(), width / 3);
            lines = ui.font().split(text, width - bw - 30);
            int h = Math.max(lines.size() * 10 + 10, button == null ? 20 : 28);
            if (button != null) {
                button.setBounds(x + width - bw - 5, y + (h - 20) / 2, bw, 20);
            }
            return height = h;
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
            Theme t = ui.theme();
            Draw.rect(g, x, y, width, height, t.panelBgDeep());
            Draw.outline(g, x, y, width, height, Draw.mix(t.warning(), t.panelBgDeep(), 0.45f));
            Draw.rect(g, x + 5, y + (height - 9) / 2, 9, 9, t.warning());
            Icon.EXCLAMATION.drawCentered(g, x + 5, y + (height - 9) / 2, 9, 9, t.outline());
            int ly = y + (height - lines.size() * 10) / 2 + 1;
            for (FormattedCharSequence line : lines) {
                Draw.text(g, ui.font(), line, x + 19, ly, t.textOnDark());
                ly += 10;
            }
            if (button != null) {
                button.render(g, mouseX, mouseY, delta);
            }
        }

        @Override
        List<ConfeeWidget> widgets() {
            return button == null ? List.of() : List.of(button);
        }
    }

    /** Full width row holding one widget (navigation row to a sub-section). */
    static final class Single extends Row {
        private final ButtonWidget widget;

        Single(Ui ui, ButtonWidget widget) {
            super(ui);
            this.widget = widget;
        }

        @Override
        int layout(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
            widget.setBounds(x, y, width, 20);
            return height = 20;
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
            widget.render(g, mouseX, mouseY, delta);
        }

        @Override
        List<ConfeeWidget> widgets() {
            return List.of(widget);
        }
    }

    /**
     * Element of a list of objects / lists (spec 8.2 bis): navigation button to its sub-screen, then move up, move
     * down and remove.
     */
    static final class ListElement extends Row {
        private static final int ICON = 16;
        private final ButtonWidget open;
        private final IconButton up;
        private final IconButton down;
        private final IconButton remove;

        ListElement(Ui ui, ButtonWidget open, boolean first, boolean last, Runnable onUp, Runnable onDown, Runnable onRemove) {
            super(ui);
            Theme t = ui.theme();
            this.open = open;
            this.up = new IconButton(ui, Icon.ARROW_UP, Component.translatable("confee.tooltip.move_up"), ICON, t::textOnDark, onUp);
            this.down = new IconButton(ui, Icon.ARROW_DOWN, Component.translatable("confee.tooltip.move_down"), ICON, t::textOnDark, onDown);
            this.remove = new IconButton(ui, Icon.CROSS, Component.translatable("confee.tooltip.remove"), ICON, t::textOnDark, onRemove);
            up.setEnabled(!first);
            down.setEnabled(!last);
        }

        @Override
        int layout(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
            int icons = 3 * (ICON + 2);
            open.setBounds(x, y, width - icons - 2, 20);
            int ix = x + width - icons;
            up.setBounds(ix, y + 2, ICON, ICON);
            down.setBounds(ix + ICON + 2, y + 2, ICON, ICON);
            remove.setBounds(ix + 2 * (ICON + 2), y + 2, ICON, ICON);
            return height = 20;
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
            open.render(g, mouseX, mouseY, delta);
            up.render(g, mouseX, mouseY, delta);
            down.render(g, mouseX, mouseY, delta);
            remove.render(g, mouseX, mouseY, delta);
        }

        @Override
        List<ConfeeWidget> widgets() {
            return List.of(open, up, down, remove);
        }
    }
}

package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.ui.Labels;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import fr.heetstudio.confee.ui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * File selection (spec 10.4 A), when a mod has several config files: one large button per file with its readable
 * name, the file name in gray and a source badge ("Confee", "Forge", "File").
 * <p>Internal: may change without notice.</p>
 */
public final class FileSelectScreen extends ConfeeScreen {

    private static final int TITLE_H = 22;
    private static final int ITEM_H = 34;
    private static final int GAP = 4;
    private static final int MAX_W = 400;

    /**
     * One file of the list.
     *
     * @param name     readable name ("Client", "Main"...)
     * @param fileName path relative to config/
     * @param badge    source badge
     * @param open     creates the screen of this file, with the given parent
     */
    public record Entry(Component name, String fileName, Component badge, Function<Screen, Screen> open,
                        @org.jetbrains.annotations.Nullable Component disabledReason) {

        public Entry(Component name, String fileName, Component badge, Function<Screen, Screen> open) {
            this(name, fileName, badge, open, null);
        }
    }

    private final List<Entry> entries;
    private final Theme theme;
    private IconButton back;
    private final List<FileButton> buttons = new ArrayList<>();
    private int[] viewport = new int[4];
    private double scroll;

    /**
     * @param theme theme of the mod's first Confee config (spec 11), or the default one
     */
    public FileSelectScreen(Screen parent, String modId, Theme theme, List<Entry> entries) {
        super(Labels.modName(modId), parent);
        this.entries = List.copyOf(entries);
        this.theme = theme;
    }

    @Override
    protected Theme theme() {
        return theme;
    }

    @Override
    protected void build() {
        back = new IconButton(ui, Icon.BACK, Component.translatable("confee.nav.back"), 16, ui.theme()::titleBarText, this::onClose);
        buttons.clear();
        for (Entry entry : entries) {
            FileButton button = new FileButton(ui, entry, () -> minecraft.setScreen(entry.open().apply(this)));
            button.setEnabled(entry.disabledReason() == null);
            buttons.add(button);
        }
        int w = Math.min(MAX_W, width - 16);
        viewport = new int[]{(width - w) / 2, TITLE_H, (width + w) / 2, height};
        if (focus == null) {
            buttons.stream().filter(ConfeeWidget::isFocusable).findFirst().ifPresent(this::setFocus);
        }
    }

    @Override
    protected List<ConfeeWidget> widgets() {
        List<ConfeeWidget> list = new ArrayList<>();
        list.add(back);
        list.addAll(buttons);
        return list;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        Draw.rect(g, 0, 0, width, height, t.panelBgDeep());
        int x = viewport[0];
        int w = viewport[2] - viewport[0];
        Draw.rect(g, x, TITLE_H, w, height - TITLE_H, t.panelBg());
        int contentH = buttons.size() * (ITEM_H + GAP) + 12;
        scroll = Math.max(0, Math.min(Math.max(0, contentH - (height - TITLE_H)), scroll));
        int y = TITLE_H + 8 - (int) scroll;
        g.enableScissor(viewport[0], viewport[1], viewport[2], viewport[3]);
        for (FileButton button : buttons) {
            button.setBounds(x + 8, y, w - 16, ITEM_H);
            button.setClip(viewport);
            button.render(g, mouseX, mouseY, delta);
            y += ITEM_H + GAP;
        }
        g.disableScissor();

        Draw.rect(g, 0, 0, width, TITLE_H - 2, t.titleBarBg());
        Draw.rect(g, 0, TITLE_H - 2, width, 1, t.toggleOffTrack());
        Draw.rect(g, 0, TITLE_H - 1, width, 1, t.outline());
        back.setBounds(3, 2, 16, 16);
        back.render(g, mouseX, mouseY, delta);
        Draw.textFit(g, font, title, 23, 6, width - 30, t.titleBarText());
    }

    @Override
    protected boolean scrolled(double mx, double my, double delta) {
        scroll -= delta * 24;
        return true;
    }

    @Override
    protected boolean shortcut(int key, int modifiers, boolean typing) {
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            int index = buttons.indexOf(focus);
            int next = Math.max(0, Math.min(buttons.size() - 1, index + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
            if (!buttons.isEmpty()) {
                setFocus(buttons.get(next));
            }
            return true;
        }
        return false;
    }

    /** Large button: readable name, file name in gray, source badge on the right. */
    private static final class FileButton extends ConfeeWidget {
        private final Entry entry;
        private final Runnable onPress;
        private boolean mouseDown;

        FileButton(Ui ui, Entry entry, Runnable onPress) {
            super(ui, 100, ITEM_H);
            this.entry = entry;
            this.onPress = onPress;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
            Theme t = ui.theme();
            boolean hover = hovered(mouseX, mouseY);
            boolean pressed = mouseDown && hover;
            int face = !enabled ? Draw.mix(t.buttonFace(), t.panelBg(), 0.55f) : hover ? t.buttonFaceHover() : t.buttonFace();
            int text = enabled ? t.textOnLight() : t.textDisabled();
            Draw.bevel(g, x, y, width, height, face, enabled ? t.buttonShadow() : Draw.mix(t.buttonShadow(), t.panelBg(), 0.55f),
                    t.outline(), pressed);
            int off = pressed ? 1 : 0;
            int badgeW = ui.font().width(entry.badge()) + 8;
            int textW = width - 20 - badgeW - 8;
            Draw.textFit(g, ui.font(), entry.name(), x + 8, y + 7 + off, textW, text);
            Component second = entry.disabledReason() == null ? Component.literal(entry.fileName())
                    : Component.translatable("confee.file.disabled", entry.fileName(), entry.disabledReason());
            Draw.textFit(g, ui.font(), second, x + 8, y + 19 + off, textW, enabled ? t.buttonShadow() : t.textDisabled());
            int bx = x + width - 20 - badgeW;
            int by = y + (height - 2 - 12) / 2 + off;
            Draw.rect(g, bx, by, badgeW, 12, enabled ? t.accent() : Draw.mix(t.accent(), t.panelBg(), 0.6f));
            Draw.text(g, ui.font(), entry.badge(), bx + 4, by + 2, t.onAccentText());
            if (enabled) {
                Icon.CHEVRON_RIGHT.draw(g, x + width - 11, y + (height - 2 - 7) / 2 + off, t.textOnLight());
            }
            if (focused) {
                Draw.focusRing(g, x, y, width, height, t.accent());
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && enabled && contains(mx, my)) {
                mouseDown = true;
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            boolean was = mouseDown;
            mouseDown = false;
            if (was && contains(mx, my)) {
                ui.click();
                onPress.run();
            }
            return was;
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
                ui.click();
                onPress.run();
                return true;
            }
            return false;
        }

        @Override
        public Component tooltip(double mouseX, double mouseY) {
            return entry.disabledReason();
        }

        @Override
        public Component narration() {
            return Component.translatable("confee.narration.file", entry.name(), entry.fileName(), entry.badge());
        }
    }
}

package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.external.ExternalConfig;
import fr.heetstudio.confee.ui.Labels;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import fr.heetstudio.confee.ui.widget.ButtonWidget;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import fr.heetstudio.confee.ui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Spec 10.4 D: "Confee cannot read this file (reason)" with buttons to open the file or its folder in the system's
 * applications.
 * <p>Internal: may change without notice.</p>
 */
public final class UnreadableFileScreen extends ConfeeScreen {

    private static final int TITLE_H = 22;

    private final ExternalConfig config;
    private final Component message;
    private IconButton back;
    private ButtonWidget openFile;
    private ButtonWidget openFolder;

    public UnreadableFileScreen(Screen parent, ExternalConfig config) {
        super(Component.translatable("confee.screen.title", Labels.modName(config.modId()), config.relativePath()), parent);
        this.config = config;
        this.message = config.status() == ExternalConfig.Status.UNSUPPORTED_FORMAT
                ? Component.translatable("confee.unreadable.unsupported", config.problem())
                : Component.translatable("confee.unreadable.broken", config.problem());
    }

    @Override
    protected Theme theme() {
        return Theme.DEFAULT;
    }

    @Override
    protected void build() {
        back = new IconButton(ui, Icon.BACK, Component.translatable("confee.nav.back"), 16, ui.theme()::titleBarText, this::onClose);
        openFile = new ButtonWidget(ui, Component.translatable("confee.button.open_file"), ButtonWidget.Style.PRIMARY,
                () -> com.mojang.blaze3d.Blaze3D.openPath(config.file()));
        openFolder = new ButtonWidget(ui, Component.translatable("confee.button.open_folder"), ButtonWidget.Style.NORMAL,
                () -> com.mojang.blaze3d.Blaze3D.openPath(config.file().getParent()));
        if (focus == null) {
            setFocus(openFile);
        }
    }

    @Override
    protected List<ConfeeWidget> widgets() {
        return List.of(back, openFile, openFolder);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        Draw.rect(g, 0, 0, width, height, t.panelBgDeep());
        int w = Math.min(320, width - 16);
        int x = (width - w) / 2;
        List<FormattedCharSequence> lines = font.split(message, w - 20);
        int h = 10 + 9 + 8 + lines.size() * 10 + 10 + 20 + 10;
        int y = Math.max(TITLE_H + 8, (height - h) / 2);
        Draw.rect(g, x, y, w, h, t.panelBg());
        Draw.outline(g, x, y, w, h, t.outline());
        Draw.textFit(g, font, Component.literal(config.relativePath()), x + 10, y + 10, w - 20, t.textOnDark());
        int ly = y + 27;
        for (FormattedCharSequence line : lines) {
            Draw.text(g, font, line, x + 10, ly, Draw.mix(t.textOnDark(), t.panelBg(), 0.2f));
            ly += 10;
        }
        int by = y + h - 30;
        int fw = Math.max(openFile.preferredWidth(), 80);
        int dw = Math.max(openFolder.preferredWidth(), 80);
        openFile.setBounds(x + w - 10 - fw, by, fw, 20);
        openFolder.setBounds(openFile.x - 4 - dw, by, dw, 20);
        openFolder.render(g, mouseX, mouseY, delta);
        openFile.render(g, mouseX, mouseY, delta);

        Draw.rect(g, 0, 0, width, TITLE_H - 2, t.titleBarBg());
        Draw.rect(g, 0, TITLE_H - 2, width, 1, t.toggleOffTrack());
        Draw.rect(g, 0, TITLE_H - 1, width, 1, t.outline());
        back.setBounds(3, 2, 16, 16);
        back.render(g, mouseX, mouseY, delta);
        Draw.textFit(g, font, title, 23, 6, width - 30, t.titleBarText());
    }
}

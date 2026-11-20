package fr.heetstudio.confee.ui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.api.Decoration;
import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.core.Conditions;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.ui.EditSession;
import fr.heetstudio.confee.ui.ItemIcons;
import fr.heetstudio.confee.ui.Labels;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import fr.heetstudio.confee.ui.widget.ButtonWidget;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A {@link Decoration} of the mod (title, separator, text, image, button) in the option list, and the layout banner.
 * <p>Internal: may change without notice.</p>
 */
final class DecorationRow extends Row {

    private final Decoration decoration;
    @Nullable
    private final EditSession session;
    @Nullable
    private final Component text;
    @Nullable
    private final ButtonWidget button;
    private List<FormattedCharSequence> lines = List.of();
    private boolean hiddenByCondition;

    DecorationRow(Ui ui, Decoration decoration, @Nullable EditSession session) {
        super(ui);
        this.decoration = decoration;
        this.session = session;
        this.text = Labels.text(decoration.text());
        if (decoration.kind() == Decoration.Kind.BUTTON) {
            Runnable action = decoration.action();
            button = new ButtonWidget(ui, text, ButtonWidget.Style.NORMAL, () -> {
                try {
                    action.run();
                } catch (RuntimeException | LinkageError e) {
                    ConfeeLog.error("Button '" + decoration.text() + "' failed", e instanceof RuntimeException re ? re : new RuntimeException(e));
                }
            });
            button.setIcon(decoration.icon());
        } else {
            button = null;
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    void sync() {
        if (session != null && decoration.visibleIf() != null) {
            hiddenByCondition = !Conditions.test(decoration.visibleIf(), session.handle().schema().root(),
                    o -> session.get((Option) o));
            if (button != null) {
                button.setVisible(!hiddenByCondition);
            }
        }
    }

    @Override
    boolean hidden() {
        return hiddenByCondition;
    }

    private int iconW() {
        return decoration.icon() != null ? 20 : 0;
    }

    @Override
    int layout(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        return height = switch (decoration.kind()) {
            case HEADER -> {
                lines = ui.font().split(text, width - iconW());
                yield Math.max(lines.size() * 10, iconW() > 0 ? 16 : 0) + 6;
            }
            case SEPARATOR -> 5;
            case TEXT -> {
                boolean framed = decoration.style() != Decoration.Style.PLAIN;
                int inset = framed ? 19 + 5 : iconW();
                lines = ui.font().split(text, width - inset - (framed ? 6 : 0));
                yield lines.size() * 10 + (framed ? 10 : 0);
            }
            case IMAGE -> imageHeight(width);
            case BUTTON -> {
                button.setBounds(x, y, Math.min(width, Math.max(button.preferredWidth(), 80)), 20);
                yield 20;
            }
        };
    }

    private int imageHeight(int width) {
        int[] size = Textures.size(decoration.image());
        if (size[0] <= 0) {
            return 10;
        }
        int h = decoration.height();
        int w = Math.round(size[0] * (h / (float) size[1]));
        return w > width ? Math.max(1, Math.round(size[1] * (width / (float) size[0]))) : h;
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        switch (decoration.kind()) {
            case HEADER -> {
                int tx = x;
                if (iconW() > 0) {
                    ItemIcons.drawIcon(g, decoration.icon(), x, y, 16);
                    tx += iconW();
                }
                int ly = y + (iconW() > 0 && lines.size() == 1 ? 4 : 0);
                for (FormattedCharSequence line : lines) {
                    Draw.text(g, ui.font(), line, tx, ly, t.textOnDark());
                    ly += 10;
                }
                Draw.rect(g, x, y + height - 3, Math.min(width, 24), 2, t.accent());
                Draw.rect(g, x + Math.min(width, 24), y + height - 2, width - Math.min(width, 24), 1,
                        Draw.mix(t.panelBg(), t.textOnDark(), 0.12f));
            }
            case SEPARATOR -> Draw.rect(g, x, y + 2, width, 1, Draw.mix(t.panelBg(), t.textOnDark(), 0.15f));
            case TEXT -> renderText(g, t);
            case IMAGE -> renderImage(g);
            case BUTTON -> button.render(g, mouseX, mouseY, delta);
        }
    }

    private void renderText(GuiGraphics g, Theme t) {
        boolean framed = decoration.style() != Decoration.Style.PLAIN;
        int tx = x;
        int ly = y;
        if (framed) {
            boolean warning = decoration.style() == Decoration.Style.WARNING;
            int frame = warning ? Draw.mix(t.warning(), t.panelBgDeep(), 0.45f) : Draw.mix(t.textOnDark(), t.panelBgDeep(), 0.7f);
            Draw.rect(g, x, y, width, height, t.panelBgDeep());
            Draw.outline(g, x, y, width, height, frame);
            int iy = y + (height - 9) / 2;
            if (decoration.icon() != null) {
                ItemIcons.drawIcon(g, decoration.icon(), x + 3, y + (height - 16) / 2, 16);
            } else if (warning) {
                Draw.rect(g, x + 5, iy, 9, 9, t.warning());
                Icon.EXCLAMATION.drawCentered(g, x + 5, iy, 9, 9, t.outline());
            } else {
                Draw.rect(g, x + 5, iy, 9, 9, t.accent());
                Icon.INFO.drawCentered(g, x + 5, iy, 9, 9, t.onAccentText());
            }
            tx = x + 24;
            ly = y + 5;
        } else if (decoration.icon() != null) {
            ItemIcons.drawIcon(g, decoration.icon(), x, y, 16);
            tx += iconW();
        }
        int color = framed ? t.textOnDark() : ui.secondaryText();
        for (FormattedCharSequence line : lines) {
            Draw.text(g, ui.font(), line, tx, ly, color);
            ly += 10;
        }
    }

    private void renderImage(GuiGraphics g) {
        int[] size = Textures.size(decoration.image());
        ResourceLocation id = ResourceLocation.tryParse(decoration.image());
        if (size[0] <= 0 || id == null) {
            Draw.textFit(g, ui.font(), Component.translatable("confee.preview.missing_image", decoration.image()), x, y,
                    width, ui.theme().error());
            return;
        }
        int h = height;
        int w = Math.min(width, Math.round(size[0] * (h / (float) size[1])));
        RenderSystem.enableBlend();
        g.blit(RenderType::guiTextured, id, x + (width - w) / 2, y, 0, 0, w, h, size[0], size[1], size[0], size[1]);
        RenderSystem.disableBlend();
    }

    @Override
    List<ConfeeWidget> widgets() {
        return button == null ? List.of() : List.of(button);
    }
}

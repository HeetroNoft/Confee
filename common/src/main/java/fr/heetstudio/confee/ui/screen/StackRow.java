package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.ui.EditSession;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A {@code String} option with {@code @ItemStack}: item, count and extra data fields under the name.
 * <p>Internal: may change without notice.</p>
 */
final class StackRow extends OptionRow {

    private final Ui ui;
    private final BiConsumer<String, Consumer<String>> picker;
    private StackEditor stack;
    /** Value the fields were built from. */
    private Object shown;

    StackRow(Ui ui, EditSession session, Option<?> option, Component name, @Nullable Component description,
             BiConsumer<String, Consumer<String>> picker) {
        super(ui, session, option, name, description, null);
        this.ui = ui;
        this.picker = picker;
        build();
    }

    private void build() {
        shown = session.get(option);
        stack = new StackEditor(ui, option, name, String.valueOf(shown), picker, this::edited);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void edited() {
        String snbt = stack.check();
        if (snbt != null) {
            ((EditSession) session).set((Option) option, snbt);
            shown = snbt;
        }
    }

    @Override
    void syncValue() {
        // value changed elsewhere (reset): new fields
        boolean editing = stack.widgets().stream().anyMatch(ConfeeWidget::isFocused);
        if (!editing && !Objects.equals(shown, session.get(option)) && stack.error() == null) {
            build();
        }
    }

    @Override
    int layout(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        layoutSubtitle(width);
        reset.setBounds(x + width - ICON, y - 1, ICON, ICON);
        int h = headerHeight();
        h += stack.layout(x + DOT_GUTTER, y + h, width - DOT_GUTTER);
        return height = h;
    }

    @Override
    void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!session.isDefault(option) && !option.readOnly()) {
            Draw.rect(g, x, y + 3, 3, 3, ui.theme().accent());
        }
        Draw.textFit(g, ui.font(), name, x + DOT_GUTTER, y + 1, width - DOT_GUTTER - 2 * ICON - 6, labelColor());
        renderSubtitle(g);
        renderRestartIcon(g);
        reset.render(g, mouseX, mouseY, delta);
        List<ConfeeWidget> fields = stack.widgets();
        for (ConfeeWidget field : fields) {
            field.render(g, mouseX, mouseY, delta);
        }
        if (stack.error() != null) {
            ConfeeWidget last = fields.get(fields.size() - 1);
            Draw.textFit(g, ui.font(), stack.error(), x + DOT_GUTTER, last.y + last.height + 2, width - DOT_GUTTER, ui.theme().error());
        }
    }

    @Override
    List<ConfeeWidget> widgets() {
        List<ConfeeWidget> list = new ArrayList<>(stack.widgets());
        list.add(reset);
        return list;
    }

    @Override
    boolean hasError() {
        return !hiddenByCondition && stack.error() != null;
    }
}

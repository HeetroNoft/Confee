package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.core.ObjectField;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.ui.EditSession;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.ValueText;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import fr.heetstudio.confee.ui.widget.ButtonWidget;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import fr.heetstudio.confee.ui.widget.DropdownWidget;
import fr.heetstudio.confee.ui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Editor of a list of objects: one card per element (its fields, ▲ ▼ ✕), "+ Add" creates an element with the
 * default field values. Applied only when every field is valid.
 * <p>Internal: may change without notice.</p>
 */
final class ObjectListRow extends OptionRow {

    private static final int PAD = 4;
    private static final int GAP = 3;
    private static final int HEADER_H = 14;
    private static final int FIELD_H = 18;

    private final Function<ValueText.Problem, Component> problemText;
    private final Consumer<ConfeeWidget> focusRequest;
    private final DropdownWidget.Opener opener;
    private final List<Card> cards = new ArrayList<>();
    private final ButtonWidget add;
    @Nullable
    private List<?> shownList;

    private final class Card {
        final Map<String, Object> values = new LinkedHashMap<>();
        final List<ScalarEditor> editors = new ArrayList<>();
        final List<Component> labels = new ArrayList<>();
        final IconButton up;
        final IconButton down;
        final IconButton remove;
        int top;
        int bottom;

        Card(Map<?, ?> element) {
            for (ObjectField field : option.objectFields()) {
                Object value = element.get(field.key());
                values.put(field.key(), value == null ? field.defaultValue() : value);
                Component label = Component.literal(field.displayName() != null ? field.displayName()
                        : ValueText.humanize(field.key()));
                labels.add(label);
                editors.add(new ScalarEditor(ui, label, field.type(), field.enumClass(), () -> values.get(field.key()), v -> {
                    values.put(field.key(), v);
                    edited();
                }, problemText, opener));
            }
            Theme t = ui.theme();
            up = new IconButton(ui, Icon.ARROW_UP, Component.translatable("confee.tooltip.move_up"), 12, t::textOnDark,
                    () -> move(this, -1));
            down = new IconButton(ui, Icon.ARROW_DOWN, Component.translatable("confee.tooltip.move_down"), 12, t::textOnDark,
                    () -> move(this, 1));
            remove = new IconButton(ui, Icon.CROSS, Component.translatable("confee.tooltip.remove"), 12, t::textOnDark,
                    () -> remove(this));
        }

        boolean hasError() {
            return editors.stream().anyMatch(e -> e.error() != null);
        }
    }

    ObjectListRow(Ui ui, EditSession session, Option<?> option, Component name, @Nullable Component description,
                  Function<ValueText.Problem, Component> problemText, Consumer<ConfeeWidget> focusRequest,
                  DropdownWidget.Opener opener) {
        super(ui, session, option, name, description, null);
        this.problemText = problemText;
        this.focusRequest = focusRequest;
        this.opener = opener;
        this.add = new ButtonWidget(ui, Component.translatable("confee.button.add"), ButtonWidget.Style.NORMAL, this::addCard);
        rebuild();
    }

    private List<?> current() {
        return (List<?>) session.get(option);
    }

    private void rebuild() {
        cards.clear();
        for (Object element : current()) {
            cards.add(new Card((Map<?, ?>) element));
        }
        shownList = current();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void edited() {
        if (cards.stream().anyMatch(Card::hasError)) {
            return;
        }
        List<Object> list = new ArrayList<>();
        for (Card card : cards) {
            list.add(Collections.unmodifiableMap(new LinkedHashMap<>(card.values)));
        }
        List<Object> value = List.copyOf(list);
        ((EditSession) session).set((Option) option, value);
        shownList = value;
    }

    private void addCard() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (ObjectField field : option.objectFields()) {
            defaults.put(field.key(), field.defaultValue());
        }
        Card card = new Card(defaults);
        cards.add(card);
        edited();
        focusRequest.accept(card.editors.get(0).widget);
    }

    private void remove(Card card) {
        cards.remove(card);
        edited();
        focusRequest.accept(add);
    }

    private void move(Card card, int direction) {
        int index = cards.indexOf(card);
        int target = index + direction;
        if (target < 0 || target >= cards.size()) {
            return;
        }
        cards.remove(index);
        cards.add(target, card);
        edited();
        focusRequest.accept(direction < 0 ? card.up : card.down);
    }

    @Override
    void syncValue() {
        boolean editing = cards.stream().flatMap(c -> c.editors.stream()).anyMatch(e -> e.widget.isFocused());
        if (!editing && !Objects.equals(shownList, current()) && cards.stream().noneMatch(Card::hasError)) {
            rebuild();
        }
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).up.setEnabled(editable && i > 0);
            cards.get(i).down.setEnabled(editable && i < cards.size() - 1);
        }
    }

    @Override
    int layout(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        reset.setBounds(x + width - ICON, y - 1, ICON, ICON);
        int cx = x + OptionRow.DOT_GUTTER;
        int cw = width - OptionRow.DOT_GUTTER;
        int labelW = Math.max(50, (cw - 2 * PAD) * 2 / 5);
        layoutSubtitle(width);
        int cy = y + headerHeight() + 2;
        for (Card card : cards) {
            card.top = cy;
            // ▲ ▼ only with @Reorderable: the remove button takes the place of the arrows otherwise
            int buttons = option.reorderable() ? 3 : 1;
            int bx = cx + cw - PAD - buttons * 12 - (buttons - 1) * 2;
            card.up.setBounds(bx, cy + 1, 12, 12);
            card.down.setBounds(bx + 14, cy + 1, 12, 12);
            card.remove.setBounds(bx + (buttons - 1) * 14, cy + 1, 12, 12);
            int fy = cy + HEADER_H;
            for (ScalarEditor editor : card.editors) {
                int h = editor.height();
                editor.widget.setBounds(cx + PAD + labelW, fy + (FIELD_H - h) / 2, cw - 2 * PAD - labelW, h);
                fy += FIELD_H + (editor.error() != null ? 11 : 0) + GAP;
            }
            card.bottom = fy - GAP + PAD;
            cy = card.bottom + GAP + 1;
        }
        add.setBounds(cx, cy, Math.min(cw, add.preferredWidth()), 18);
        return height = cy + 18 - y;
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        if (!session.isDefault(option)) {
            Draw.rect(g, x, y + 3, 3, 3, t.accent());
        }
        Draw.textFit(g, ui.font(), name, x + OptionRow.DOT_GUTTER, y + 1, width - OptionRow.DOT_GUTTER - 2 * ICON - 6, labelColor());
        renderSubtitle(g);
        renderRestartIcon(g);
        reset.render(g, mouseX, mouseY, delta);
        int cx = x + OptionRow.DOT_GUTTER;
        int cw = width - OptionRow.DOT_GUTTER;
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            Draw.rect(g, cx, card.top, cw, card.bottom - card.top, Draw.mix(t.panelBg(), t.panelBgDeep(), 0.55f));
            Draw.outline(g, cx, card.top, cw, card.bottom - card.top, Draw.mix(t.panelBg(), t.outline(), 0.5f));
            Draw.text(g, ui.font(), "#" + (i + 1), cx + PAD, card.top + 3, ui.secondaryText());
            if (option.reorderable()) {
                card.up.render(g, mouseX, mouseY, delta);
                card.down.render(g, mouseX, mouseY, delta);
            }
            card.remove.render(g, mouseX, mouseY, delta);
            for (int f = 0; f < card.editors.size(); f++) {
                ScalarEditor editor = card.editors.get(f);
                int rowY = editor.widget.y + editor.widget.height / 2 - 4;
                int labelW = editor.widget.x - cx - 2 * PAD;
                Draw.textFit(g, ui.font(), card.labels.get(f), cx + PAD, rowY, labelW, labelColor());
                editor.widget.render(g, mouseX, mouseY, delta);
                if (editor.error() != null) {
                    Draw.textFit(g, ui.font(), editor.error(), editor.widget.x, editor.widget.y + editor.widget.height + 2,
                            editor.widget.width, t.error());
                }
            }
        }
        add.render(g, mouseX, mouseY, delta);
    }

    @Override
    List<ConfeeWidget> widgets() {
        List<ConfeeWidget> list = new ArrayList<>();
        list.add(reset);
        for (Card card : cards) {
            for (ScalarEditor editor : card.editors) {
                list.add(editor.widget);
            }
            if (option.reorderable()) {
                list.add(card.up);
                list.add(card.down);
            }
            list.add(card.remove);
        }
        list.add(add);
        return list;
    }

    @Override
    boolean hasError() {
        return !hiddenByCondition && cards.stream().anyMatch(Card::hasError);
    }
}

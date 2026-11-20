package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.OptionType;
import fr.heetstudio.confee.ui.EditSession;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.ValueText;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import fr.heetstudio.confee.ui.widget.ButtonWidget;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import fr.heetstudio.confee.ui.widget.DropdownWidget;
import fr.heetstudio.confee.ui.widget.IconButton;
import fr.heetstudio.confee.ui.widget.TextFieldWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Map editor: one line per entry (key field, value widget, ✕), "+ Add" at the bottom. Keys must be filled and unique;
 * the map is only applied when every entry is valid.
 * <p>Internal: may change without notice.</p>
 */
final class MapRow extends OptionRow {

    private static final int GAP = 2;
    private static final int ENTRY_H = 18;

    private final Function<ValueText.Problem, Component> problemText;
    private final Consumer<ConfeeWidget> focusRequest;
    private final DropdownWidget.Opener opener;
    private final List<Entry> entries = new ArrayList<>();
    private final ButtonWidget add;
    @Nullable
    private Map<?, ?> shownMap;

    private final class Entry {
        final TextFieldWidget key;
        final ScalarEditor value;
        Object current;
        final IconButton remove;

        Entry(String keyText, Object initial) {
            this.current = initial;
            key = new TextFieldWidget(ui, Component.translatable("confee.map.key"), keyText, 256, null, s -> edited());
            key.setHint(Component.translatable("confee.map.key"));
            value = new ScalarEditor(ui, name, option.elementType(), option.enumClass(), () -> current, v -> {
                current = v;
                edited();
            }, problemText, opener);
            remove = new IconButton(ui, Icon.CROSS, Component.translatable("confee.tooltip.remove"), 14,
                    ui.theme()::textOnDark, () -> remove(this));
        }

        @Nullable
        Component error() {
            return key.error() != null ? key.error() : value.error();
        }
    }

    MapRow(Ui ui, EditSession session, Option<?> option, Component name, @Nullable Component description,
           Function<ValueText.Problem, Component> problemText, Consumer<ConfeeWidget> focusRequest,
           DropdownWidget.Opener opener) {
        super(ui, session, option, name, description, null);
        this.problemText = problemText;
        this.focusRequest = focusRequest;
        this.opener = opener;
        this.add = new ButtonWidget(ui, Component.translatable("confee.button.add"), ButtonWidget.Style.NORMAL, this::addEntry);
        rebuild();
    }

    private Map<?, ?> current() {
        return (Map<?, ?>) session.get(option);
    }

    private void rebuild() {
        entries.clear();
        current().forEach((k, v) -> entries.add(new Entry(String.valueOf(k), v)));
        shownMap = current();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void edited() {
        Map<String, Object> map = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        boolean ok = true;
        for (Entry entry : entries) {
            String key = entry.key.text().trim();
            if (key.isEmpty()) {
                entry.key.setError(Component.translatable("confee.error.empty_key"));
                ok = false;
            } else if (!seen.add(key)) {
                entry.key.setError(Component.translatable("confee.error.duplicate_key"));
                ok = false;
            } else {
                entry.key.setError(null);
            }
            if (entry.value.error() != null) {
                ok = false;
            }
            map.put(key, entry.current);
        }
        if (ok) {
            Map<String, Object> value = Collections.unmodifiableMap(map);
            ((EditSession) session).set((Option) option, value);
            shownMap = value;
        }
    }

    private Object defaultValue() {
        OptionType type = option.elementType();
        return switch (type) {
            case BOOLEAN -> false;
            case INT -> 0;
            case LONG -> 0L;
            case FLOAT -> 0f;
            case DOUBLE -> 0.0;
            case ENUM -> option.enumClass().getEnumConstants()[0];
            default -> "";
        };
    }

    private void addEntry() {
        Entry entry = new Entry("", defaultValue());
        entries.add(entry);
        edited();
        focusRequest.accept(entry.key);
    }

    private void remove(Entry entry) {
        entries.remove(entry);
        edited();
        focusRequest.accept(add);
    }

    @Override
    void syncValue() {
        boolean editing = entries.stream().anyMatch(e -> e.key.isFocused() || e.value.widget.isFocused());
        if (!editing && !Objects.equals(shownMap, current()) && entries.stream().noneMatch(e -> e.error() != null)) {
            rebuild();
        }
    }

    @Override
    int layout(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        reset.setBounds(x + width - ICON, y - 1, ICON, ICON);
        int cx = x + DOT_GUTTER;
        int cw = width - DOT_GUTTER - 14 - GAP;
        int keyW = cw * 2 / 5;
        layoutSubtitle(width);
        int cy = y + headerHeight();
        for (Entry entry : entries) {
            entry.key.setBounds(cx, cy, keyW - GAP, ENTRY_H);
            entry.value.widget.setBounds(cx + keyW, cy + (ENTRY_H - entry.value.height()) / 2, cw - keyW, entry.value.height());
            entry.remove.setBounds(cx + cw + GAP, cy + (ENTRY_H - 14) / 2, 14, 14);
            cy += ENTRY_H + (entry.error() != null ? 11 : 0) + GAP;
        }
        add.setBounds(cx, cy, Math.min(cw, add.preferredWidth()), 18);
        return height = cy + 18 - y;
    }

    @Override
    void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        if (!session.isDefault(option)) {
            Draw.rect(g, x, y + 3, 3, 3, t.accent());
        }
        Draw.textFit(g, ui.font(), name, x + DOT_GUTTER, y + 1, width - DOT_GUTTER - 2 * ICON - 6, labelColor());
        renderSubtitle(g);
        renderRestartIcon(g);
        reset.render(g, mouseX, mouseY, delta);
        for (Entry entry : entries) {
            entry.key.render(g, mouseX, mouseY, delta);
            entry.value.widget.render(g, mouseX, mouseY, delta);
            entry.remove.render(g, mouseX, mouseY, delta);
            if (entry.error() != null) {
                Draw.textFit(g, ui.font(), entry.error(), entry.key.x, entry.key.y + ENTRY_H + 2, width - DOT_GUTTER, t.error());
            }
        }
        add.render(g, mouseX, mouseY, delta);
    }

    @Override
    List<ConfeeWidget> widgets() {
        List<ConfeeWidget> list = new ArrayList<>();
        list.add(reset);
        for (Entry entry : entries) {
            list.add(entry.key);
            list.add(entry.value.widget);
            list.add(entry.remove);
        }
        list.add(add);
        return list;
    }

    @Override
    boolean hasError() {
        return !hiddenByCondition && entries.stream().anyMatch(e -> e.error() != null);
    }
}

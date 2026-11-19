package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.OptionType;
import fr.heetstudio.confee.core.Validated;
import fr.heetstudio.confee.ui.EditSession;
import fr.heetstudio.confee.ui.Labels;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.ValueText;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import fr.heetstudio.confee.ui.widget.ButtonWidget;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import fr.heetstudio.confee.ui.widget.DropdownWidget;
import fr.heetstudio.confee.ui.widget.IconButton;
import fr.heetstudio.confee.ui.widget.TextFieldWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * List editor (spec 10.3): one text field (or a dropdown for enums) per entry with ▲ ▼ (reorder) and ✕ (remove),
 * "+ Add" at the bottom. The list is only applied when every entry is valid.
 * <p>List options (spec 6.1): values already in the list are not suggested nor offered by "+ Add" and the picker
 * (unless {@code @AllowDuplicates}); {@code @Size} greys out "+ Add" / ✕ at the bounds; ▲ ▼ only with
 * {@code @Reorderable}; {@code @Sorted} hides the
 * arrows; {@code @Range} / {@code @Pattern} / {@code @MaxLength} on the list show an error on the field of the value.</p>
 * <p>Internal: may change without notice.</p>
 */
final class ListRow extends OptionRow {

    private static final int GAP = 2;

    private final Function<ValueText.Problem, Component> problemText;
    private final Consumer<ConfeeWidget> focusRequest;
    private final DropdownWidget.Opener opener;
    private final List<Entry> entries = new ArrayList<>();
    private final ButtonWidget add;
    @Nullable
    private List<?> shownList;
    @Nullable
    private final Function<String, List<String>> suggester;
    private final Consumer<Overlay> overlays;
    /** Declared or guessed registry of the entries, or null. */
    @Nullable
    private final String registry;
    private final Runnable closeOverlay;
    /** IDs allowed by the option's filters ({@code @Entity(only = ...)}), or null. */
    @Nullable
    private final java.util.function.Predicate<String> accepted;
    /** How many entries hold each value (duplicate key), rebuilt only when the list changes (not every frame). */
    @Nullable
    private java.util.Map<Object, Integer> usedCache;

    private final class Entry {
        @Nullable
        final TextFieldWidget field;
        @Nullable
        final DropdownWidget<Object> dropdown;
        Object enumValue;
        final IconButton up;
        final IconButton down;
        final IconButton remove;

        @SuppressWarnings({"unchecked", "rawtypes"})
        Entry(Object value) {
            Theme t = ui.theme();
            if (isEnumList()) {
                enumValue = value;
                List<Object> constants = Arrays.asList((Object[]) option.enumClass().getEnumConstants());
                dropdown = new DropdownWidget<>(ui, name, constants, Labels::enumValue, () -> enumValue, v -> {
                    enumValue = v;
                    edited();
                }, opener);
                field = null;
            } else {
                field = new TextFieldWidget(ui, name, value instanceof Double || value instanceof Float
                        ? ValueText.number(value) : String.valueOf(value), 32767, numericFilter(), s -> edited());
                dropdown = null;
                if (suggester != null) {
                    // values already in the list are not suggested again (unless the list allows duplicates)
                    field.setSuggester(option.allowDuplicates() ? suggester
                            : text -> withoutUsed(suggester.apply(text), this));
                }
                if (fr.heetstudio.confee.ui.RegistryIcons.hasIcons(registry)) {
                    TextFieldWidget f = field;
                    field.setIconRegistry(registry);
                    Entry self = this;
                    field.setPickerAction(() -> overlays.accept(new ItemPickerPopup(ui, name, registry, f.text(),
                            usedIds(self), ItemPickerPopup.Styles.of(option, registry), f::setTextAndNotify, closeOverlay)),
                            Component.translatable("confee.tooltip.pick"));
                }
            }
            up = new IconButton(ui, Icon.ARROW_UP, Component.translatable("confee.tooltip.move_up"), 14,
                    t::textOnDark, () -> move(this, -1));
            down = new IconButton(ui, Icon.ARROW_DOWN, Component.translatable("confee.tooltip.move_down"), 14,
                    t::textOnDark, () -> move(this, 1));
            remove = new IconButton(ui, Icon.CROSS, Component.translatable("confee.tooltip.remove"), 14,
                    t::textOnDark, () -> remove(this));
        }

        ConfeeWidget input() {
            return field != null ? field : dropdown;
        }

        @Nullable
        Component error() {
            return field == null ? null : field.error();
        }
    }

    ListRow(Ui ui, EditSession session, Option<?> option, Component name, @Nullable Component description,
            Function<ValueText.Problem, Component> problemText, Consumer<ConfeeWidget> focusRequest,
            DropdownWidget.Opener opener, Consumer<Overlay> overlays, Runnable closeOverlay) {
        super(ui, session, option, name, description, null);
        this.problemText = problemText;
        this.focusRequest = focusRequest;
        this.opener = opener;
        this.overlays = overlays;
        this.closeOverlay = closeOverlay;
        fr.heetstudio.confee.ui.RegistryDetector.Detected detected =
                fr.heetstudio.confee.ui.RegistryDetector.of(session.handle().schema(), option, session.get(option));
        this.registry = detected == null ? null : detected.registry();
        this.accepted = fr.heetstudio.confee.ui.RegistryFilters.of(option);
        this.suggester = fr.heetstudio.confee.ui.Suggestions.forSources(registry, detected != null && detected.tags(),
                option.suggestions(), option.path(), accepted);
        this.add = new ButtonWidget(ui, Component.translatable("confee.button.add"), ButtonWidget.Style.NORMAL, this::addEntry);
        rebuild();
    }

    /** ▲ ▼ move buttons: only on a list whose order matters ({@code @Reorderable}), never on a sorted one. */
    private boolean showArrows() {
        return option.reorderable() && !option.sorted();
    }

    private boolean isEnumList() {
        return option.elementType() == OptionType.ENUM;
    }

    @Nullable
    private java.util.function.Predicate<String> numericFilter() {
        OptionType type = option.elementType();
        if (type == OptionType.INT || type == OptionType.LONG) {
            return s -> ValueText.isNumberText(s, false);
        }
        if (type == OptionType.FLOAT || type == OptionType.DOUBLE) {
            return s -> ValueText.isNumberText(s, true);
        }
        return null;
    }

    private List<?> current() {
        return (List<?>) session.get(option);
    }

    private void rebuild() {
        entries.clear();
        for (Object element : current()) {
            entries.add(new Entry(element));
        }
        shownList = current();
        usedCache = null;
        updateButtons();
    }

    /** Re-reads every entry; applies the list only when all are valid. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void edited() {
        List<Object> values = new ArrayList<>(entries.size());
        boolean ok = true;
        for (Entry entry : entries) {
            if (entry.field == null) {
                values.add(entry.enumValue);
                continue;
            }
            ValueText.Parsed parsed = ValueText.parseElement(option, entry.field.text());
            // one value at a time: @Range / @Pattern / @MaxLength (the size and duplicates are about the whole list)
            Validated<Object> check = parsed.ok() ? option.validateElement(parsed.value()) : null;
            String text = entry.field.text().trim();
            if (!parsed.ok() || check == null || !check.isOk()) {
                entry.field.setError(problemText.apply(parsed.ok() ? ValueText.Problem.INVALID : parsed.problem()));
                ok = false;
            } else if (accepted != null && !text.isEmpty() && !text.startsWith("#") && !accepted.test(text)) {
                entry.field.setError(Component.translatable("confee.error.filtered", text));
                ok = false;
            } else {
                entry.field.setError(null);
                values.add(check.value());
            }
        }
        usedCache = null;
        updateButtons();
        if (ok) {
            List<Object> list = List.copyOf(values);
            ((EditSession) session).set((Option) option, list);
            shownList = list;
        }
    }

    /** @Size: "+ Add" greyed out at the maximum, ✕ at the minimum; "+ Add" also when every enum value is used. */
    private void updateButtons() {
        boolean room = entries.size() < option.maxSize();
        if (room && isEnumList() && !option.allowDuplicates()) {
            room = usedCounts().size() < option.enumClass().getEnumConstants().length;
        }
        add.setEnabled(room);
        boolean removable = entries.size() > option.minSize();
        for (Entry entry : entries) {
            entry.remove.setEnabled(removable);
        }
    }

    /** Duplicate key of an entry: its trimmed text (lower case with @IgnoreCase), or its enum value. */
    private Object keyOf(Entry entry) {
        return entry.field != null ? textKey(entry.field.text()) : entry.enumValue;
    }

    private Object textKey(String text) {
        return option.duplicateKey(text.trim());
    }

    private java.util.Map<Object, Integer> usedCounts() {
        if (usedCache == null) {
            usedCache = new java.util.HashMap<>();
            for (Entry entry : entries) {
                usedCache.merge(keyOf(entry), 1, Integer::sum);
            }
        }
        return usedCache;
    }

    /** Whether another entry than {@code self} holds the value with this key. */
    private boolean usedByOther(Object key, @Nullable Entry self) {
        int count = usedCounts().getOrDefault(key, 0);
        return count - (self != null && key.equals(keyOf(self)) ? 1 : 0) > 0;
    }

    private List<String> withoutUsed(List<String> suggestions, Entry entry) {
        return suggestions.stream().filter(s -> !usedByOther(textKey(s), entry)).toList();
    }

    /** IDs the picker does not offer: the values of the other entries. */
    private java.util.Set<String> usedIds(Entry self) {
        if (option.allowDuplicates()) {
            return java.util.Set.of();
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Entry entry : entries) {
            if (entry != self && entry.field != null) {
                ids.add(entry.field.text().trim());
            }
        }
        return ids;
    }

    /** First value of the new entry: one that is not in the list yet (unless the list allows duplicates). */
    private Object initialValue() {
        OptionType type = option.elementType();
        java.util.function.Predicate<Object> used = key -> !option.allowDuplicates() && usedByOther(key, null);
        if (isEnumList()) {
            Object[] constants = option.enumClass().getEnumConstants();
            for (Object constant : constants) {
                if (!used.test(constant)) {
                    return constant;
                }
            }
            return constants[0];
        }
        if (type == OptionType.BOOLEAN) {
            return used.test("false") && !used.test("true") ? "true" : "false";
        }
        if (type != null && type.isNumeric()) {
            // start at the lower @Range bound of the values when there is one
            long n = option.elementMin() != null ? (long) Math.ceil(option.elementMin().doubleValue()) : 0;
            while (used.test(Long.toString(n)) || used.test(n + ".0")) {
                n++;
            }
            return Long.toString(n);
        }
        return "";
    }

    private void addEntry() {
        Object initial = initialValue();
        Entry entry = new Entry(initial);
        entries.add(entry);
        edited();
        focusRequest.accept(entry.input());
    }

    private void remove(Entry entry) {
        int index = entries.indexOf(entry);
        entries.remove(entry);
        edited();
        if (entries.isEmpty()) {
            focusRequest.accept(add);
        } else {
            focusRequest.accept(entries.get(Math.min(index, entries.size() - 1)).remove);
        }
    }

    private void move(Entry entry, int direction) {
        int index = entries.indexOf(entry);
        int target = index + direction;
        if (target < 0 || target >= entries.size()) {
            return;
        }
        entries.remove(index);
        entries.add(target, entry);
        edited();
        focusRequest.accept(direction < 0 ? entry.up : entry.down);
    }

    @Override
    void syncValue() {
        // list replaced elsewhere (reset): rebuild the entries
        boolean editing = entries.stream().anyMatch(e -> e.input().isFocused());
        if (!editing && !Objects.equals(shownList, current()) && entries.stream().noneMatch(e -> e.error() != null)) {
            rebuild();
        }
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).up.setEnabled(editable && i > 0);
            entries.get(i).down.setEnabled(editable && i < entries.size() - 1);
        }
    }

    @Override
    int layout(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        reset.setBounds(x + width - ICON, y - 1, ICON, ICON);
        int cx = x + DOT_GUTTER;
        int cw = width - DOT_GUTTER;
        layoutSubtitle(width);
        int cy = y + headerHeight();
        int arrows = showArrows() ? 2 : 0;
        int buttons = (arrows + 1) * 14 + arrows * GAP;
        for (Entry entry : entries) {
            entry.input().setBounds(cx, cy, cw - buttons - GAP, TextFieldWidget.HEIGHT);
            int bx = cx + cw - buttons;
            int by = cy + (TextFieldWidget.HEIGHT - 14) / 2;
            entry.up.setBounds(bx, by, 14, 14);
            entry.down.setBounds(bx + 14 + GAP, by, 14, 14);
            entry.remove.setBounds(bx + arrows * (14 + GAP), by, 14, 14);
            cy += TextFieldWidget.HEIGHT;
            if (entry.error() != null) {
                cy += 11;
            }
            cy += GAP;
        }
        add.setBounds(cx, cy, Math.min(cw, add.preferredWidth()), 18);
        cy += 18;
        return height = cy - y;
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        if (!session.isDefault(option)) {
            Draw.rect(g, x, y + 3, 3, 3, t.accent());
        }
        Draw.textFit(g, ui.font(), name, x + DOT_GUTTER, y + 1, width - DOT_GUTTER - 2 * ICON - 6, labelColor());
        renderSubtitle(g);
        renderRestartIcon(g);
        reset.render(g, mouseX, mouseY, delta);
        for (Entry entry : entries) {
            entry.input().render(g, mouseX, mouseY, delta);
            if (showArrows()) {
                entry.up.render(g, mouseX, mouseY, delta);
                entry.down.render(g, mouseX, mouseY, delta);
            }
            entry.remove.render(g, mouseX, mouseY, delta);
            if (entry.error() != null) {
                ConfeeWidget input = entry.input();
                Draw.textFit(g, ui.font(), entry.error(), input.x, input.y + input.height + 2, input.width, t.error());
            }
        }
        add.render(g, mouseX, mouseY, delta);
    }

    @Override
    List<ConfeeWidget> widgets() {
        List<ConfeeWidget> list = new ArrayList<>();
        list.add(reset);
        for (Entry entry : entries) {
            list.add(entry.input());
            if (showArrows()) {
                list.add(entry.up);
                list.add(entry.down);
            }
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

package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.core.Conditions;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.Section;
import fr.heetstudio.confee.ui.EditSession;
import fr.heetstudio.confee.ui.Labels;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.ValueText;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import fr.heetstudio.confee.ui.widget.IconButton;
import fr.heetstudio.confee.ui.widget.SliderWidget;
import fr.heetstudio.confee.ui.widget.TextFieldWidget;
import fr.heetstudio.confee.ui.widget.ToggleWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One option (spec 10.4 B): label line with the "modified" dot, the restart "!" icon and the reset ↺ button
 * (only when the value differs from the default), then the control. Toggles keep their label on the same line.
 * Conditions ({@code enabledIf} / {@code visibleIf}) are evaluated on every frame against the pending values.
 * <p>Internal: may change without notice.</p>
 */
class OptionRow extends Row {

    static final int LABEL_H = 11;
    static final int ICON = 11;
    /** Room on the left for the "modified" dot, so labels stay aligned. */
    static final int DOT_GUTTER = 6;

    protected final EditSession session;
    protected final Option<?> option;
    protected final Component name;
    @Nullable
    protected final Component description;
    @Nullable
    protected final ConfeeWidget control;
    @Nullable
    protected final TextFieldWidget field;
    /** Text under the name ({@code @Description}), or null. */
    @Nullable
    protected final Component subtitle;
    private List<net.minecraft.util.FormattedCharSequence> subtitleLines = List.of();
    protected final IconButton reset;
    private final boolean inline;
    private final boolean slider;
    private int labelW;
    /** Hidden by its {@code visibleIf} condition. */
    protected boolean hiddenByCondition;
    /** Grayed out by its {@code enabledIf} condition (or read-only). */
    protected boolean editable = true;

    OptionRow(Ui ui, EditSession session, Option<?> option, Component name, @Nullable Component description,
              @Nullable ConfeeWidget control) {
        super(ui);
        this.session = session;
        this.option = option;
        this.name = name;
        this.description = description;
        this.control = control;
        this.field = control instanceof TextFieldWidget tf ? tf
                : control instanceof fr.heetstudio.confee.ui.widget.StepperWidget stepper ? stepper.field() : null;
        this.subtitle = Labels.subtitle(session.handle().schema(), option);
        this.inline = control instanceof ToggleWidget;
        this.slider = control instanceof SliderWidget;
        this.reset = new IconButton(ui, Icon.RESET, Component.translatable(resetKey(session)), ICON,
                () -> ui.theme().textOnDark(), () -> session.resetToDefault(option));
    }

    /** Spec 8.3: for other mods' files the reset restores the original value, not a default. */
    static String resetKey(EditSession session) {
        return session.handle().schema().source() == fr.heetstudio.confee.core.ConfigSource.RAW_FILE
                ? "confee.tooltip.restore_option" : "confee.tooltip.reset_option";
    }

    Option<?> option() {
        return option;
    }

    /** Label text; sliders show "Name: value unit" (spec 10.3). */
    Component labelText() {
        if (slider) {
            String value = ValueText.number(session.get(option));
            Component unit = Labels.unit(option);
            return Component.translatable("confee.slider.label", name,
                    unit == null ? Component.literal(value) : Component.literal(value + " ").append(unit));
        }
        return name;
    }

    @Override
    final void sync() {
        updateConditions();
        syncValue();
        reset.setVisible(!hiddenByCondition && editable && !session.isDefault(option));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void updateConditions() {
        Section root = session.handle().schema().root();
        hiddenByCondition = !Conditions.test(option.visibleIf(), root, o -> session.get((Option) o));
        editable = !option.readOnly() && Conditions.test(option.enabledIf(), root, o -> session.get((Option) o));
        for (ConfeeWidget widget : widgets()) {
            widget.setVisible(!hiddenByCondition);
            if (widget != reset) {
                widget.setEnabled(editable);
            }
        }
    }

    /** Refreshes the controls from the edited value (after a reset, a condition...). */
    void syncValue() {
        if (control instanceof fr.heetstudio.confee.ui.widget.MultilineFieldWidget area && !area.isFocused()) {
            String shown = String.valueOf(session.get(option));
            if (!shown.equals(area.text())) {
                area.setTextSilently(shown);
                area.setError(null);
            }
        }
        if (field != null && !field.isFocused()) {
            Object shown = session.get(option);
            ValueText.Parsed parsed = ValueText.parse(option, field.text());
            if (!parsed.ok() || !Objects.equals(parsed.value(), shown)) {
                // value changed elsewhere (reset): show it
                field.setTextSilently(ValueText.toFieldText(option, shown));
                field.setError(null);
            }
        }
    }

    @Override
    boolean hidden() {
        return hiddenByCondition;
    }

    /** Name line + description lines: where the control starts, relative to the row. */
    protected int headerHeight() {
        int base = inline ? ToggleWidget.KNOB : LABEL_H;
        return subtitleLines.isEmpty() ? base : base + subtitleLines.size() * 10 + (inline ? 3 : 2);
    }

    /** Wraps the description to the row width (call at the start of layout). */
    protected void layoutSubtitle(int width) {
        subtitleLines = subtitle == null ? List.of() : ui.font().split(subtitle, Math.max(40, width - DOT_GUTTER - 4));
    }

    /** Draws the description under the name line. */
    protected void renderSubtitle(GuiGraphics g) {
        int ly = y + (inline ? ToggleWidget.KNOB + 3 : LABEL_H);
        int color = editable ? ui.secondaryText() : ui.theme().textDisabled();
        for (net.minecraft.util.FormattedCharSequence line : subtitleLines) {
            Draw.text(g, ui.font(), line, x + DOT_GUTTER, ly, color);
            ly += 10;
        }
    }

    @Override
    int layout(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        layoutSubtitle(width);
        int iconsX = x + width - ICON;
        reset.setBounds(iconsX, y + (inline ? (ToggleWidget.KNOB - ICON) / 2 : -1), ICON, ICON);
        int contentX = x + DOT_GUTTER;
        int contentW = width - DOT_GUTTER;
        if (inline) {
            control.setBounds(contentX, y, ToggleWidget.TRACK, ToggleWidget.KNOB);
            labelW = width - DOT_GUTTER - ToggleWidget.TRACK - 6 - 2 * ICON - 6;
            return height = headerHeight();
        }
        labelW = contentW - 2 * ICON - 6;
        int h = headerHeight();
        if (control != null) {
            int controlH = control.heightFor(contentW);
            control.setBounds(contentX, y + h, contentW, controlH);
            h += controlH;
        }
        if (field != null && field.error() != null) {
            h += 11;
        }
        return height = h;
    }

    /** Label color: grayed when the option cannot be edited. */
    protected int labelColor() {
        return editable ? ui.theme().textOnDark() : ui.theme().textDisabled();
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        int lineY = inline ? y + (ToggleWidget.KNOB - 8) / 2 : y + 1;
        if (!session.isDefault(option) && !option.readOnly()) {
            Draw.rect(g, x, lineY + 2, 3, 3, t.accent());
        }
        if (inline) {
            // the toggle draws its own label; keep it from running under the icons
            ((ToggleWidget) control).setLabel(Component.literal(Draw.fit(ui.font(), name.getString(), labelW)));
        } else {
            Draw.textFit(g, ui.font(), labelText(), x + DOT_GUTTER, lineY, labelW, labelColor());
        }
        renderSubtitle(g);
        renderRestartIcon(g);
        reset.render(g, mouseX, mouseY, delta);
        if (control != null) {
            control.render(g, mouseX, mouseY, delta);
        }
        if (field != null && field.error() != null) {
            Draw.textFit(g, ui.font(), field.error(), x + DOT_GUTTER, field.y + field.height + 2, width - DOT_GUTTER, t.error());
        }
    }

    protected void renderRestartIcon(GuiGraphics g) {
        if (option.requiresRestart()) {
            int wx = warningX();
            int wy = inline ? y + (ToggleWidget.KNOB - 9) / 2 : y;
            Draw.rect(g, wx, wy, 9, 9, ui.theme().warning());
            Icon.EXCLAMATION.drawCentered(g, wx, wy, 9, 9, ui.theme().outline());
        }
    }

    protected int warningX() {
        return x + width - ICON - 3 - 9;
    }

    @Override
    List<ConfeeWidget> widgets() {
        List<ConfeeWidget> list = new ArrayList<>(2);
        if (control != null) {
            list.add(control);
        }
        list.add(reset);
        return list;
    }

    @Override
    @Nullable
    ConfeeScreen.TooltipTarget tooltip(double mx, double my) {
        if (option.requiresRestart()) {
            int wx = warningX();
            int wy = inline ? y + (ToggleWidget.KNOB - 9) / 2 : y;
            if (mx >= wx && mx < wx + 9 && my >= wy && my < wy + 9) {
                return new ConfeeScreen.TooltipTarget(List.of(option, "restart"),
                        Component.translatable("confee.tooltip.requires_restart"), new int[]{wx, wy, 9, 9});
            }
        }
        int labelX = inline ? x + DOT_GUTTER + ToggleWidget.TRACK + 6 : x + DOT_GUTTER;
        int lineH = inline ? ToggleWidget.KNOB : LABEL_H;
        int textW = Math.min(labelW <= 0 ? width : labelW, ui.font().width(inline ? name : labelText()));
        if (mx >= labelX && mx < labelX + Math.max(textW, 20) && my >= y && my < y + lineH) {
            Component text = tooltipText();
            // anchored to the whole name + description block, so the tooltip never hides them
            int blockH = inline ? lineH : headerHeight();
            return text == null ? null : new ConfeeScreen.TooltipTarget(List.of(option, "label"), text,
                    new int[]{labelX, y, Math.max(textW, 20), blockH});
        }
        return null;
    }

    /** Description, plus why the option is grayed out when a condition disables it. */
    @Nullable
    protected Component tooltipText() {
        Component condition = null;
        if (!editable && !option.readOnly() && option.enabledIf() != null) {
            Section root = session.handle().schema().root();
            String target = Conditions.target(option.enabledIf());
            Component targetName = root.findOption(target)
                    .map(o -> Labels.name(session.handle().schema(), o, o.path()))
                    .orElse(Component.literal(target));
            condition = Component.translatable("confee.tooltip.condition", targetName);
        }
        if (description == null) {
            return condition;
        }
        return condition == null ? description : description.copy().append("\n").append(condition);
    }

    @Override
    boolean hasError() {
        return !hiddenByCondition && field != null && field.error() != null;
    }
}

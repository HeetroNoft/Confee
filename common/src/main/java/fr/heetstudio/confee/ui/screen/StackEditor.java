package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.StackText;
import fr.heetstudio.confee.core.Validated;
import fr.heetstudio.confee.ui.Suggestions;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.ValueText;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import fr.heetstudio.confee.ui.widget.TextFieldWidget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Fields of one {@code @ItemStack} value: item (icon, suggestions by name, picker), count, extra data (SNBT). Reads
 * and writes the SNBT text of the value; reports the first problem of the three fields.
 * <p>Internal: may change without notice.</p>
 */
final class StackEditor {

    static final int COUNT_W = 40;
    private static final int GAP = 2;

    private final Option<?> option;
    private final Option.StackSpec spec;
    final TextFieldWidget item;
    @Nullable
    final TextFieldWidget count;
    @Nullable
    final TextFieldWidget extra;
    @Nullable
    private Component error;
    /** Count kept when the count field is hidden. */
    private int hiddenCount = 1;

    /**
     * @param picker opens the item picker with the current ID; the second argument applies the picked ID
     * @param edited called after each edit of any field
     */
    StackEditor(Ui ui, Option<?> option, Component label, String value, BiConsumer<String, Consumer<String>> picker,
                Runnable edited) {
        this.option = option;
        this.spec = option.stackSpec();
        String id = value;
        String countText = "1";
        String extraText = "";
        try {
            StackText stack = StackText.parse(value);
            id = stack.id();
            countText = Integer.toString(stack.count());
            hiddenCount = stack.count();
            extraText = stack.extra() == null ? "" : stack.extra();
        } catch (IllegalArgumentException e) {
            // kept as written in the file (OnFileError.KEEP): shown in the item field with its error
        }
        item = new TextFieldWidget(ui, label, id, 32767, null, s -> edited.run());
        item.setIconRegistry("minecraft:item");
        item.setSuggester(Suggestions.forSources("minecraft:item", false, null, option.path()));
        item.setHint(Component.translatable("confee.stack.item"));
        TextFieldWidget itemField = item;
        item.setPickerAction(() -> picker.accept(itemField.text().trim(), itemField::setTextAndNotify),
                Component.translatable("confee.tooltip.pick"));
        if (spec.count()) {
            count = new TextFieldWidget(ui, Component.translatable("confee.stack.count"), countText, 6,
                    s -> ValueText.isNumberText(s, false), s -> edited.run());
        } else {
            count = null;
        }
        if (spec.components()) {
            extra = new TextFieldWidget(ui, Component.translatable("confee.stack.extra"), extraText, 32767, null, s -> edited.run());
            extra.setHint(Component.translatable("confee.stack.extra_hint"));
        } else {
            extra = null;
        }
        check();
    }

    List<ConfeeWidget> widgets() {
        List<ConfeeWidget> list = new ArrayList<>(3);
        list.add(item);
        if (count != null) {
            list.add(count);
        }
        if (extra != null) {
            list.add(extra);
        }
        return list;
    }

    /** Places the fields; returns the height (error line included). */
    int layout(int x, int y, int width) {
        int itemW = count == null ? width : width - COUNT_W - GAP;
        item.setBounds(x, y, itemW, TextFieldWidget.HEIGHT);
        if (count != null) {
            count.setBounds(x + itemW + GAP, y, COUNT_W, TextFieldWidget.HEIGHT);
        }
        int h = TextFieldWidget.HEIGHT;
        if (extra != null) {
            extra.setBounds(x, y + h + GAP, width, TextFieldWidget.HEIGHT);
            h += GAP + TextFieldWidget.HEIGHT;
        }
        return h + (error != null ? 11 : 0);
    }

    @Nullable
    Component error() {
        return error;
    }

    /**
     * Checks the three fields and marks the wrong one.
     *
     * @return the SNBT of the stack, or null when a field is wrong
     */
    @Nullable
    String check() {
        error = null;
        item.setError(null);
        if (count != null) {
            count.setError(null);
        }
        if (extra != null) {
            extra.setError(null);
        }
        String id = item.text().trim();
        if (id.startsWith("{")) {
            // a stack of the file that could not be read, kept as written (OnFileError.KEEP)
            return fail(item, Component.translatable("confee.error.stack"));
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return fail(item, Component.translatable("confee.error.unknown_item", id));
        }
        Item found = BuiltInRegistries.ITEM.get(rl);
        int n = hiddenCount;
        if (count != null) {
            try {
                n = Integer.parseInt(count.text().trim());
            } catch (NumberFormatException e) {
                return fail(count, Component.translatable("confee.error.not_an_integer"));
            }
            int max = spec.maxCount() > 0 ? spec.maxCount() : spec.maxCount() == 0 ? found.getDefaultMaxStackSize() : Integer.MAX_VALUE;
            if (n < 1 || n > max) {
                return fail(count, Component.translatable("confee.error.range", 1, max));
            }
        }
        String extraText = extra == null ? null : extra.text().trim();
        if (extraText != null && !extraText.isEmpty()) {
            if (!extraText.startsWith("{") || StackText.extraProblem(extraText) != null) {
                return fail(extra, Component.translatable("confee.error.snbt"));
            }
        }
        String snbt = new StackText(rl.toString(), n, extraText).write();
        // the mod's own @Validator, after Confee's checks
        Validated<?> mod = option.type() == fr.heetstudio.confee.core.OptionType.LIST
                ? option.validateElement(snbt) : option.validate(snbt);
        if (!mod.isOk() && mod.reason() != null) {
            return fail(item, ValueText.reason(mod.reason()));
        }
        return snbt;
    }

    @Nullable
    private String fail(TextFieldWidget field, Component message) {
        field.setError(message);
        error = message;
        return null;
    }
}

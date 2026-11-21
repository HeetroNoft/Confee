package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.core.ConfigSource;
import fr.heetstudio.confee.core.EditableConfig;
import fr.heetstudio.confee.external.ExternalConfig;
import fr.heetstudio.confee.core.ConfigSchema;
import fr.heetstudio.confee.core.Node;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.OptionType;
import fr.heetstudio.confee.core.Section;
import fr.heetstudio.confee.core.WidgetHint;
import fr.heetstudio.confee.ui.EditSession;
import fr.heetstudio.confee.ui.Labels;
import fr.heetstudio.confee.ui.ValueText;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.render.Icon;
import fr.heetstudio.confee.api.Decoration;
import fr.heetstudio.confee.api.Layout;
import fr.heetstudio.confee.ui.ItemIcons;
import fr.heetstudio.confee.ui.Suggestions;
import fr.heetstudio.confee.ui.widget.ButtonWidget;
import fr.heetstudio.confee.ui.widget.CheckboxesWidget;
import fr.heetstudio.confee.ui.widget.KeybindWidget;
import fr.heetstudio.confee.ui.widget.StepperWidget;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import fr.heetstudio.confee.ui.widget.DropdownWidget;
import fr.heetstudio.confee.ui.widget.IconButton;
import fr.heetstudio.confee.ui.widget.MultilineFieldWidget;
import fr.heetstudio.confee.ui.widget.SegmentedWidget;
import fr.heetstudio.confee.ui.widget.SliderWidget;
import fr.heetstudio.confee.ui.widget.TextFieldWidget;
import fr.heetstudio.confee.ui.widget.ToggleWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Main config screen (spec 10.4 B): light title bar with back arrow and search, category sidebar (only with 2+
 * categories; folds into a dropdown when the window is narrow), scrolling option panel, bottom bar with
 * "Reset all", "Cancel" and the primary "Save". Changes stay pending until "Save".
 * <p>Internal: may change without notice.</p>
 */
public final class ConfigScreen extends ConfeeScreen {

    private static final int TITLE_H = 22;
    private static final int BOTTOM_H = 28;
    private static final int SIDEBAR_W = 116;

    private static final int PANEL_MIN = 200;
    private static final int GAP = 6;
    private static final int PAD = 8;
    private static final int ROW_GAP = 8;
    private static final int SCROLL_STEP = 24;

    private final EditableConfig handle;
    private final ConfigSchema schema;
    private final EditSession session;
    private final List<Category> categories = new ArrayList<>();
    private final boolean hasEditableOptions;

    // state kept across resizes
    private int selected;
    private final List<Section> nav = new ArrayList<>();
    private String query = "";
    private double scroll;
    private double sidebarScroll;

    // widgets (rebuilt by build())
    private IconButton back;
    private TextFieldWidget search;
    private final List<ButtonWidget> categoryButtons = new ArrayList<>();
    @Nullable
    private DropdownWidget<Integer> categoryDropdown;
    private ButtonWidget resetAll;
    private ButtonWidget cancel;
    private ButtonWidget save;
    private final List<Row> rows = new ArrayList<>();

    // layout
    private boolean showSidebar;
    private boolean collapsed;
    private int left;
    private int contentW;
    private int panelX;
    private int panelW;
    private int[] viewport = new int[4];
    private int contentH;
    private boolean draggingScrollbar;
    @Nullable
    private ConfeeWidget reveal;
    // preview column (optional, hidden on narrow windows)
    private final boolean hasPreviews;
    private PreviewPanel previewPanel;
    private boolean showPreview;
    private int previewX;
    @Nullable
    private Option<?> previewOption;

    private record Category(Component label, Section section, boolean general) {
    }

    public ConfigScreen(Screen parent, EditableConfig handle) {
        super(title(handle), parent);
        this.handle = handle;
        this.schema = handle.schema();
        this.session = new EditSession(handle);
        collectCategories();
        boolean[] editable = {false};
        schema.root().forEachOption(o -> editable[0] |= !o.readOnly() && !o.hidden());
        this.hasEditableOptions = editable[0];
        this.hasPreviews = PreviewPanel.anyPreview(schema);
        this.layout = schema.layout();
    }

    private final Layout layout;

    /** "Mod name — Config name" (spec 10.4 B); only the mod name for its main config; the path for raw files. */
    private static Component title(EditableConfig handle) {
        ConfigSchema schema = handle.schema();
        Component mod = Labels.modName(schema.modId());
        if (handle instanceof ExternalConfig external) {
            return Component.translatable("confee.screen.title", mod, external.relativePath());
        }
        return schema.name() == null ? mod
                : Component.translatable("confee.screen.title", mod, Labels.configName(schema));
    }

    /** Root options form the "General" category; each root section is a category (spec 10.4 B). */
    private void collectCategories() {
        Section root = schema.root();
        boolean rootOptions = root.children().stream().anyMatch(n -> n instanceof Option<?> o && !o.hidden());
        if (rootOptions) {
            categories.add(new Category(Component.translatable("confee.screen.general"), root, true));
        }
        for (Node child : root.children()) {
            if (child instanceof Section section && hasVisibleOptions(section)) {
                categories.add(new Category(Labels.section(schema, section), section, false));
            }
        }
    }

    private static boolean hasVisibleOptions(Section section) {
        if (section.listActions() != null) {
            // a list of objects / lists, even emptied: it still has "+ Add"
            return true;
        }
        boolean[] any = {false};
        section.forEachOption(o -> any[0] |= !o.hidden());
        return any[0];
    }

    @Override
    protected Theme theme() {
        return schema.theme();
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void build() {
        Theme t = ui.theme();
        back = new IconButton(ui, Icon.BACK, Component.translatable("confee.nav.back"), 16, t::titleBarText, this::requestClose);
        search = new TextFieldWidget(ui, Component.translatable("confee.screen.search"), query, 64, null, text -> {
            query = text;
            scroll = 0;
            rebuildRows();
        });
        search.setHint(Component.translatable("confee.screen.search"));
        search.setLeadingIcon(Icon.SEARCH);

        previewPanel = new PreviewPanel(ui, schema, session, layout.previewWidth());
        categoryButtons.clear();
        for (int i = 0; i < categories.size(); i++) {
            int index = i;
            ButtonWidget button = new ButtonWidget(ui, categories.get(i).label(), ButtonWidget.Style.NORMAL, () -> selectCategory(index));
            if (!categories.get(i).general()) {
                // the comment of the section / table, as a tooltip
                Section section = categories.get(i).section();
                button.setTooltip(Labels.description(schema, section, section.path()));
                button.setIcon(section.icon());
            }
            categoryButtons.add(button);
        }
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            indexes.add(i);
        }
        categoryDropdown = categories.size() < 2 ? null : new DropdownWidget<>(ui, Component.translatable("confee.screen.categories"),
                indexes, i -> categories.get(i).label(), () -> selected, this::selectCategory, this::openDropdown);

        resetAll = new ButtonWidget(ui, Component.translatable("confee.button.reset_all"), ButtonWidget.Style.NORMAL, this::confirmResetAll);
        cancel = new ButtonWidget(ui, Component.translatable("confee.button.cancel"), ButtonWidget.Style.NORMAL, this::onClose);
        save = new ButtonWidget(ui, Component.translatable("confee.button.save"), ButtonWidget.Style.PRIMARY, this::saveAndClose);
        save.setDisabledReason(Component.translatable("confee.tooltip.save_blocked"));

        computeLayout();
        rebuildRows();
    }

    private void computeLayout() {
        boolean wantsSidebar = categories.size() >= 2;
        showSidebar = wantsSidebar && layout.sidebar() != Layout.Sidebar.HIDDEN && width >= SIDEBAR_W + GAP + PANEL_MIN + 2 * PAD;
        collapsed = wantsSidebar && !showSidebar;
        int sideW = showSidebar ? SIDEBAR_W + GAP : 0;
        // the preview column only when there is room for it (hidden otherwise)
        showPreview = hasPreviews && width >= sideW + PANEL_MIN + GAP + previewPanel.width() + 2 * PAD;
        int previewW = showPreview ? GAP + previewPanel.width() : 0;
        int max = sideW + layout.panelWidth() + previewW;
        contentW = Math.min(max, width - 2 * PAD);
        if (width - 2 * PAD < PANEL_MIN) {
            contentW = width;
        }
        left = (width - contentW) / 2;
        panelX = showSidebar ? left + SIDEBAR_W + GAP : left;
        panelW = contentW - sideW - previewW;
        previewX = panelX + panelW + GAP;
        int top = TITLE_H + (collapsed ? DropdownWidget.HEIGHT + 2 * GAP : 0);
        viewport = new int[]{panelX, top, panelX + panelW, height - BOTTOM_H};
    }

    private void selectCategory(int index) {
        if (index == selected && nav.isEmpty() && query.isEmpty()) {
            return;
        }
        selected = index;
        nav.clear();
        scroll = 0;
        if (!query.isEmpty()) {
            search.setTextSilently("");
            query = "";
        }
        rebuildRows();
    }

    private void openDropdown(DropdownWidget<?> dropdown) {
        openOverlay(new DropdownPopup(ui, dropdown, height, this::closeOverlay));
    }

    // ------------------------------------------------------------------ rows

    private boolean isRawFile() {
        return schema.source() == ConfigSource.RAW_FILE;
    }

    private void rebuildRows() {
        rows.clear();
        if (handle instanceof ExternalConfig external) {
            // spec 8.3: the mod may only read its file at startup
            rows.add(new Row.Banner(ui, Component.translatable("confee.banner.raw_file"),
                    new ButtonWidget(ui, Component.translatable("confee.button.open_file"), ButtonWidget.Style.NORMAL,
                            () -> com.mojang.blaze3d.Blaze3D.openPath(external.file()))));
        }
        if (schema.source() == ConfigSource.FORGE_SPEC && schema.scope() == fr.heetstudio.confee.api.ConfigScope.SERVER) {
            // spec 8.4: server configs are read-only until the server sync arrives
            rows.add(new Row.Banner(ui, Component.translatable("confee.banner.server_read_only"), null));
        }
        if (categories.isEmpty()) {
            rows.add(new Row.Message(ui, Component.translatable("confee.empty")));
            return;
        }
        if (!query.isBlank()) {
            buildSearchRows();
            return;
        }
        Category category = categories.get(Math.min(selected, categories.size() - 1));
        Section section = nav.isEmpty() ? category.section() : nav.get(nav.size() - 1);
        if (layout.banner() != null && nav.isEmpty()) {
            rows.add(new DecorationRow(ui, Decoration.image(layout.banner(), layout.bannerHeight()), null));
        }
        if (!nav.isEmpty()) {
            rows.add(new Row.Header(ui, Labels.section(schema, section), () -> {
                nav.remove(nav.size() - 1);
                scroll = 0;
                rebuildRows();
            }));
        }
        List<Node> children = section.children();
        for (int i = 0; i <= children.size(); i++) {
            for (Section.Placed placed : section.decorations()) {
                if (placed.index() == i) {
                    rows.add(new DecorationRow(ui, placed.decoration(), session));
                }
            }
            if (i == children.size()) {
                break;
            }
            Node child = children.get(i);
            if (section.listActions() != null && child instanceof Section element
                    && fr.heetstudio.confee.format.ParsedList.indexOf(element.key()) >= 0) {
                rows.add(listElementRow(section, element, i == 0, i == children.size() - 1));
            } else if (child instanceof Option<?> option && !option.hidden()) {
                rows.add(createRow(option));
            } else if (child instanceof Section sub && !category.general() && hasVisibleOptions(sub)) {
                ButtonWidget navButton = new ButtonWidget(ui, Labels.section(schema, sub), ButtonWidget.Style.NAVIGATION, () -> {
                    nav.add(sub);
                    scroll = 0;
                    rebuildRows();
                });
                navButton.setTooltip(Labels.description(schema, sub, sub.path()));
                navButton.setIcon(sub.icon());
                rows.add(new Row.Single(ui, navButton));
            }
        }
        if (section.listActions() != null) {
            fr.heetstudio.confee.core.ListActions actions = section.listActions();
            rows.add(new Row.Single(ui, new ButtonWidget(ui, Component.translatable("confee.button.add"), ButtonWidget.Style.NORMAL,
                    () -> changeList(() -> actions.add(session.pendingValues())))));
        }
    }

    // ------------------------------------------------------------------ lists of objects / lists (spec 8.2 bis)

    private Row listElementRow(Section list, Section element, boolean first, boolean last) {
        fr.heetstudio.confee.core.ListActions actions = list.listActions();
        int index = fr.heetstudio.confee.format.ParsedList.indexOf(element.key());
        ButtonWidget open = new ButtonWidget(ui, Labels.section(schema, element), ButtonWidget.Style.NAVIGATION, () -> {
            nav.add(element);
            scroll = 0;
            rebuildRows();
        });
        open.setTooltip(Labels.description(schema, element, element.path()));
        return new Row.ListElement(ui, open, first, last,
                () -> changeList(() -> actions.move(index, -1, session.pendingValues())),
                () -> changeList(() -> actions.move(index, 1, session.pendingValues())),
                () -> changeList(() -> actions.remove(index, session.pendingValues())));
    }

    /**
     * Runs a list change (it keeps the values being edited), then opens the rebuilt file on the same category and
     * sub-screen. The change stays in memory until "Save".
     */
    private void changeList(java.util.function.BooleanSupplier change) {
        if (!change.getAsBoolean()) {
            Modal modal = new Modal(ui, Component.translatable("confee.modal.list_failed.title"),
                    Component.translatable("confee.modal.list_failed.message"), this::closeOverlay);
            modal.button(Component.translatable("confee.button.ok"), true, this::closeOverlay);
            openOverlay(modal);
            return;
        }
        ConfigScreen next = new ConfigScreen(parent, handle);
        String categoryPath = categories.isEmpty() ? "" : categories.get(Math.min(selected, categories.size() - 1)).section().path();
        next.restore(categoryPath, nav.stream().map(Section::path).toList(), scroll);
        minecraft.gui.setScreen(next);
    }

    /** Opens the category and the sub-screens at these paths, as far as they still exist. */
    private void restore(String categoryPath, List<String> navPaths, double previousScroll) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).section().path().equals(categoryPath)) {
                selected = i;
                break;
            }
        }
        for (String path : navPaths) {
            Section found = findSection(schema.root(), path);
            if (found == null) {
                break;
            }
            nav.add(found);
        }
        scroll = previousScroll;
    }

    @Nullable
    private static Section findSection(Section from, String path) {
        if (from.path().equals(path)) {
            return from;
        }
        for (Node child : from.children()) {
            if (child instanceof Section sub && (path.equals(sub.path()) || path.startsWith(sub.path() + "."))) {
                Section found = findSection(sub, path);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Spec 10.4 B: live filter on name + description of every category, grouped by category. */
    private void buildSearchRows() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (Category category : categories) {
            List<Option<?>> matches = new ArrayList<>();
            collectMatches(category.section(), category.general(), q, matches);
            if (!matches.isEmpty()) {
                rows.add(new Row.Header(ui, category.label(), null));
                for (Option<?> option : matches) {
                    rows.add(createRow(option));
                }
            }
        }
        if (rows.stream().noneMatch(r -> r instanceof OptionRow)) {
            rows.add(new Row.Message(ui, Component.translatable("confee.search.no_results", query.trim())));
        }
    }

    private void collectMatches(Section section, boolean general, String q, List<Option<?>> out) {
        for (Node child : section.children()) {
            if (child instanceof Option<?> option && !option.hidden()) {
                String name = Labels.name(schema, option, option.path()).getString().toLowerCase(Locale.ROOT);
                Component description = Labels.description(schema, option, option.path());
                Component subtitle = Labels.subtitle(schema, option);
                String desc = (description == null ? "" : description.getString().toLowerCase(Locale.ROOT))
                        + "\n" + (subtitle == null ? "" : subtitle.getString().toLowerCase(Locale.ROOT));
                if (name.contains(q) || desc.contains(q)) {
                    out.add(option);
                }
            } else if (child instanceof Section sub && !general) {
                collectMatches(sub, false, q, out);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Row createRow(Option<?> option) {
        Component name = Labels.name(schema, option, option.path());
        Component description = Labels.description(schema, option, option.path());
        Option raw = option;
        ConfeeWidget custom = customWidget(option, name);
        if (custom != null) {
            if (option.readOnly()) {
                custom.setEnabled(false);
            }
            return new OptionRow(ui, session, option, name, description, custom);
        }
        ConfeeWidget control;
        switch (option.type()) {
            case BOOLEAN -> control = new ToggleWidget(ui, name, () -> (Boolean) session.get(raw),
                    value -> session.set(raw, value));
            case ENUM -> control = choice(option, name);
            case LIST -> {
                if (option.widgetHint() == WidgetHint.CHECKBOXES) {
                    List<Object> values = Arrays.asList((Object[]) option.enumClass().getEnumConstants());
                    control = new CheckboxesWidget<>(ui, name, values, Labels::enumValue, () -> (List<Object>) session.get(raw),
                            v -> session.set(raw, List.copyOf(v)));
                    break;
                }
                return new ListRow(ui, session, option, name, description, this::problemText, this::focusLater,
                        this::openDropdown, this::openOverlay, this::closeOverlay);
            }
            case MAP -> {
                return new MapRow(ui, session, option, name, description, this::problemText, this::focusLater, this::openDropdown);
            }
            case OBJECT_LIST -> {
                return new ObjectListRow(ui, session, option, name, description, this::problemText, this::focusLater, this::openDropdown);
            }
            case STRING -> control = option.widgetHint() == WidgetHint.MULTILINE ? multiline(option, name)
                    : option.widgetHint() == WidgetHint.KEYBIND ? new KeybindWidget(ui, name, () -> (String) session.get(raw),
                    v -> session.set(raw, v)) : field(option, name);
            default -> {
                boolean sliderOk = option.widgetHint() == WidgetHint.SLIDER && option.min() != null && option.max() != null
                        && option.type().isNumeric();
                if (sliderOk) {
                    control = slider(option, name);
                } else if (option.widgetHint() == WidgetHint.STEPPER && option.type().isNumeric()) {
                    control = stepper(option, name);
                } else {
                    control = field(option, name);
                }
            }
        }
        if (option.readOnly() && control != null) {
            control.setEnabled(false);
        }
        return new OptionRow(ui, session, option, name, description, control);
    }

    /** The mod's own widget: {@code @Widget}, else {@code ConfeeWidgets.forOption/forType}; null when none or broken. */
    @Nullable
    private ConfeeWidget customWidget(Option<?> option, Component name) {
        if (option.readOnly() && schema.source() == ConfigSource.RAW_FILE) {
            return null;
        }
        // Confee configs, Forge configs ("modid:client", "modid:common"...) and other files ("file:modid/path")
        String configId = handle instanceof ExternalConfig external
                ? fr.heetstudio.confee.api.client.ConfeeWidgets.fileId(schema.modId(), external.relativePath()) : schema.id();
        java.util.function.Supplier<?> factory = option.widgetFactory();
        if (factory == null) {
            factory = fr.heetstudio.confee.api.client.ConfeeWidgets.lookup(configId, schema.modId(), option.path(), valueClass(option));
        }
        if (factory == null) {
            return null;
        }
        try {
            Object widget = factory.get();
            if (widget instanceof fr.heetstudio.confee.api.client.OptionWidget<?> optionWidget) {
                return new CustomWidgetAdapter(ui, session, option, name, optionWidget, w -> reveal = w);
            }
            fr.heetstudio.confee.ConfeeLog.warn("Widget of option '{}' is not an OptionWidget: {}", option.path(), widget);
        } catch (RuntimeException | LinkageError e) {
            fr.heetstudio.confee.ConfeeLog.error("Cannot create the widget of option '" + option.path() + "'",
                    e instanceof RuntimeException re ? re : new RuntimeException(e));
        }
        return null;
    }

    @Nullable
    private static Class<?> valueClass(Option<?> option) {
        return switch (option.type()) {
            case BOOLEAN -> Boolean.class;
            case INT, COLOR -> Integer.class;
            case LONG -> Long.class;
            case FLOAT -> Float.class;
            case DOUBLE -> Double.class;
            case STRING, IDENTIFIER -> String.class;
            case ENUM -> option.enumClass();
            case LIST, OBJECT_LIST -> List.class;
            case MAP -> java.util.Map.class;
        };
    }

    /** Number field with − / + buttons; the value is clamped to the bounds. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ConfeeWidget stepper(Option<?> option, Component name) {
        TextFieldWidget field = (TextFieldWidget) field(option, name);
        Option raw = option;
        double step = option.step() != null ? option.step().doubleValue() : 1;
        return new StepperWidget(ui, field, steps -> {
            double current = ((Number) session.get(raw)).doubleValue();
            double next = current + steps * step;
            if (option.min() != null) {
                next = Math.max(option.min().doubleValue(), next);
            }
            if (option.max() != null) {
                next = Math.min(option.max().doubleValue(), next);
            }
            Object value = switch (option.type()) {
                case INT -> (Object) (int) Math.round(next);
                case LONG -> (Object) Math.round(next);
                case FLOAT -> (Object) (float) next;
                default -> (Object) next;
            };
            session.set(raw, value);
            field.setTextSilently(ValueText.toFieldText(option, value));
            field.setResponder(text -> {
                ValueText.Parsed parsed = ValueText.parse(option, text);
                if (parsed.ok()) {
                    field.setError(null);
                    session.set(raw, parsed.value());
                } else {
                    field.setError(problemText(option, parsed));
                }
            });
            field.setError(null);
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ConfeeWidget choice(Option<?> option, Component name) {
        List<Object> values = option.allowedValues() != null ? new ArrayList<>(option.allowedValues())
                : new ArrayList<>(Arrays.asList(option.enumClass().getEnumConstants()));
        Option raw = option;
        if (values.size() >= 2 && values.size() <= 4 && option.widgetHint() != WidgetHint.DROPDOWN) {
            return new SegmentedWidget<>(ui, name, values, Labels::enumValue, () -> session.get(raw), v -> session.set(raw, v));
        }
        return new DropdownWidget<>(ui, name, values, Labels::enumValue, () -> session.get(raw), v -> session.set(raw, v),
                this::openDropdown);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ConfeeWidget slider(Option<?> option, Component name) {
        Option raw = option;
        double step = option.step() != null ? option.step().doubleValue() : 0;
        return new SliderWidget(ui, option.min().doubleValue(), option.max().doubleValue(), step,
                () -> ((Number) session.get(raw)).doubleValue(),
                v -> session.set(raw, switch (option.type()) {
                    case INT -> (Object) (int) Math.round(v);
                    case LONG -> (Object) Math.round(v);
                    case FLOAT -> (Object) (float) v;
                    default -> (Object) v;
                }),
                () -> Component.translatable("confee.slider.label", name,
                        Component.literal(ValueText.number(session.get(raw)))));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ConfeeWidget multiline(Option<?> option, Component name) {
        Option raw = option;
        int maxLength = option.maxLength() > 0 ? option.maxLength() : Integer.MAX_VALUE;
        MultilineFieldWidget[] holder = new MultilineFieldWidget[1];
        holder[0] = new MultilineFieldWidget(ui, name, String.valueOf(session.get(raw)), maxLength, text -> {
            ValueText.Parsed parsed = ValueText.parse(option, text);
            holder[0].setError(parsed.ok() ? null : problemText(option, parsed));
            if (parsed.ok()) {
                session.set(raw, parsed.value());
            }
        });
        return holder[0];
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ConfeeWidget field(Option<?> option, Component name) {
        Option raw = option;
        OptionType type = option.type();
        java.util.function.Predicate<String> filter = switch (type) {
            case INT, LONG -> s -> ValueText.isNumberText(s, false);
            case FLOAT, DOUBLE -> s -> ValueText.isNumberText(s, true);
            default -> null;
        };
        int maxLength = option.maxLength() > 0 ? option.maxLength() : Integer.MAX_VALUE;
        TextFieldWidget[] holder = new TextFieldWidget[1];
        // filters of the option (@Entity(only = ...)): a value outside them is an error on the field
        java.util.function.Predicate<String> accepted = fr.heetstudio.confee.ui.RegistryFilters.of(option);
        TextFieldWidget field = new TextFieldWidget(ui, name, ValueText.toFieldText(option, session.get(raw)), maxLength, filter, text -> {
            ValueText.Parsed parsed = ValueText.parse(option, text);
            if (parsed.ok() && accepted != null && !text.isBlank() && !text.trim().startsWith("#") && !accepted.test(text)) {
                holder[0].setError(Component.translatable("confee.error.filtered", text.trim()));
            } else if (parsed.ok()) {
                holder[0].setError(null);
                session.set(raw, parsed.value());
                if (type == OptionType.COLOR) {
                    holder[0].setSwatch((Integer) parsed.value());
                }
            } else {
                holder[0].setError(problemText(option, parsed));
            }
        });
        holder[0] = field;
        if (type == OptionType.COLOR) {
            field.setSwatch((Integer) session.get(raw));
            field.setSwatchAction(() -> openOverlay(new ColorPickerPopup(ui, name, (Integer) session.get(raw),
                    option.colorAlpha(), argb -> {
                        session.set(raw, argb);
                        field.setTextSilently(ValueText.toFieldText(option, argb));
                        field.setSwatch(argb);
                        field.setError(null);
                    }, this::closeOverlay)));
        }
        if (type == OptionType.STRING && schema.source() != ConfigSource.CONFEE && !option.readOnly()
                && colorOf((String) session.get(raw)) != null) {
            // spec 8.3: color-looking strings of other mods get a preview and the color picker
            field.setSwatch(colorOf((String) session.get(raw)));
            field.setResponder(text -> {
                ValueText.Parsed parsed = ValueText.parse(option, text);
                if (parsed.ok()) {
                    session.set(raw, parsed.value());
                }
                field.setSwatch(colorOf(text));
            });
            field.setSwatchAction(() -> {
                String original = (String) session.get(raw);
                Integer argb = colorOf(original);
                boolean alpha = hexDigits(original) == 8;
                openOverlay(new ColorPickerPopup(ui, name, argb == null ? 0xFFFFFFFF : argb, alpha, picked -> {
                    String text = formatColorLike(original, picked, alpha);
                    session.set(raw, text);
                    field.setTextSilently(text);
                    field.setSwatch(picked);
                }, this::closeOverlay));
            });
        }
        if ((type == OptionType.INT || type == OptionType.LONG) && schema.source() != ConfigSource.CONFEE
                && !option.readOnly() && IntColors.isColorKey(option.key())
                && IntColors.argb(session.get(raw)) != null) {
            // spec 8.2 bis: integers named like a color (in range) get a preview and the color picker
            boolean alpha = IntColors.hasAlpha(session.get(raw));
            boolean signed = ((Number) session.get(raw)).longValue() < 0;
            field.setSwatch(IntColors.argb(session.get(raw)));
            field.setResponder(text -> {
                ValueText.Parsed parsed = ValueText.parse(option, text);
                if (parsed.ok()) {
                    field.setError(null);
                    session.set(raw, parsed.value());
                    field.setSwatch(IntColors.argb(parsed.value()));
                } else {
                    field.setError(problemText(option, parsed));
                }
            });
            field.setSwatchAction(() -> {
                Integer argb = IntColors.argb(session.get(raw));
                openOverlay(new ColorPickerPopup(ui, name, argb == null ? 0xFFFFFFFF : argb, alpha, picked -> {
                    Object value = IntColors.toValue(picked, alpha, signed, type == OptionType.LONG);
                    session.set(raw, value);
                    field.setTextSilently(String.valueOf(value));
                    field.setSwatch(picked);
                    field.setError(null);
                }, this::closeOverlay));
            });
        }
        Component unit = Labels.unit(option);
        if (unit != null) {
            field.setSuffix(unit);
        }
        fr.heetstudio.confee.ui.RegistryDetector.Detected detected = type == OptionType.IDENTIFIER || type == OptionType.STRING
                ? fr.heetstudio.confee.ui.RegistryDetector.of(schema, option, session.get(raw)) : null;
        if (type == OptionType.IDENTIFIER || type == OptionType.STRING) {
            field.setSuggester(Suggestions.forSources(detected == null ? null : detected.registry(),
                    detected != null && detected.tags(), option.suggestions(), option.path(), accepted));
            Component worldHint = Suggestions.worldHint(option);
            if (worldHint != null) {
                field.setHint(worldHint);
                field.setPersistentHint(worldHint);
            }
        }
        if (detected != null && fr.heetstudio.confee.ui.RegistryIcons.hasIcons(detected.registry())) {
            String registry = detected.registry();
            field.setIconRegistry(registry);
            field.setPickerAction(() -> openOverlay(new ItemPickerPopup(ui, name, registry, field.text(), java.util.Set.of(),
                    ItemPickerPopup.Styles.of(option, registry), field::setTextAndNotify, this::closeOverlay)),
                    Component.translatable("confee.tooltip.pick"));
        }
        if (option.readOnly()) {
            field.setEnabled(false);
            field.setDisabledReason(Component.translatable("confee.tooltip.read_only"));
        }
        return field;
    }

    private static int hexDigits(String text) {
        String t = text.trim();
        return t.startsWith("#") ? t.length() - 1 : t.regionMatches(true, 0, "0x", 0, 2) ? t.length() - 2 : 0;
    }

    /** The picked color written like the original text: same prefix ({@code #} / {@code 0x}), case and length. */
    private static String formatColorLike(String original, int argb, boolean alpha) {
        String t = original.trim();
        String prefix = t.startsWith("#") ? "#" : t.substring(0, 2);
        String hex = alpha ? String.format(Locale.ROOT, "%08X", argb) : String.format(Locale.ROOT, "%06X", argb & 0xFFFFFF);
        boolean lower = t.substring(prefix.length()).equals(t.substring(prefix.length()).toLowerCase(Locale.ROOT))
                && t.substring(prefix.length()).matches(".*[a-f].*");
        return prefix + (lower ? hex.toLowerCase(Locale.ROOT) : hex);
    }

    /** {@code #RRGGBB}, {@code #AARRGGBB}, {@code 0xRRGGBB} or {@code 0xAARRGGBB}, else null. */
    @Nullable
    private static Integer colorOf(String text) {
        String t = text.trim();
        String hex = t.startsWith("#") ? t.substring(1) : t.regionMatches(true, 0, "0x", 0, 2) ? t.substring(2) : null;
        if (hex == null || (hex.length() != 6 && hex.length() != 8) || !hex.matches("[0-9a-fA-F]+")) {
            return null;
        }
        long v = Long.parseLong(hex, 16);
        return hex.length() == 6 ? (int) (0xFF000000L | v) : (int) v;
    }

    private Component problemText(ValueText.Problem problem) {
        return problemText(null, problem);
    }

    private Component problemText(@Nullable Option<?> option, ValueText.Problem problem) {
        return switch (problem) {
            case NOT_A_NUMBER -> Component.translatable("confee.error.not_a_number");
            case NOT_AN_INTEGER -> Component.translatable("confee.error.not_an_integer");
            case OUT_OF_RANGE -> {
                if (option == null || (option.min() == null && option.max() == null)) {
                    yield Component.translatable("confee.error.out_of_range");
                }
                if (option.min() != null && option.max() != null) {
                    yield Component.translatable("confee.error.range", ValueText.number(option.min()), ValueText.number(option.max()));
                }
                yield option.min() != null ? Component.translatable("confee.error.min", ValueText.number(option.min()))
                        : Component.translatable("confee.error.max", ValueText.number(option.max()));
            }
            case PATTERN -> Component.translatable("confee.error.pattern");
            case MAX_LENGTH -> Component.translatable("confee.error.max_length", option == null ? 0 : option.maxLength());
            case IDENTIFIER -> Component.translatable("confee.error.identifier");
            case COLOR -> Component.translatable(option != null && option.colorAlpha() ? "confee.error.color_alpha" : "confee.error.color");
            case INVALID, REFUSED -> Component.translatable("confee.error.invalid");
        };
    }

    /** Problem of a field; a value refused by the mod's validator shows the mod's message. */
    private Component problemText(@Nullable Option<?> option, ValueText.Parsed parsed) {
        return parsed.reason() != null ? ValueText.reason(parsed.reason()) : problemText(option, parsed.problem());
    }

    private void focusLater(ConfeeWidget widget) {
        setFocus(widget);
    }

    /**
     * Internal (used by the test mod): focuses the control of an option shown in the current view.
     *
     * @return false when the option is not shown
     */
    /**
     * Shows the section at this dotted path (its category, then each sub-screen down to it), like clicking the
     * navigation rows. Used by the test mod's screenshot script.
     */
    public boolean openSection(String path) {
        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            String base = category.section().path();
            if (category.general() || !(path.equals(base) || path.startsWith(base + "."))) {
                continue;
            }
            List<Section> chain = new ArrayList<>();
            Section current = category.section();
            while (!current.path().equals(path)) {
                Section next = null;
                for (Node child : current.children()) {
                    if (child instanceof Section sub && (path.equals(sub.path()) || path.startsWith(sub.path() + "."))) {
                        next = sub;
                        break;
                    }
                }
                if (next == null) {
                    return false;
                }
                chain.add(next);
                current = next;
            }
            selected = i;
            nav.clear();
            nav.addAll(chain);
            scroll = 0;
            rebuildRows();
            return true;
        }
        return false;
    }

    /**
     * Runs "+ Add" ({@code add}), ▲ ({@code up}), ▼ ({@code down}) or ✕ ({@code remove}) of a list of objects / lists
     * like its buttons do. Used by the test mod's screenshot script.
     */
    public boolean runListAction(String listPath, String action, int index) {
        Section list = findSection(schema.root(), listPath);
        if (list == null || list.listActions() == null) {
            return false;
        }
        fr.heetstudio.confee.core.ListActions actions = list.listActions();
        switch (action) {
            case "add" -> changeList(() -> actions.add(session.pendingValues()));
            case "up" -> changeList(() -> actions.move(index, -1, session.pendingValues()));
            case "down" -> changeList(() -> actions.move(index, 1, session.pendingValues()));
            case "remove" -> changeList(() -> actions.remove(index, session.pendingValues()));
            default -> {
                return false;
            }
        }
        return true;
    }

    public boolean focusOption(String path) {
        for (Row row : rows) {
            if (row instanceof OptionRow optionRow && optionRow.option.path().equals(path) && !row.widgets().isEmpty()) {
                setFocus(row.widgets().get(0) instanceof fr.heetstudio.confee.ui.widget.IconButton && row.widgets().size() > 1
                        ? row.widgets().get(1) : row.widgets().get(0));
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ widgets list (focus order)

    @Override
    protected List<ConfeeWidget> widgets() {
        List<ConfeeWidget> list = new ArrayList<>();
        list.add(back);
        list.add(search);
        if (showSidebar) {
            list.addAll(categoryButtons);
        } else if (collapsed && categoryDropdown != null) {
            list.add(categoryDropdown);
        }
        for (Row row : rows) {
            list.addAll(row.widgets());
        }
        list.add(resetAll);
        list.add(cancel);
        list.add(save);
        return list;
    }

    @Override
    protected void onFocused(ConfeeWidget widget) {
        reveal = widget;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        Draw.rect(g, 0, 0, width, height, t.panelBgDeep());
        if (layout.background() != null) {
            int[] size = Textures.size(layout.background());
            net.minecraft.resources.Identifier bg = net.minecraft.resources.Identifier.tryParse(layout.background());
            if (size[0] > 0 && bg != null) {
                // tiled at its own size
                g.blit(RenderPipelines.GUI_TEXTURED, bg, 0, 0, 0, 0, width, height, size[0], size[1]);
            }
        }
        layoutRows();
        renderBody(g, mouseX, mouseY, delta);
        renderTitleBar(g, mouseX, mouseY, delta);
        renderBottomBar(g, mouseX, mouseY, delta);
    }

    private void layoutRows() {
        for (Row row : rows) {
            row.sync();
        }
        int viewH = viewport[3] - viewport[1];
        int rowW = panelW - 2 * PAD - 5;
        int y = viewport[1] + PAD;
        int total = 0;
        for (Row row : rows) {
            if (row.hidden()) {
                row.layout(panelX + PAD, -10_000, rowW);
                continue;
            }
            int h = row.layout(panelX + PAD, (int) (y - scroll), rowW);
            y += h + ROW_GAP;
            total += h + ROW_GAP;
        }
        contentH = total - ROW_GAP + 2 * PAD;
        double maxScroll = Math.max(0, contentH - viewH);
        if (reveal != null) {
            if (rows.stream().anyMatch(r -> r.widgets().contains(reveal))) {
                // only the focused part of a tall widget (a mod's widget may return a smaller area)
                int[] area = reveal.revealBounds();
                int top = area[1];
                int bottom = area[1] + area[3];
                if (top < viewport[1] + 4) {
                    scroll -= viewport[1] + 4 - top;
                } else if (bottom > viewport[3] - 4) {
                    // an area taller than the panel shows its top
                    scroll += Math.min(bottom - (viewport[3] - 4), top - (viewport[1] + 4));
                }
            }
            reveal = null;
            scroll = Math.max(0, Math.min(maxScroll, scroll));
            layoutRows();
            return;
        }
        if (scroll > maxScroll || scroll < 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll));
            layoutRows();
            return;
        }
        for (Row row : rows) {
            for (ConfeeWidget w : row.widgets()) {
                w.setClip(viewport);
            }
        }
    }

    private void renderBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        int top = TITLE_H;
        int bottom = height - BOTTOM_H;
        // panel
        Draw.rect(g, panelX, top, panelW, bottom - top, t.panelBg());
        if (collapsed && categoryDropdown != null) {
            categoryDropdown.setBounds(panelX + PAD, top + GAP, panelW - 2 * PAD, DropdownWidget.HEIGHT);
            categoryDropdown.render(g, mouseX, mouseY, delta);
            Draw.rect(g, panelX, viewport[1] - 1, panelW, 1, t.outline());
        }
        g.enableScissor(viewport[0], viewport[1], viewport[2], viewport[3]);
        boolean inside = mouseX >= viewport[0] && mouseX < viewport[2] && mouseY >= viewport[1] && mouseY < viewport[3];
        int mx = inside ? mouseX : -10_000;
        int my = inside ? mouseY : -10_000;
        for (Row row : rows) {
            if (!row.hidden() && row.y + row.height >= viewport[1] && row.y <= viewport[3]) {
                row.render(g, mx, my, delta);
            }
        }
        g.disableScissor();
        renderScrollbar(g);
        if (showPreview) {
            updatePreviewOption(mx, my);
            Object shown = dropdownPreview(); // may change previewOption: called first
            previewPanel.render(g, previewX, top, bottom, previewOption, shown, delta);
        }
        if (showSidebar) {
            renderSidebar(g, mouseX, mouseY, delta);
        }
    }

    /**
     * While the dropdown of an option is open, the preview shows the hovered item for a moment (nothing is chosen
     * until the click). Returns that item, or null.
     */
    @Nullable
    private Object dropdownPreview() {
        if (!(overlay instanceof DropdownPopup popup)) {
            return null;
        }
        for (Row row : rows) {
            if (row instanceof OptionRow optionRow && !row.hidden() && row.widgets().contains(popup.dropdown())
                    && java.util.Objects.equals(popup.dropdown().current(), session.get(optionRow.option()))) {
                // only a dropdown that edits the value itself (not one element of a list)
                previewOption = optionRow.option();
                return popup.highlightedValue();
            }
        }
        return null;
    }

    /** The hovered option, else the one holding the keyboard focus; kept while the mouse is elsewhere. */
    private void updatePreviewOption(int mx, int my) {
        for (Row row : rows) {
            if (row instanceof OptionRow optionRow && !row.hidden()
                    && mx >= row.x && mx < row.x + row.width && my >= row.y && my < row.y + row.height) {
                previewOption = optionRow.option();
                return;
            }
        }
        if (focus != null) {
            for (Row row : rows) {
                if (row instanceof OptionRow optionRow && row.widgets().contains(focus)) {
                    previewOption = optionRow.option();
                    return;
                }
            }
        }
        if (previewOption != null && rows.stream().noneMatch(r -> r instanceof OptionRow o && o.option() == previewOption)) {
            previewOption = null;
        }
    }

    private int[] scrollbarRect() {
        int viewH = viewport[3] - viewport[1];
        if (contentH <= viewH) {
            return null;
        }
        int trackX = panelX + panelW - 5;
        int handleH = Math.max(16, viewH * viewH / contentH);
        int handleY = viewport[1] + (int) Math.round((viewH - handleH) * scroll / (contentH - viewH));
        return new int[]{trackX, viewport[1], 3, viewH, handleY, handleH};
    }

    private void renderScrollbar(GuiGraphicsExtractor g) {
        int[] r = scrollbarRect();
        if (r == null) {
            return;
        }
        Theme t = ui.theme();
        Draw.rect(g, r[0], r[1], r[2], r[3], t.panelBgDeep());
        Draw.rect(g, r[0], r[4], r[2], r[5], draggingScrollbar ? t.buttonFaceHover() : t.buttonFace());
    }

    private void renderSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        int top = TITLE_H;
        int bottom = height - BOTTOM_H;
        Draw.rect(g, left, top, SIDEBAR_W, bottom - top, t.panelBg());
        int y = top + PAD - (int) sidebarScroll;
        g.enableScissor(left, top, left + SIDEBAR_W, bottom);
        Draw.textFit(g, ui.font(), Component.translatable("confee.screen.categories"), left + PAD, y, SIDEBAR_W - 2 * PAD, ui.secondaryText());
        y += 12;
        boolean inside = mouseY >= top && mouseY < bottom;
        for (int i = 0; i < categoryButtons.size(); i++) {
            ButtonWidget button = categoryButtons.get(i);
            button.setBounds(left + PAD, y, SIDEBAR_W - 2 * PAD, 20);
            button.setClip(new int[]{left, top, left + SIDEBAR_W, bottom});
            button.setSelected(i == selected && query.isBlank());
            button.render(g, inside ? mouseX : -10_000, mouseY, delta);
            y += 22;
        }
        g.disableScissor();
    }

    private void renderTitleBar(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        Draw.rect(g, 0, 0, width, TITLE_H - 2, t.titleBarBg());
        Draw.rect(g, 0, TITLE_H - 2, width, 1, t.toggleOffTrack());
        Draw.rect(g, 0, TITLE_H - 1, width, 1, t.outline());
        back.setBounds(3, 2, 16, 16);
        back.render(g, mouseX, mouseY, delta);
        int searchW = Math.max(70, Math.min(150, width / 4));
        search.setBounds(width - searchW - 4, 1, searchW, 18);
        search.render(g, mouseX, mouseY, delta);
        int titleX = 23;
        Draw.textFit(g, font, title, titleX, 6, search.x - 8 - titleX, t.titleBarText());
    }

    private void renderBottomBar(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme t = ui.theme();
        int top = height - BOTTOM_H;
        Draw.rect(g, 0, top, width, BOTTOM_H, t.panelBg());
        Draw.rect(g, 0, top, width, 1, t.outline());
        int by = top + 4;
        int x0 = left;
        int x1 = left + contentW;
        int saveW = Math.max(60, save.preferredWidth());
        int cancelW = Math.max(56, cancel.preferredWidth());
        int resetW = resetAll.preferredWidth();
        int needed = resetW + cancelW + saveW + 2 * 4 + 8;
        if (needed > x1 - x0) {
            // narrow window: share the room
            int each = (x1 - x0 - 8) / 3;
            resetW = cancelW = saveW = each;
        }
        resetAll.setBounds(x0, by, resetW, 20);
        save.setBounds(x1 - saveW, by, saveW, 20);
        cancel.setBounds(save.x - 4 - cancelW, by, cancelW, 20);
        save.setEnabled(!hasErrors());
        resetAll.setEnabled(hasEditableOptions);
        resetAll.render(g, mouseX, mouseY, delta);
        cancel.render(g, mouseX, mouseY, delta);
        save.render(g, mouseX, mouseY, delta);
    }

    private boolean hasErrors() {
        for (Row row : rows) {
            if (row.hasError()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected int[] tooltipRegion(@Nullable int[] anchor) {
        if (anchor != null && anchor[0] >= panelX && anchor[0] < panelX + panelW && anchor[1] >= viewport[1] && anchor[1] < viewport[3]) {
            // tooltips of the options stay over the option panel (not over the preview column or the sidebar)
            return new int[]{panelX, TITLE_H, panelX + panelW, height - BOTTOM_H};
        }
        return super.tooltipRegion(anchor);
    }

    @Override
    @Nullable
    protected TooltipTarget tooltipTarget(double mx, double my) {
        if (mx < viewport[0] || mx >= viewport[2] || my < viewport[1] || my >= viewport[3]) {
            return null;
        }
        for (Row row : rows) {
            TooltipTarget target = row.tooltip(mx, my);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean scrolled(double mx, double my, double delta) {
        if (showSidebar && mx >= left && mx < left + SIDEBAR_W) {
            int top = TITLE_H;
            int listH = 12 + categoryButtons.size() * 22 + 2 * PAD;
            double max = Math.max(0, listH - (height - BOTTOM_H - top));
            sidebarScroll = Math.max(0, Math.min(max, sidebarScroll - delta * SCROLL_STEP));
            return true;
        }
        scroll -= delta * SCROLL_STEP;
        return true;
    }

    @Override
    protected boolean mouseClickedOutside(double mx, double my, int button) {
        int[] r = scrollbarRect();
        if (r != null && mx >= r[0] - 2 && mx < r[0] + r[2] + 2 && my >= r[1] && my < r[1] + r[3]) {
            draggingScrollbar = true;
            scrollTo(my, r);
            return true;
        }
        return false;
    }

    @Override
    protected boolean mouseDraggedOutside(double mx, double my, int button, double dx, double dy) {
        int[] r = scrollbarRect();
        if (draggingScrollbar && r != null) {
            scrollTo(my, r);
            return true;
        }
        return false;
    }

    @Override
    protected boolean mouseReleasedOutside(double mx, double my, int button) {
        boolean was = draggingScrollbar;
        draggingScrollbar = false;
        return was;
    }

    private void scrollTo(double my, int[] r) {
        int viewH = viewport[3] - viewport[1];
        double f = (my - r[1] - r[5] / 2.0) / (r[3] - r[5]);
        scroll = Math.max(0, Math.min(1, f)) * (contentH - viewH);
    }

    @Override
    protected boolean shortcut(int key, int modifiers, boolean typing) {
        boolean ctrl = (modifiers & InputConstants.MOD_CONTROL) != 0;
        boolean shift = (modifiers & InputConstants.MOD_SHIFT) != 0;
        if (categories.size() >= 2) {
            // spec 10.3: [ ] or Ctrl+Tab switch categories
            if ((ctrl && key == InputConstants.KEY_TAB) || (!typing && (key == InputConstants.KEY_LBRACKET || key == InputConstants.KEY_RBRACKET))) {
                int direction = key == InputConstants.KEY_LBRACKET || (key == InputConstants.KEY_TAB && shift) ? -1 : 1;
                ui.click();
                selectCategory(Math.floorMod(selected + direction, categories.size()));
                return true;
            }
        }
        if (ctrl && key == InputConstants.KEY_F) {
            setFocus(search);
            return true;
        }
        if (!typing && (key == InputConstants.KEY_PAGEDOWN || key == InputConstants.KEY_PAGEUP)) {
            scroll += (key == InputConstants.KEY_PAGEDOWN ? 1 : -1) * (viewport[3] - viewport[1] - 20);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ actions

    /** Spec 10.4: Escape with unsaved changes asks first. */
    @Override
    protected void requestClose() {
        if (!session.isDirty()) {
            onClose();
            return;
        }
        Modal modal = new Modal(ui, Component.translatable("confee.modal.unsaved.title"),
                Component.translatable("confee.modal.unsaved.message"), this::closeOverlay);
        modal.button(Component.translatable("confee.button.save"), true, () -> {
            closeOverlay();
            if (hasErrors()) {
                return;
            }
            saveAndClose();
        });
        modal.button(Component.translatable("confee.button.dont_save"), false, () -> {
            closeOverlay();
            session.discard();
            onClose();
        });
        modal.button(Component.translatable("confee.button.cancel"), false, this::closeOverlay);
        openOverlay(modal);
    }

    private void confirmResetAll() {
        if (handle instanceof ExternalConfig external && external.hasBackup()) {
            // spec 8.3: restore the original copy of the whole file
            Modal restore = new Modal(ui, Component.translatable("confee.modal.restore_all.title"),
                    Component.translatable("confee.modal.restore_all.message", external.relativePath()), this::closeOverlay);
            restore.button(Component.translatable("confee.button.restore"), true, () -> {
                closeOverlay();
                if (external.restoreOriginal()) {
                    minecraft.gui.setScreen(new ConfigScreen(parent, external));
                }
            });
            restore.button(Component.translatable("confee.button.cancel"), false, this::closeOverlay);
            openOverlay(restore);
            return;
        }
        Modal modal = new Modal(ui, Component.translatable("confee.modal.reset_all.title"),
                Component.translatable("confee.modal.reset_all.message", title), this::closeOverlay);
        modal.button(Component.translatable("confee.button.reset"), true, () -> {
            closeOverlay();
            session.resetAllToDefault();
        });
        modal.button(Component.translatable("confee.button.cancel"), false, this::closeOverlay);
        openOverlay(modal);
    }

    /** Spec 10.4: validate, save, close; then tell the player when a restart is needed. */
    private void saveAndClose() {
        if (hasErrors()) {
            return;
        }
        if (session.isDirty() && handle.changedOnDisk()) {
            // spec 14: the file changed elsewhere while the screen was open
            Modal modal = new Modal(ui, Component.translatable("confee.modal.changed_on_disk.title"),
                    Component.translatable("confee.modal.changed_on_disk.message"), this::closeOverlay);
            modal.button(Component.translatable("confee.button.overwrite"), true, () -> {
                closeOverlay();
                commitAndClose();
            });
            modal.button(Component.translatable("confee.button.cancel"), false, this::closeOverlay);
            openOverlay(modal);
            return;
        }
        commitAndClose();
    }

    private void commitAndClose() {
        EditSession.Result result = session.commit();
        if (result.restartRequired()) {
            Modal modal = new Modal(ui, Component.translatable("confee.modal.restart.title"),
                    Component.translatable("confee.modal.restart.message"), this::onClose);
            modal.button(Component.translatable("confee.button.ok"), true, this::onClose);
            openOverlay(modal);
            return;
        }
        onClose();
    }

    /** Closing without saving ("Cancel", Escape then "Don't save") forgets the list changes kept in memory. */
    @Override
    public void onClose() {
        session.discard();
        super.onClose();
    }

    @Override
    public void resize(int width, int height) {
        ConfeeWidget focused = focus;
        closeOverlay();
        super.resize(width, height);
        if (focused == search) {
            setFocus(search);
        }
    }
}

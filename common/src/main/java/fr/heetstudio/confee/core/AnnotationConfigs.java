package fr.heetstudio.confee.core;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.api.annotation.Category;
import fr.heetstudio.confee.api.annotation.Color;
import fr.heetstudio.confee.api.annotation.Comment;
import fr.heetstudio.confee.api.annotation.Config;
import fr.heetstudio.confee.api.annotation.Hidden;
import fr.heetstudio.confee.api.annotation.Key;
import fr.heetstudio.confee.api.annotation.MaxLength;
import fr.heetstudio.confee.api.annotation.Name;
import fr.heetstudio.confee.api.annotation.Pattern;
import fr.heetstudio.confee.api.annotation.Range;
import fr.heetstudio.confee.api.annotation.RequiresRestart;
import fr.heetstudio.confee.api.annotation.Slider;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import fr.heetstudio.confee.ConfeeLog;

/**
 * Builds a {@link ConfigSchema} from a class annotated with {@link Config} (spec 6.1).
 * <p>Declaration mistakes throw in a development environment and are logged and skipped in production (spec 14).</p>
 * <p>Internal: may change without notice.</p>
 */
public final class AnnotationConfigs {

    private static final String RESOURCE_LOCATION = "net.minecraft.resources.ResourceLocation";

    /** Typed shortcuts → registry. */
    private static final Map<Class<? extends java.lang.annotation.Annotation>, String> REGISTRY_SHORTCUTS = Map.ofEntries(
            Map.entry(fr.heetstudio.confee.api.annotation.Item.class, "minecraft:item"),
            Map.entry(fr.heetstudio.confee.api.annotation.Block.class, "minecraft:block"),
            Map.entry(fr.heetstudio.confee.api.annotation.Entity.class, "minecraft:entity_type"),
            Map.entry(fr.heetstudio.confee.api.annotation.Biome.class, "minecraft:worldgen/biome"),
            Map.entry(fr.heetstudio.confee.api.annotation.Dimension.class, "minecraft:dimension"),
            Map.entry(fr.heetstudio.confee.api.annotation.Structure.class, "minecraft:worldgen/structure"),
            Map.entry(fr.heetstudio.confee.api.annotation.Enchantment.class, "minecraft:enchantment"),
            Map.entry(fr.heetstudio.confee.api.annotation.Effect.class, "minecraft:mob_effect"),
            Map.entry(fr.heetstudio.confee.api.annotation.Sound.class, "minecraft:sound_event"),
            Map.entry(fr.heetstudio.confee.api.annotation.Fluid.class, "minecraft:fluid"),
            Map.entry(fr.heetstudio.confee.api.annotation.ModId.class, PseudoRegistries.MOD));
    private static final Map<Class<?>, OptionType> LIST_ELEMENTS = Map.of(
            String.class, OptionType.STRING,
            Integer.class, OptionType.INT,
            Long.class, OptionType.LONG,
            Float.class, OptionType.FLOAT,
            Double.class, OptionType.DOUBLE,
            Boolean.class, OptionType.BOOLEAN);

    private AnnotationConfigs() {
    }

    public static <T> ConfigHandle<T> create(Class<T> type) {
        return create(type, ConfeeEnvironment.current());
    }

    public static <T> ConfigHandle<T> create(Class<T> type, ConfeeEnvironment environment) {
        Config config = type.getAnnotation(Config.class);
        if (config == null) {
            throw new IllegalArgumentException("[Confee] " + type.getName() + " is not annotated with @Config");
        }
        T instance = instantiate(type);
        Comment classComment = type.getAnnotation(Comment.class);
        Section root = Section.root(classComment == null ? null : classComment.value());
        Walker walker = new Walker(environment.development(), type.getName(), new ArrayList<>());
        walker.walk(type, instance, root, true);
        if (!walker.errors().isEmpty()) {
            // spec 14: every mistake at once, with how to fix it
            throw new IllegalStateException("[Confee] " + walker.errors().size() + " problem(s) in config class "
                    + type.getName() + ":\n  - " + String.join("\n  - ", walker.errors()));
        }
        validateConditions(root, environment.development(), type.getName());

        String name = config.name().isEmpty() ? null : config.name();
        ConfigSchema schema = new ConfigSchema(config.modId(), name, config.scope(), config.format(),
                ConfigHandle.filePath(environment, config.modId(), name, config.format()),
                theme(type.getAnnotation(fr.heetstudio.confee.api.annotation.Theme.class)),
                ConfigSource.CONFEE, root, layout(type.getAnnotation(fr.heetstudio.confee.api.annotation.Layout.class)))
                .withFileOptions(config.writeDefaults(), List.of(config.legacyFiles()));
        return new ConfigHandle<>(schema, instance, ConfigHandle.isActive(environment, config.scope()));
    }

    /** Spec 14: a condition on an unknown option is an error in development, a warning in production. */
    public static void validateConditions(Section root, boolean strict, String where) {
        try {
            Conditions.validate(root);
        } catch (IllegalStateException e) {
            if (strict) {
                throw new IllegalStateException(e.getMessage() + " (in " + where + ")", e);
            }
            ConfeeLog.warn("{} (in {}); these conditions are ignored", e.getMessage(), where);
        }
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Confee] " + type.getName() + " needs a constructor without arguments", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("[Confee] Cannot create an instance of " + type.getName(), e);
        }
    }

    /** {@code @Layout} → model (defaults when absent). */
    static fr.heetstudio.confee.api.Layout layout(@Nullable fr.heetstudio.confee.api.annotation.Layout l) {
        fr.heetstudio.confee.api.Layout.Builder b = fr.heetstudio.confee.api.Layout.builder();
        if (l == null) {
            return b.build();
        }
        if (l.panelWidth() != 0) b.panelWidth(l.panelWidth());
        if (l.previewWidth() != 0) b.previewWidth(l.previewWidth());
        b.sidebar(l.sidebar());
        if (!l.banner().isBlank()) b.banner(l.banner(), l.bannerHeight());
        if (!l.background().isBlank()) b.background(l.background());
        return b.build();
    }

    /** @param t the class annotation, or null */
    static Theme theme(fr.heetstudio.confee.api.annotation.Theme t) {
        Theme.Builder b = Theme.builder();
        if (t == null) {
            return b.build();
        }
        int unset = fr.heetstudio.confee.api.annotation.Theme.UNSET;
        if (t.accent() != unset) b.accent(t.accent());
        if (t.accentDark() != unset) b.accentDark(t.accentDark());
        if (t.accentLight() != unset) b.accentLight(t.accentLight());
        if (t.onAccentText() != unset) b.onAccentText(t.onAccentText());
        if (t.panelBg() != unset) b.panelBg(t.panelBg());
        if (t.panelBgDeep() != unset) b.panelBgDeep(t.panelBgDeep());
        if (t.buttonFace() != unset) b.buttonFace(t.buttonFace());
        if (t.buttonFaceHover() != unset) b.buttonFaceHover(t.buttonFaceHover());
        if (t.buttonShadow() != unset) b.buttonShadow(t.buttonShadow());
        if (t.outline() != unset) b.outline(t.outline());
        if (t.textOnDark() != unset) b.textOnDark(t.textOnDark());
        if (t.textOnLight() != unset) b.textOnLight(t.textOnLight());
        if (t.textDisabled() != unset) b.textDisabled(t.textDisabled());
        if (t.warning() != unset) b.warning(t.warning());
        if (t.error() != unset) b.error(t.error());
        if (t.toggleOffTrack() != unset) b.toggleOffTrack(t.toggleOffTrack());
        if (t.titleBarBg() != unset) b.titleBarBg(t.titleBarBg());
        if (t.titleBarText() != unset) b.titleBarText(t.titleBarText());
        return b.build();
    }

    /** Walks the fields of a config class (and of its {@code @Section} classes). */
    private record Walker(boolean strict, String rootClass, List<String> errors) {

        void walk(Class<?> type, Object instance, Section target, boolean allowCategories) {
            for (Field field : type.getDeclaredFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || !Modifier.isPublic(mods)
                        || field.isSynthetic()) {
                    continue;
                }
                try {
                    Section destination = destination(field, target, allowCategories);
                    if (field.getType() == fr.heetstudio.confee.api.Decoration.class) {
                        Object decoration = get(field, instance);
                        if (decoration == null) {
                            throw new IllegalArgumentException("the Decoration field has no value. Fix: initialize it, e.g. = Decoration.separator()");
                        }
                        destination.addDecoration((fr.heetstudio.confee.api.Decoration) decoration);
                        continue;
                    }
                    String key = key(field);
                    if (field.getType().isAnnotationPresent(fr.heetstudio.confee.api.annotation.Section.class)) {
                        addSubSection(field, instance, destination, key);
                    } else {
                        destination.add(option(field, instance, key));
                    }
                } catch (RuntimeException e) {
                    String where = field.getDeclaringClass().getSimpleName() + "." + field.getName();
                    if (strict) {
                        errors.add(where + ": " + e.getMessage());
                        continue;
                    }
                    ConfeeLog.warn("Ignoring config field {}: {}", rootClass + "/" + where, e.getMessage());
                }
            }
        }

        private Section destination(Field field, Section target, boolean allowCategories) {
            Category category = field.getAnnotation(Category.class);
            if (category == null) {
                return target;
            }
            if (!allowCategories) {
                throw new IllegalArgumentException("@Category is only allowed in the @Config class, not in a @Section");
            }
            String key = category.value();
            if (key.isBlank() || key.contains(".")) {
                throw new IllegalArgumentException("invalid category key '" + key + "'. Fix: a non-empty key without '.'");
            }
            Section section = target.find(key)
                    .map(node -> {
                        if (node instanceof Section s) {
                            return s;
                        }
                        throw new IllegalArgumentException("category '" + key + "' has the same key as an option. Fix: rename one of them");
                    })
                    .orElseGet(() -> target.add(new Section(key, null, null)));
            if (!category.icon().isBlank()) {
                if (section.icon() != null && !section.icon().equals(category.icon())) {
                    throw new IllegalArgumentException("category '" + key + "' has two icons ('" + section.icon() + "' and '"
                            + category.icon() + "'). Fix: set icon on one field only");
                }
                section.setIcon(category.icon());
            }
            return section;
        }

        private void addSubSection(Field field, Object instance, Section destination, String key) {
            Object nested = get(field, instance);
            if (nested == null) {
                nested = instantiate(field.getType());
                set(field, instance, nested);
            }
            Section section = destination.add(new Section(key, text(field.getAnnotation(Name.class)),
                    comment(field, field.getType())));
            fr.heetstudio.confee.api.annotation.Icon icon = field.getAnnotation(fr.heetstudio.confee.api.annotation.Icon.class);
            if (icon == null) {
                icon = field.getType().getAnnotation(fr.heetstudio.confee.api.annotation.Icon.class);
            }
            if (icon != null) {
                section.setIcon(icon.value());
            }
            // @Preview on the field holding the section, or on its class: shown for its options without one
            fr.heetstudio.confee.api.annotation.Preview sectionPreview = field.getAnnotation(fr.heetstudio.confee.api.annotation.Preview.class);
            if (sectionPreview == null) {
                sectionPreview = field.getType().getAnnotation(fr.heetstudio.confee.api.annotation.Preview.class);
            }
            if (sectionPreview != null) {
                section.setPreview(preview(sectionPreview));
            }
            walk(field.getType(), nested, section, false);
        }

        private static String key(Field field) {
            Key key = field.getAnnotation(Key.class);
            String value = key == null ? field.getName() : key.value();
            if (value.isBlank() || value.contains(".")) {
                throw new IllegalArgumentException("invalid key '" + value + "'. Fix: a non-empty @Key without '.'");
            }
            return value;
        }

        @Nullable
        private static String comment(Field field, @Nullable Class<?> sectionType) {
            Comment comment = field.getAnnotation(Comment.class);
            if (comment == null && sectionType != null) {
                comment = sectionType.getAnnotation(Comment.class);
            }
            return comment == null ? null : comment.value();
        }

        @Nullable
        private static String text(@Nullable Name name) {
            return name == null ? null : name.value();
        }
    }

    // ------------------------------------------------------------------ options

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Option<?> option(Field field, Object instance, String key) {
        Class<?> type = field.getType();
        OptionType optionType;
        Class<? extends Enum<?>> enumClass = null;
        OptionType elementType = null;
        boolean identifier = false;
        boolean enumSet = false;
        Class<?> objectClass = null;

        Color color = field.getAnnotation(Color.class);
        if (color != null && type != int.class && type != Integer.class) {
            throw new IllegalArgumentException("@Color is only allowed on int fields");
        }
        if (type == boolean.class || type == Boolean.class) {
            optionType = OptionType.BOOLEAN;
        } else if (type == int.class || type == Integer.class) {
            optionType = color != null ? OptionType.COLOR : OptionType.INT;
        } else if (type == long.class || type == Long.class) {
            optionType = OptionType.LONG;
        } else if (type == float.class || type == Float.class) {
            optionType = OptionType.FLOAT;
        } else if (type == double.class || type == Double.class) {
            optionType = OptionType.DOUBLE;
        } else if (type == String.class) {
            optionType = OptionType.STRING;
        } else if (type.isEnum()) {
            optionType = OptionType.ENUM;
            enumClass = (Class<? extends Enum<?>>) type;
        } else if (type == List.class) {
            Class<?> element = typeArgument(field, 0);
            if (element.isAnnotationPresent(fr.heetstudio.confee.api.annotation.Section.class)) {
                optionType = OptionType.OBJECT_LIST;
                objectClass = element;
            } else if (element.isEnum()) {
                optionType = OptionType.LIST;
                elementType = OptionType.ENUM;
                enumClass = (Class<? extends Enum<?>>) element;
            } else {
                optionType = OptionType.LIST;
                elementType = listElementType(field);
            }
        } else if (type == java.util.Set.class || type == java.util.EnumSet.class) {
            Class<?> element = typeArgument(field, 0);
            if (!element.isEnum()) {
                throw new IllegalArgumentException("a Set must hold enum values (check boxes). Fix: use List<" + element.getSimpleName() + "> for a list");
            }
            optionType = OptionType.LIST;
            elementType = OptionType.ENUM;
            enumClass = (Class<? extends Enum<?>>) element;
            enumSet = true;
        } else if (type == Map.class) {
            if (typeArgument(field, 0) != String.class) {
                throw new IllegalArgumentException("map keys must be String");
            }
            Class<?> value = typeArgument(field, 1);
            optionType = OptionType.MAP;
            if (value.isEnum()) {
                elementType = OptionType.ENUM;
                enumClass = (Class<? extends Enum<?>>) value;
            } else {
                elementType = LIST_ELEMENTS.get(value);
                if (elementType == null) {
                    throw new IllegalArgumentException("unsupported map value type " + value.getName());
                }
            }
        } else if (type.getName().equals(RESOURCE_LOCATION)) {
            optionType = OptionType.IDENTIFIER;
            identifier = true;
        } else {
            throw new IllegalArgumentException("unsupported type " + type.getName()
                    + ". Fix: use boolean, int, long, float, double, String, an enum, ResourceLocation, List, Set<Enum>, "
                    + "Map<String, V>, a @Section class or List<@Section class>");
        }

        List<ObjectField> objectFields = objectClass == null ? null : ObjectLists.fields(objectClass);
        ValueStore store = identifier ? new IdentifierFieldStore(field, instance)
                : objectClass != null ? new ObjectListFieldStore(field, instance, objectClass, objectFields)
                : enumSet ? new EnumSetFieldStore(field, instance, (Class) enumClass)
                : new FieldStore(field, instance);
        Object initial = store.get();
        if (initial == null) {
            throw new IllegalArgumentException("the field has no initial value (it is the default value)");
        }
        if (optionType == OptionType.LIST) {
            initial = List.copyOf((java.util.Collection<?>) initial);
        }
        if (optionType == OptionType.MAP) {
            initial = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>((Map<?, ?>) initial));
        }

        Option.Builder builder = Option.builder(key, optionType)
                .defaultValue(initial)
                .store(store)
                .displayName(Walker.text(field.getAnnotation(Name.class)))
                .comment(Walker.comment(field, null))
                .requiresRestart(field.isAnnotationPresent(RequiresRestart.class))
                .hidden(field.isAnnotationPresent(Hidden.class));
        if (enumClass != null) {
            builder.enumClass(enumClass);
        }
        if (elementType != null) {
            builder.elementType(elementType);
        }
        if (enumSet) {
            builder.widgetHint(WidgetHint.CHECKBOXES);
        }
        Range range = field.getAnnotation(Range.class);
        if (range != null) {
            builder.range(Double.isInfinite(range.min()) ? null : range.min(),
                    Double.isInfinite(range.max()) ? null : range.max());
        }
        Slider slider = field.getAnnotation(Slider.class);
        if (slider != null) {
            builder.widgetHint(WidgetHint.SLIDER).step(slider.step());
        }
        Pattern pattern = field.getAnnotation(Pattern.class);
        if (pattern != null) {
            builder.pattern(pattern.value());
        }
        fr.heetstudio.confee.api.annotation.Validator modValidator = field.getAnnotation(fr.heetstudio.confee.api.annotation.Validator.class);
        if (modValidator != null) {
            builder.modValidator(instantiate(modValidator.value()));
            if (modValidator.onFileError() == fr.heetstudio.confee.api.OnFileError.KEEP) {
                builder.keepRefused(true);
            }
        }
        fr.heetstudio.confee.api.annotation.ItemStack stack = field.getAnnotation(fr.heetstudio.confee.api.annotation.ItemStack.class);
        if (stack != null) {
            boolean stringList = field.getType() == List.class
                    && field.getGenericType() instanceof java.lang.reflect.ParameterizedType pt
                    && pt.getActualTypeArguments()[0] == String.class;
            if (field.getType() != String.class && !stringList) {
                throw new IllegalArgumentException("@ItemStack needs a String or a List<String> field");
            }
            if (stack.maxCount() < -1) {
                throw new IllegalArgumentException("@ItemStack(maxCount) must be -1 (no limit), 0 (stack size) or positive");
            }
            builder.itemStack(new Option.StackSpec(stack.count(), stack.components(), stack.maxCount()));
            if (stack.onFileError() == fr.heetstudio.confee.api.OnFileError.KEEP) {
                builder.keepRefused(true);
            }
        }
        MaxLength maxLength = field.getAnnotation(MaxLength.class);
        if (maxLength != null) {
            if (maxLength.value() <= 0) {
                throw new IllegalArgumentException("@MaxLength must be positive");
            }
            builder.maxLength(maxLength.value());
        }
        if (color != null) {
            builder.colorAlpha(color.alpha());
        }
        if (objectFields != null) {
            builder.objectFields(objectFields);
        }
        fr.heetstudio.confee.api.annotation.EnabledIf enabledIf = field.getAnnotation(fr.heetstudio.confee.api.annotation.EnabledIf.class);
        if (enabledIf != null) {
            builder.enabledIf(enabledIf.value());
        }
        fr.heetstudio.confee.api.annotation.VisibleIf visibleIf = field.getAnnotation(fr.heetstudio.confee.api.annotation.VisibleIf.class);
        if (visibleIf != null) {
            builder.visibleIf(visibleIf.value());
        }
        fr.heetstudio.confee.api.annotation.Unit unit = field.getAnnotation(fr.heetstudio.confee.api.annotation.Unit.class);
        if (unit != null) {
            builder.unit(unit.value());
        }
        if (field.isAnnotationPresent(fr.heetstudio.confee.api.annotation.AllowDuplicates.class)) {
            builder.allowDuplicates(true);
        }
        fr.heetstudio.confee.api.annotation.Size size = field.getAnnotation(fr.heetstudio.confee.api.annotation.Size.class);
        if (size != null) {
            builder.size(size.min(), size.max());
        }
        if (field.isAnnotationPresent(fr.heetstudio.confee.api.annotation.Reorderable.class)) {
            builder.reorderable(true);
        }
        if (field.isAnnotationPresent(fr.heetstudio.confee.api.annotation.Sorted.class)) {
            builder.sorted(true);
        }
        if (field.isAnnotationPresent(fr.heetstudio.confee.api.annotation.IgnoreCase.class)) {
            builder.ignoreCase(true);
        }

        if (field.isAnnotationPresent(fr.heetstudio.confee.api.annotation.Multiline.class)) {
            builder.widgetHint(WidgetHint.MULTILINE);
        }
        fr.heetstudio.confee.api.annotation.Registry registry = field.getAnnotation(fr.heetstudio.confee.api.annotation.Registry.class);
        List<String> registries = new ArrayList<>();
        boolean tags = false;
        if (registry != null) {
            registries.add("@Registry");
            builder.registry(registry.value());
            tags = registry.tags();
        }
        for (Map.Entry<Class<? extends java.lang.annotation.Annotation>, String> shortcut : REGISTRY_SHORTCUTS.entrySet()) {
            java.lang.annotation.Annotation a = field.getAnnotation(shortcut.getKey());
            if (a != null) {
                registries.add("@" + shortcut.getKey().getSimpleName());
                builder.registry(shortcut.getValue());
                tags |= shortcutTags(a);
                builder.filters(shortcutFilters(a));
            }
        }
        if (registries.size() > 1) {
            throw new IllegalArgumentException(String.join(", ", registries) + " cannot be used together. Fix: keep one");
        }
        if (tags) {
            if (identifier) {
                throw new IllegalArgumentException("tags (#namespace:path) do not fit in a ResourceLocation. Fix: use a String or List<String> field");
            }
            builder.allowTags(true);
        }
        fr.heetstudio.confee.api.annotation.Picker picker = field.getAnnotation(fr.heetstudio.confee.api.annotation.Picker.class);
        if (picker != null) {
            builder.pickerStyles(List.of(picker.styles()));
            builder.pickerDefault(picker.defaultStyle());
        }
        fr.heetstudio.confee.api.annotation.Preview preview = field.getAnnotation(fr.heetstudio.confee.api.annotation.Preview.class);
        if (preview != null) {
            builder.preview(preview(preview));
        }
        fr.heetstudio.confee.api.annotation.Description description = field.getAnnotation(fr.heetstudio.confee.api.annotation.Description.class);
        if (description != null) {
            builder.description(description.value());
        }
        fr.heetstudio.confee.api.annotation.Suggestions suggestions = field.getAnnotation(fr.heetstudio.confee.api.annotation.Suggestions.class);
        if (suggestions != null) {
            builder.suggestions(suggestions(suggestions));
            builder.suggestionSources(List.of(suggestions.sources()));
        }
        fr.heetstudio.confee.api.annotation.Stepper stepper = field.getAnnotation(fr.heetstudio.confee.api.annotation.Stepper.class);
        if (stepper != null) {
            if (slider != null) {
                throw new IllegalArgumentException("@Slider and @Stepper cannot be used together");
            }
            builder.widgetHint(WidgetHint.STEPPER).step(stepper.step());
        }
        if (field.isAnnotationPresent(fr.heetstudio.confee.api.annotation.Keybind.class)) {
            builder.widgetHint(WidgetHint.KEYBIND);
        }
        fr.heetstudio.confee.api.annotation.Widget widget = field.getAnnotation(fr.heetstudio.confee.api.annotation.Widget.class);
        if (widget != null) {
            Class<?> widgetClass = widget.value();
            builder.widgetFactory(() -> instantiate(widgetClass));
        }
        return builder.build();
    }

    /** {@code only = ...} of a registry shortcut ({@code @Entity(only = EntityFilter.MONSTER)}), empty when none. */
    private static List<Enum<?>> shortcutFilters(java.lang.annotation.Annotation a) {
        try {
            Object only = a.annotationType().getMethod("only").invoke(a);
            List<Enum<?>> out = new ArrayList<>();
            for (Object filter : (Object[]) only) {
                out.add((Enum<?>) filter);
            }
            return out;
        } catch (ReflectiveOperationException e) {
            return List.of(); // the shortcut has no filters (@Sound, @ModId...)
        }
    }

    private static boolean shortcutTags(java.lang.annotation.Annotation a) {
        try {
            return (Boolean) a.annotationType().getMethod("tags").invoke(a);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** {@code @Suggestions} → provider: fixed values first, then the provider's (created once, on first use). */
    static fr.heetstudio.confee.api.SuggestionProvider suggestions(fr.heetstudio.confee.api.annotation.Suggestions annotation) {
        List<String> fixed = List.of(annotation.value());
        Class<? extends fr.heetstudio.confee.api.SuggestionProvider> providerClass = annotation.provider();
        if (providerClass == fr.heetstudio.confee.api.SuggestionProvider.class) {
            return () -> fixed;
        }
        fr.heetstudio.confee.api.SuggestionProvider[] provider = new fr.heetstudio.confee.api.SuggestionProvider[1];
        return new fr.heetstudio.confee.api.SuggestionProvider() {
            private fr.heetstudio.confee.api.SuggestionProvider provider() {
                if (provider[0] == null) {
                    provider[0] = instantiate(providerClass);
                }
                return provider[0];
            }

            @Override
            public java.util.Collection<String> suggestions() {
                List<String> all = new ArrayList<>(fixed);
                all.addAll(provider().suggestions());
                return all;
            }

            @Override
            public boolean needsWorld() {
                return provider().needsWorld();
            }
        };
    }

    /** {@code @Preview} → model. The renderer is only instantiated on the client, when the preview is shown. */
    @Nullable
    static fr.heetstudio.confee.api.Preview preview(fr.heetstudio.confee.api.annotation.Preview annotation) {
        Class<? extends fr.heetstudio.confee.api.PreviewRenderer> rendererClass = annotation.renderer();
        boolean hasRenderer = rendererClass != fr.heetstudio.confee.api.PreviewRenderer.class;
        return fr.heetstudio.confee.api.Preview.of(annotation.image(), annotation.text(),
                hasRenderer ? () -> instantiate(rendererClass) : null);
    }

    private static Class<?> typeArgument(Field field, int index) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments()[index] instanceof Class<?> argument) {
            return argument;
        }
        throw new IllegalArgumentException("the field must declare its type arguments, e.g. List<String> or Map<String, Integer>");
    }

    private static OptionType listElementType(Field field) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
            OptionType type = LIST_ELEMENTS.get(element);
            if (type != null) {
                return type;
            }
            throw new IllegalArgumentException("unsupported list element type " + element.getName()
                    + " (use String, Integer, Long, Float, Double or Boolean)");
        }
        throw new IllegalArgumentException("a List field must declare its element type, e.g. List<String>");
    }

    @Nullable
    private static Object get(Field field, Object instance) {
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read field " + field.getName(), e);
        }
    }

    private static void set(Field field, Object instance, Object value) {
        try {
            field.setAccessible(true);
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot write field " + field.getName(), e);
        }
    }

    /** Reads and writes the value directly in the mod's config object. */
    private static class FieldStore implements ValueStore<Object> {
        final Field field;
        final Object instance;

        FieldStore(Field field, Object instance) {
            if (Modifier.isFinal(field.getModifiers())) {
                throw new IllegalArgumentException("config fields cannot be final");
            }
            this.field = field;
            this.instance = instance;
        }

        @Override
        public Object get() {
            return AnnotationConfigs.get(field, instance);
        }

        @Override
        public void set(Object value) {
            // collections are copied so the mod can modify its field without touching Confee's validated value
            Object copy = value instanceof List<?> list ? new ArrayList<>(list)
                    : value instanceof Map<?, ?> map ? new java.util.LinkedHashMap<>(map) : value;
            AnnotationConfigs.set(field, instance, copy);
        }
    }

    /** {@code Set<SomeEnum>} / {@code EnumSet} field ↔ ordered list of enum values. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class EnumSetFieldStore extends FieldStore {
        private final Class enumClass;

        EnumSetFieldStore(Field field, Object instance, Class<? extends Enum<?>> enumClass) {
            super(field, instance);
            this.enumClass = enumClass;
        }

        @Override
        public Object get() {
            Object value = super.get();
            return value == null ? null : ((java.util.Collection<?>) value).stream()
                    .sorted(java.util.Comparator.comparingInt(e -> ((Enum<?>) e).ordinal())).toList();
        }

        @Override
        public void set(Object value) {
            java.util.EnumSet set = java.util.EnumSet.noneOf(enumClass);
            set.addAll((List) value);
            AnnotationConfigs.set(field, instance, set);
        }
    }

    /** {@code List<SomeSection>} field ↔ list of maps of an OBJECT_LIST option. */
    private static final class ObjectListFieldStore extends FieldStore {
        private final Class<?> elementClass;
        private final List<ObjectField> fields;

        ObjectListFieldStore(Field field, Object instance, Class<?> elementClass, List<ObjectField> fields) {
            super(field, instance);
            this.elementClass = elementClass;
            this.fields = fields;
        }

        @Override
        public Object get() {
            Object value = super.get();
            return value == null ? null : ObjectLists.toMaps((List<?>) value, fields);
        }

        @Override
        public void set(Object value) {
            AnnotationConfigs.set(field, instance, ObjectLists.toObjects((List<?>) value, elementClass));
        }
    }

    /** ResourceLocation field ↔ String value of an IDENTIFIER option. */
    private static final class IdentifierFieldStore extends FieldStore {

        IdentifierFieldStore(Field field, Object instance) {
            super(field, instance);
        }

        @Override
        public Object get() {
            Object value = super.get();
            return value == null ? null : value.toString();
        }

        @Override
        public void set(Object value) {
            super.set(new net.minecraft.resources.ResourceLocation((String) value));
        }
    }
}

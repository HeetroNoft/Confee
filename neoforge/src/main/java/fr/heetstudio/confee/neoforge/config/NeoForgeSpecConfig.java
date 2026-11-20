package fr.heetstudio.confee.neoforge.config;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.api.ConfigFormat;
import fr.heetstudio.confee.api.ConfigScope;
import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.core.ConfigSchema;
import fr.heetstudio.confee.core.ConfigSource;
import fr.heetstudio.confee.core.EditableConfig;
import fr.heetstudio.confee.core.FileStamp;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.OptionType;
import fr.heetstudio.confee.core.Section;
import fr.heetstudio.confee.core.ValueStore;
import fr.heetstudio.confee.ui.ValueText;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.client.resources.language.I18n;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A Forge/NeoForge {@link ModConfigSpec} seen as an editable config (spec 8.4): keys, values, real defaults,
 * comments, translation keys, ranges and {@code needsWorldRestart} come from the spec. Saving goes through
 * {@link ModConfigSpec.ConfigValue#set} and {@link ModConfigSpec#save()}; Forge's file watcher then reloads the
 * config and fires the mod's reloading event (applied while the game runs). SERVER configs are read-only in V1.
 * Client side only.
 * <p>Internal: may change without notice.</p>
 */
public final class NeoForgeSpecConfig implements EditableConfig {

    private final ModConfig modConfig;
    private final ModConfigSpec spec;
    private final boolean readOnly;
    private final ConfigSchema schema;
    private final Map<Option<?>, Binding> bindings = new LinkedHashMap<>();
    private FileStamp stamp;

    private record Binding(ModConfigSpec.ConfigValue<Object> value, ModConfigSpec.ValueSpec spec) {
    }

    NeoForgeSpecConfig(ModConfig modConfig, ModConfigSpec spec, Path filePath) {
        this.modConfig = modConfig;
        this.spec = spec;
        this.readOnly = modConfig.getType() == ModConfig.Type.SERVER;
        Section root = Section.root(null);
        walk(spec.getSpec(), spec.getValues(), new ArrayList<>(), root);
        ConfigScope scope = switch (modConfig.getType()) {
            case CLIENT -> ConfigScope.CLIENT;
            // STARTUP (NeoForge 20.5+): a common config read once at launch
            case COMMON, STARTUP -> ConfigScope.COMMON;
            case SERVER -> ConfigScope.SERVER;
        };
        this.schema = new ConfigSchema(modConfig.getModId(), type(modConfig), scope, ConfigFormat.TOML, filePath,
                Theme.DEFAULT, ConfigSource.FORGE_SPEC, root);
        this.stamp = FileStamp.of(filePath);
    }

    static String type(ModConfig config) {
        return config.getType().name().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean readOnly() {
        return readOnly;
    }

    // ------------------------------------------------------------------ spec → model

    private void walk(UnmodifiableConfig specLevel, UnmodifiableConfig valueLevel, List<String> path, Section section) {
        for (Map.Entry<String, Object> entry : specLevel.valueMap().entrySet()) {
            String key = entry.getKey();
            List<String> childPath = new ArrayList<>(path);
            childPath.add(key);
            Object node = entry.getValue();
            Object valueNode = valueLevel.valueMap().get(key);
            try {
                if (node instanceof ModConfigSpec.ValueSpec valueSpec
                        && valueNode instanceof ModConfigSpec.ConfigValue<?> configValue) {
                    section.add(option(uniqueKey(section, key), valueSpec, cast(configValue)));
                } else if (node instanceof UnmodifiableConfig subSpec && valueNode instanceof UnmodifiableConfig subValues) {
                    String translationKey = spec.getLevelTranslationKey(childPath);
                    Section sub = new Section(uniqueKey(section, key), translated(translationKey, key),
                            spec.getLevelComment(childPath));
                    section.add(sub);
                    walk(subSpec, subValues, childPath, sub);
                }
            } catch (RuntimeException e) {
                // never crash because of another mod's spec: skip the entry (spec 14)
                ConfeeLog.warn("Skipping {} of {} config {}: {}", String.join(".", childPath), modConfig.getModId(),
                        modConfig.getFileName(), e.getMessage());
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Option<?> option(String key, ModConfigSpec.ValueSpec valueSpec, ModConfigSpec.ConfigValue<Object> configValue) {
        Object current = configValue.get();
        Object def = valueSpec.getDefault();
        OptionType type = typeOf(current != null ? current : def);
        Option.Builder builder = Option.builder(key, type == null ? OptionType.STRING : type);
        boolean editable = type != null && !readOnly;
        if (type == OptionType.LIST) {
            builder.allowDuplicates(true); // the Forge spec of the mod decides, not Confee
        }
        if (type == null) {
            current = String.valueOf(current);
            def = current;
        }
        if (type == OptionType.ENUM) {
            Class<? extends Enum> enumClass = ((Enum<?>) current).getDeclaringClass();
            builder.enumClass(enumClass);
            // values the spec accepts (defineEnum with a restricted list)
            List<Object> allowed = new ArrayList<>();
            for (Enum<?> constant : enumClass.getEnumConstants()) {
                if (valueSpec.test(constant)) {
                    allowed.add(constant);
                }
            }
            if (allowed.size() < enumClass.getEnumConstants().length && !allowed.isEmpty()) {
                builder.allowedValues(allowed);
            }
        }
        if (type == OptionType.LIST) {
            builder.elementType(elementType((List<?>) current, def));
            current = List.copyOf((List<?>) current);
            def = def instanceof List<?> list ? List.copyOf(list) : null;
        }
        Number[] bounds = type != null && type.isNumeric() ? bounds(valueSpec, type) : null;
        if (bounds != null) {
            builder.range(bounds[0], bounds[1]);
        }
        builder.displayName(translated(valueSpec.getTranslationKey(), key))
                .comment(valueSpec.getComment())
                .requiresRestart(valueSpec.needsWorldRestart())
                .readOnly(!editable)
                .validator(value -> valueSpec.test(value))
                .store(ValueStore.memory(current))
                .defaultValue(def);
        Option<?> option;
        try {
            option = builder.build();
        } catch (IllegalStateException e) {
            // default outside the declared bounds/type: keep the option without a default
            option = builder.defaultValue(null).build();
        }
        if (editable) {
            bindings.put(option, new Binding(configValue, valueSpec));
        }
        return option;
    }

    /**
     * Range of a numeric value. {@code ModConfigSpec.Range} is private in NeoForge, so the bounds are read through
     * the public {@code correct}: correcting an extreme value returns the bound.
     */
    @Nullable
    private static Number[] bounds(ModConfigSpec.ValueSpec valueSpec, OptionType type) {
        Object range = valueSpec.getRange();
        if (range == null) {
            return null;
        }
        Object low;
        Object high;
        switch (type) {
            case INT -> {
                low = valueSpec.correct(Integer.MIN_VALUE);
                high = valueSpec.correct(Integer.MAX_VALUE);
            }
            case LONG -> {
                low = valueSpec.correct(Long.MIN_VALUE);
                high = valueSpec.correct(Long.MAX_VALUE);
            }
            default -> {
                low = valueSpec.correct(-Double.MAX_VALUE);
                high = valueSpec.correct(Double.MAX_VALUE);
            }
        }
        if (low instanceof Number min && high instanceof Number max && min.doubleValue() <= max.doubleValue()) {
            return new Number[]{min, max};
        }
        return null;
    }

    @Nullable
    private static OptionType typeOf(@Nullable Object value) {
        if (value instanceof Boolean) {
            return OptionType.BOOLEAN;
        }
        if (value instanceof Integer) {
            return OptionType.INT;
        }
        if (value instanceof Long) {
            return OptionType.LONG;
        }
        if (value instanceof Double) {
            return OptionType.DOUBLE;
        }
        if (value instanceof String) {
            return OptionType.STRING;
        }
        if (value instanceof Enum<?>) {
            return OptionType.ENUM;
        }
        if (value instanceof List<?> list && elementTypeOrNull(list) != null) {
            return OptionType.LIST;
        }
        return null;
    }

    private static OptionType elementType(List<?> current, @Nullable Object def) {
        OptionType type = elementTypeOrNull(current);
        if (type == null && def instanceof List<?> list) {
            type = elementTypeOrNull(list);
        }
        return type == null ? OptionType.STRING : type;
    }

    /** Element type of a homogeneous list of scalars; STRING for an empty list; null otherwise. */
    @Nullable
    private static OptionType elementTypeOrNull(List<?> list) {
        if (list.isEmpty()) {
            return OptionType.STRING;
        }
        OptionType type = null;
        for (Object element : list) {
            OptionType t = element instanceof List<?> ? null : typeOf(element);
            if (t == null || t == OptionType.ENUM || (type != null && type != t)) {
                return null;
            }
            type = t;
        }
        return type;
    }

    private static String translated(@Nullable String translationKey, String key) {
        if (translationKey != null && I18n.exists(translationKey)) {
            return I18n.get(translationKey);
        }
        return ValueText.humanize(key);
    }

    private static String uniqueKey(Section parent, String key) {
        String base = key.isEmpty() ? "_" : key.replace('.', '_');
        String unique = base;
        int n = 2;
        while (parent.find(unique).isPresent()) {
            unique = base + "_" + n++;
        }
        return unique;
    }

    @SuppressWarnings("unchecked")
    private static ModConfigSpec.ConfigValue<Object> cast(ModConfigSpec.ConfigValue<?> value) {
        return (ModConfigSpec.ConfigValue<Object>) value;
    }

    // ------------------------------------------------------------------ EditableConfig

    @Override
    public ConfigSchema schema() {
        return schema;
    }

    /** Spec 8.4: {@code ConfigValue#set} then the spec saves; Forge's watcher applies the change while the game runs. */
    @Override
    public void save() {
        if (readOnly) {
            return;
        }
        boolean changed = false;
        for (Map.Entry<Option<?>, Binding> entry : bindings.entrySet()) {
            Object value = entry.getKey().get();
            Binding binding = entry.getValue();
            if (Objects.equals(value, binding.value().get())) {
                continue;
            }
            if (!binding.spec().test(value)) {
                ConfeeLog.warn("{} refused value {} for {}", modConfig.getModId(), value,
                        String.join(".", binding.value().getPath()));
                continue;
            }
            binding.value().set(value);
            changed = true;
        }
        if (changed) {
            spec.save();
            stamp = FileStamp.of(schema.filePath());
            ConfeeLog.info("Saved {} config {} through Forge", modConfig.getModId(), modConfig.getFileName());
            notifyMod();
        }
    }

    /**
     * Spec 8.4: applied while the game runs. NeoForge's file watcher does not react to a save made through the spec, so
     * Confee does what the watcher would do: refresh the spec caches and send the mod its ModConfigEvent.Reloading,
     * through FML's public API.
     */
    private void notifyMod() {
        try {
            spec.afterReload();
            net.neoforged.fml.config.IConfigEvent.reloading(modConfig).post();
        } catch (RuntimeException e) {
            // a broken listener of the other mod must not crash the game (spec 14)
            ConfeeLog.error("Reloading event of " + modConfig.getModId() + " failed", e);
        }
    }

    @Override
    public void fireOnChange() {
        // the mod is notified by notifyMod() right after the save
    }

    @Override
    public boolean changedOnDisk() {
        return !readOnly && !FileStamp.of(schema.filePath()).equals(stamp);
    }
}

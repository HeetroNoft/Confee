package fr.heetstudio.confee.ui;

import fr.heetstudio.confee.core.ConfigSchema;
import fr.heetstudio.confee.core.ConfigSource;
import fr.heetstudio.confee.core.Node;
import fr.heetstudio.confee.core.Section;
import fr.heetstudio.confee.platform.Services;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Display texts of options and categories (spec 13): translation key, then explicit name, then humanized key.
 * <p>Internal: may change without notice.</p>
 */
public final class Labels {

    private Labels() {
    }

    /** {@code config.<modId>.<configName>.<path>}, or {@code config.<modId>.<path>} for an unnamed config. */
    public static String translationKey(ConfigSchema schema, String path) {
        return "config." + schema.modId() + "." + (schema.name() == null ? "" : schema.name() + ".") + path;
    }

    public static Component name(ConfigSchema schema, Node node, String path) {
        String key = translationKey(schema, path);
        if (schema.source() != ConfigSource.RAW_FILE && net.minecraft.locale.Language.getInstance().has(key)) {
            return Component.translatable(key);
        }
        if (node.displayName() != null) {
            return Component.literal(node.displayName());
        }
        return Component.literal(ValueText.humanize(node.key()));
    }

    /** Translated {@code .tooltip}, else the comment, else null. */
    @Nullable
    public static Component description(ConfigSchema schema, Node node, String path) {
        String key = translationKey(schema, path) + ".tooltip";
        if (schema.source() != ConfigSource.RAW_FILE && net.minecraft.locale.Language.getInstance().has(key)) {
            return Component.translatable(key);
        }
        return node.comment() == null ? null : Component.literal(node.comment());
    }

    /**
     * Text shown under the name of an option: translation {@code <key>.description}, else {@code @Description}
     * (itself a translation key or plain text), else null.
     */
    @Nullable
    public static Component subtitle(ConfigSchema schema, fr.heetstudio.confee.core.Option<?> option) {
        if (schema.source() == ConfigSource.RAW_FILE) {
            return null;
        }
        String key = translationKey(schema, option.path()) + ".description";
        if (net.minecraft.locale.Language.getInstance().has(key)) {
            return Component.translatable(key);
        }
        return text(option.description());
    }

    /** A translation key when it exists, else the text itself ({@code §} codes work); null for null. */
    @Nullable
    public static Component text(@Nullable String text) {
        if (text == null) {
            return null;
        }
        return net.minecraft.locale.Language.getInstance().has(text) ? Component.translatable(text) : Component.literal(text);
    }

    public static Component section(ConfigSchema schema, Section section) {
        return name(schema, section, section.path());
    }

    /** Unit of a number ({@code chunks}): translated when it is a translation key. */
    @Nullable
    public static Component unit(fr.heetstudio.confee.core.Option<?> option) {
        String unit = option.unit();
        if (unit == null) {
            return null;
        }
        return net.minecraft.locale.Language.getInstance().has(unit) ? Component.translatable(unit) : Component.literal(unit);
    }

    public static Component enumValue(Object value) {
        return Component.literal(ValueText.humanize(value instanceof Enum<?> e ? e.name() : String.valueOf(value)));
    }

    public static Component modName(String modId) {
        return Component.literal(Services.PLATFORM.getModDisplayName(modId).orElse(modId));
    }

    /** Readable config name for titles and the file list ("Client", "Builder"...), or "Main" for an unnamed one. */
    public static Component configName(ConfigSchema schema) {
        return schema.name() == null ? Component.translatable("confee.file.main")
                : Component.literal(ValueText.humanize(schema.name()));
    }
}

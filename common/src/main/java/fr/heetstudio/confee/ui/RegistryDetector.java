package fr.heetstudio.confee.ui;

import fr.heetstudio.confee.core.ConfigSchema;
import fr.heetstudio.confee.core.ConfigSource;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.OptionType;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Universal editor and Forge configs (spec 10.8): guesses the registry of text values that look like IDs, so they
 * get the same suggestions, icons, picker and preview as {@code @Item} / {@code @Entity}... options. The key name
 * decides first ({@code blocks}, {@code biomeBlacklist}, {@code mobs}...), then the values themselves (every ID found
 * in one registry). Confee configs are never guessed: their mod declares what it wants.
 * <p>Internal: may change without notice.</p>
 */
public final class RegistryDetector {

    /** Result: the registry and whether the values contain tags. */
    public record Detected(String registry, boolean tags) {
    }

    static final Detected NONE = new Detected("", false);
    private static final Map<Option<?>, Detected> CACHE = new WeakHashMap<>();

    /** Key words → registry, checked in this order. */
    private static final String[][] KEY_HINTS = {
            {"biome", "minecraft:worldgen/biome"},
            {"dimension", "minecraft:dimension"},
            {"structure", "minecraft:worldgen/structure"},
            {"enchant", "minecraft:enchantment"},
            {"effect", "minecraft:mob_effect"},
            {"potion", "minecraft:mob_effect"},
            {"sound", "minecraft:sound_event"},
            {"fluid", "minecraft:fluid"},
            {"entit", "minecraft:entity_type"},
            {"mob", "minecraft:entity_type"},
            {"block", "minecraft:block"},
            {"item", "minecraft:item"},
    };

    /** Registries tried, in this order, when the key name says nothing. */
    private static final String[] BY_VALUE = {"minecraft:item", "minecraft:block", "minecraft:entity_type",
            "minecraft:enchantment", "minecraft:mob_effect"};

    private RegistryDetector() {
    }

    /**
     * Registry of the option: declared by the mod, else guessed for other mods' files and Forge configs; null when
     * the values do not look like IDs.
     */
    @Nullable
    public static Detected of(ConfigSchema schema, Option<?> option, @Nullable Object value) {
        if (option.registry() != null) {
            return new Detected(option.registry(), option.allowTags());
        }
        if (schema.source() == ConfigSource.CONFEE || option.readOnly() || !textual(option)) {
            return null;
        }
        Detected found = CACHE.computeIfAbsent(option, o -> detect(o.key(), value, RegistryEntries::ids));
        return found == NONE ? null : found;
    }

    private static boolean textual(Option<?> option) {
        return option.type() == OptionType.STRING || option.type() == OptionType.IDENTIFIER
                || (option.type() == OptionType.LIST && option.elementType() == OptionType.STRING);
    }

    /**
     * Guess from the key and the value.
     *
     * @param known sorted IDs of a registry (the game's registries; a fake in unit tests)
     * @return the registry, or {@link #NONE}
     */
    static Detected detect(String optionKey, @Nullable Object value, java.util.function.Function<String, List<String>> known) {
        List<String> values = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.forEach(element -> values.add(String.valueOf(element).trim()));
        } else if (value != null) {
            values.add(String.valueOf(value).trim());
        }
        // IDs to check (tags are not checked: they may come from datapacks)
        List<String> ids = new ArrayList<>();
        boolean tags = false;
        for (String v : values) {
            boolean tag = v.startsWith("#");
            String id = tag ? v.substring(1) : v;
            // every value must look like an ID with a namespace ("minecraft:stone"); an empty list relies on the key
            if (!id.contains(":") || Identifier.tryParse(id) == null) {
                return NONE;
            }
            if (tag) {
                tags = true;
            } else {
                ids.add(id);
            }
        }
        String key = optionKey.toLowerCase(Locale.ROOT);
        for (String[] hint : KEY_HINTS) {
            if (key.contains(hint[0]) && (ids.isEmpty() || allKnown(known.apply(hint[1]), ids))) {
                return new Detected(hint[1], tags);
            }
        }
        if (ids.isEmpty()) {
            return NONE;
        }
        for (String registry : BY_VALUE) {
            if (allKnown(known.apply(registry), ids)) {
                return new Detected(registry, tags);
            }
        }
        return NONE;
    }

    /** Every ID is an entry of the registry ({@code known}: its sorted IDs). */
    private static boolean allKnown(List<String> known, List<String> ids) {
        if (known.isEmpty()) {
            return false;
        }
        for (String id : ids) {
            Identifier rl = Identifier.tryParse(id);
            if (rl == null || java.util.Collections.binarySearch(known, rl.toString()) < 0) {
                return false;
            }
        }
        return true;
    }

    /** For tests and reloads. */
    static void clear() {
        CACHE.clear();
    }
}

package fr.heetstudio.confee.ui;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.api.BiomeFilter;
import fr.heetstudio.confee.api.EffectFilter;
import fr.heetstudio.confee.api.EnchantmentFilter;
import fr.heetstudio.confee.api.EntityFilter;
import fr.heetstudio.confee.api.ItemFilter;
import fr.heetstudio.confee.core.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which IDs pass the filters of an option ({@code @Entity(only = EntityFilter.MONSTER)}...), from what the game
 * already defines: spawn categories, creative tabs, effect categories, enchantment categories, biome tags.
 * <p>Internal: may change without notice.</p>
 */
public final class RegistryFilters {

    private static final Map<String, Set<String>> CACHE = new HashMap<>();
    @Nullable
    private static Object cachedWorld;

    private RegistryFilters() {
    }

    /** The IDs the option accepts, or null when it has no filters (every ID of its registry is fine). */
    @Nullable
    public static Predicate<String> of(@Nullable Option<?> option) {
        if (option == null || option.filters().isEmpty() || option.registry() == null) {
            return null;
        }
        Set<String> accepted = accepted(option.registry(), option.filters());
        return id -> accepted.contains(id.trim());
    }

    private static synchronized Set<String> accepted(String registry, List<Enum<?>> filters) {
        Object world = Minecraft.getInstance().level;
        if (world != cachedWorld) {
            CACHE.clear(); // biome tags and creative tabs depend on the world
            cachedWorld = world;
        }
        return CACHE.computeIfAbsent(registry + "|" + filters, k -> {
            Set<String> out = new HashSet<>();
            for (String id : RegistryEntries.ids(registry)) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null) {
                    continue;
                }
                for (Enum<?> filter : filters) {
                    try {
                        if (matches(registry, rl, filter)) {
                            out.add(id);
                            break;
                        }
                    } catch (RuntimeException | LinkageError e) {
                        ConfeeLog.warn("Cannot check {} against {}: {}", id, filter, e.toString());
                    }
                }
            }
            return out;
        });
    }

    private static boolean matches(String registry, ResourceLocation id, Enum<?> filter) {
        if (filter instanceof EntityFilter f) {
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                return false;
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
            return f == EntityFilter.LIVING ? DefaultAttributes.hasSupplier(type) : type.getCategory().name().equals(f.name());
        }
        if (filter instanceof ItemFilter f) {
            Item item = "minecraft:block".equals(registry)
                    ? (BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id).asItem() : Items.AIR)
                    : (BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : Items.AIR);
            if (item == Items.AIR) {
                return false;
            }
            return f == ItemFilter.BLOCKS ? item instanceof BlockItem : tabItems(f).contains(item);
        }
        if (filter instanceof EffectFilter f) {
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(id);
            return effect != null && BuiltInRegistries.MOB_EFFECT.containsKey(id) && effect.getCategory().name().equals(f.name());
        }
        if (filter instanceof EnchantmentFilter f) {
            // since 1.21 enchantments are data: tags and data files, like biomes
            return switch (f) {
                case TREASURE -> RegistryEntries.tagMembers("minecraft:enchantment", "minecraft:treasure").contains(id.toString());
                case CURSE -> RegistryEntries.tagMembers("minecraft:enchantment", "minecraft:curse").contains(id.toString());
                default -> {
                    String items = RegistryEntries.enchantmentSupportedItems(id);
                    yield items != null && supportedTags(f).stream().anyMatch(tag -> items.equals("#" + tag.location()));
                }
            };
        }
        if (filter instanceof BiomeFilter f) {
            return RegistryEntries.tagMembers("minecraft:worldgen/biome",
                    "minecraft:is_" + f.name().toLowerCase(Locale.ROOT)).contains(id.toString());
        }
        return false;
    }

    // ------------------------------------------------------------------ creative tabs

    private static final Map<ItemFilter, Set<Item>> TABS = new HashMap<>();
    @Nullable
    private static Object tabsWorld = new Object();

    /** Items of a vanilla creative tab, mods' additions included (the tabs are built as the creative inventory does). */
    private static synchronized Set<Item> tabItems(ItemFilter filter) {
        Object world = Minecraft.getInstance().level;
        if (world != tabsWorld) {
            TABS.clear();
            tabsWorld = world;
        }
        return TABS.computeIfAbsent(filter, f -> {
            Set<Item> items = new HashSet<>();
            try {
                Minecraft mc = Minecraft.getInstance();
                FeatureFlagSet flags = mc.level != null ? mc.level.enabledFeatures() : FeatureFlags.DEFAULT_FLAGS;
                HolderLookup.Provider lookup = mc.level != null ? mc.level.registryAccess() : VanillaRegistries.createLookup();
                // only this tab: outside a world some tabs (tools: goat horns) need tags that only a world binds (1.20.5+)
                CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(
                        ResourceLocation.fromNamespaceAndPath("minecraft", f.name().toLowerCase(Locale.ROOT)));
                if (tab != null) {
                    tab.buildContents(new CreativeModeTab.ItemDisplayParameters(flags, false, lookup));
                    for (ItemStack stack : tab.getDisplayItems()) {
                        items.add(stack.getItem());
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                ConfeeLog.warn("Cannot build the creative tab {}: {}", f, e.toString());
            }
            if (items.isEmpty()) {
                ConfeeLog.warn("Creative tab {} is empty: the filter accepts no item", f);
            }
            return items;
        });
    }

    /**
     * Since 1.20.5 an enchantment names the items it applies to with an item tag instead of a category: the tags that
     * match each category of {@link EnchantmentFilter}.
     */
    private static java.util.Set<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> supportedTags(EnchantmentFilter f) {
        return switch (f) {
            case ARMOR -> java.util.Set.of(ItemTags.ARMOR_ENCHANTABLE);
            case ARMOR_FEET -> java.util.Set.of(ItemTags.FOOT_ARMOR_ENCHANTABLE);
            case ARMOR_LEGS -> java.util.Set.of(ItemTags.LEG_ARMOR_ENCHANTABLE);
            case ARMOR_CHEST -> java.util.Set.of(ItemTags.CHEST_ARMOR_ENCHANTABLE);
            case ARMOR_HEAD -> java.util.Set.of(ItemTags.HEAD_ARMOR_ENCHANTABLE);
            case WEAPON -> java.util.Set.of(ItemTags.WEAPON_ENCHANTABLE, ItemTags.SWORD_ENCHANTABLE,
                    ItemTags.SHARP_WEAPON_ENCHANTABLE, ItemTags.FIRE_ASPECT_ENCHANTABLE);
            case DIGGER -> java.util.Set.of(ItemTags.MINING_ENCHANTABLE, ItemTags.MINING_LOOT_ENCHANTABLE);
            case FISHING_ROD -> java.util.Set.of(ItemTags.FISHING_ENCHANTABLE);
            case TRIDENT -> java.util.Set.of(ItemTags.TRIDENT_ENCHANTABLE);
            case BREAKABLE -> java.util.Set.of(ItemTags.DURABILITY_ENCHANTABLE);
            case BOW -> java.util.Set.of(ItemTags.BOW_ENCHANTABLE);
            case WEARABLE -> java.util.Set.of(ItemTags.EQUIPPABLE_ENCHANTABLE);
            case CROSSBOW -> java.util.Set.of(ItemTags.CROSSBOW_ENCHANTABLE);
            case VANISHABLE -> java.util.Set.of(ItemTags.VANISHING_ENCHANTABLE);
            case TREASURE, CURSE -> java.util.Set.of();
        };
    }
}

package fr.heetstudio.confee.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Icons and readable names of registry entries (spec 10.8): items and blocks (the item), entity types (spawn egg),
 * enchantments (enchanted book), status effects (effect icon), fluids (bucket). Other registries have no icon.
 * <p>Internal: may change without notice.</p>
 */
public final class RegistryIcons {

    private static final String ITEM = "minecraft:item";
    private static final String BLOCK = "minecraft:block";
    private static final String ENTITY = "minecraft:entity_type";
    private static final String ENCHANTMENT = "minecraft:enchantment";
    private static final String EFFECT = "minecraft:mob_effect";
    private static final String FLUID = "minecraft:fluid";

    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    private RegistryIcons() {
    }

    /** Whether entries of this registry have icons (field icon, icons in suggestions, grid picker). */
    public static boolean hasIcons(@Nullable String registry) {
        return fr.heetstudio.confee.core.Pickers.hasPicker(registry); // same list as the server-safe check
    }

    /** Draws the icon of an entry in a {@code size} box; false when it has none (unknown ID...). */
    public static boolean draw(GuiGraphics g, @Nullable String registry, String id, int x, int y, int size) {
        if (registry != null && fr.heetstudio.confee.core.PseudoRegistries.isModList(registry)) {
            return ModIcons.draw(g, id, x, y, size);
        }
        if (EFFECT.equals(registry)) {
            MobEffect effect = lookup(BuiltInRegistries.MOB_EFFECT, id);
            if (effect == null) {
                return false;
            }
            TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures().get(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
            g.blit(x, y, 0, size, size, sprite);
            return true;
        }
        ItemStack stack = stack(registry, id);
        if (stack.isEmpty()) {
            return false;
        }
        ItemIcons.drawItem(g, stack, x, y, size);
        return true;
    }

    /** The item standing for the entry (spawn egg, enchanted book, bucket...), or empty. */
    public static ItemStack stack(@Nullable String registry, String id) {
        if (registry == null || id.startsWith("#")) {
            return ItemStack.EMPTY;
        }
        return STACKS.computeIfAbsent(registry + "|" + id, k -> {
            if (ITEM.equals(registry) || BLOCK.equals(registry)) {
                return ItemIcons.stack(registry, id);
            }
            if (ENTITY.equals(registry)) {
                EntityType<?> type = lookup(BuiltInRegistries.ENTITY_TYPE, id);
                SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
                return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
            }
            if (ENCHANTMENT.equals(registry)) {
                Enchantment enchantment = lookup(BuiltInRegistries.ENCHANTMENT, id);
                return enchantment == null ? ItemStack.EMPTY
                        : EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, enchantment.getMaxLevel()));
            }
            if (FLUID.equals(registry)) {
                Fluid fluid = lookup(BuiltInRegistries.FLUID, id);
                return fluid == null || fluid.getBucket() == Items.AIR ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
            }
            return ItemStack.EMPTY;
        });
    }

    /** Translated name of the entry when the game knows one ("Zombie", "Plains", "Speed"...), else null. */
    @Nullable
    public static Component name(@Nullable String registry, String id) {
        if (registry != null && fr.heetstudio.confee.core.PseudoRegistries.isModList(registry)) {
            return fr.heetstudio.confee.platform.Services.PLATFORM.getModDisplayName(id).map(Component::literal).orElse(null);
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (registry == null || rl == null) {
            return null;
        }
        ItemStack stack = stack(registry, id);
        if ((ITEM.equals(registry) || BLOCK.equals(registry)) && !stack.isEmpty()) {
            return stack.getHoverName();
        }
        String kind = switch (registry) {
            case ENTITY -> "entity";
            case ENCHANTMENT -> "enchantment";
            case EFFECT -> "effect";
            case BLOCK -> "block";
            case "minecraft:worldgen/biome" -> "biome";
            case "minecraft:fluid" -> "block";
            default -> null;
        };
        if (kind == null) {
            return null;
        }
        String key = kind + "." + rl.getNamespace() + "." + rl.getPath().replace('/', '.');
        return I18n.exists(key) ? Component.translatable(key) : null;
    }

    @Nullable
    private static <T> T lookup(net.minecraft.core.Registry<T> registry, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null || !registry.containsKey(rl) ? null : registry.get(rl);
    }
}

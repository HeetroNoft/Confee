package fr.heetstudio.confee.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
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
    public static boolean draw(GuiGraphicsExtractor g, @Nullable String registry, String id, int x, int y, int size) {
        if (registry != null && fr.heetstudio.confee.core.PseudoRegistries.isModList(registry)) {
            return ModIcons.draw(g, id, x, y, size);
        }
        if (EFFECT.equals(registry)) {
            MobEffect effect = lookup(BuiltInRegistries.MOB_EFFECT, id);
            if (effect == null) {
                return false;
            }
            Identifier sprite = net.minecraft.client.gui.Hud.getMobEffectSprite(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, size, size);
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
            try {
                return buildStack(registry, id);
            } catch (RuntimeException e) {
                // an item that cannot be built (26.1+: components not bound outside a world) gets no icon
                return ItemStack.EMPTY;
            }
        });
    }

    private static ItemStack buildStack(String registry, String id) {
        ItemComponents.ensureBound();
        if (ITEM.equals(registry) || BLOCK.equals(registry)) {
            return ItemIcons.stack(registry, id);
        }
        if (ENTITY.equals(registry)) {
            EntityType<?> type = lookup(BuiltInRegistries.ENTITY_TYPE, id);
            return type == null ? ItemStack.EMPTY
                    : SpawnEggItem.byId(type).map(egg -> ItemComponents.stack(egg.value())).orElse(ItemStack.EMPTY);
        }
        if (ENCHANTMENT.equals(registry)) {
            // since 1.21 enchantments are data, only known in a world: the same enchanted book for all of them
            return ItemComponents.stack(Items.ENCHANTED_BOOK);
        }
        if (FLUID.equals(registry)) {
            Fluid fluid = lookup(BuiltInRegistries.FLUID, id);
            return fluid == null || fluid.getBucket() == Items.AIR ? ItemStack.EMPTY : ItemComponents.stack(fluid.getBucket());
        }
        return ItemStack.EMPTY;
    }

    /** Translated name of the entry when the game knows one ("Zombie", "Plains", "Speed"...), else null. */
    @Nullable
    public static Component name(@Nullable String registry, String id) {
        if (registry != null && fr.heetstudio.confee.core.PseudoRegistries.isModList(registry)) {
            return fr.heetstudio.confee.platform.Services.PLATFORM.getModDisplayName(id).map(Component::literal).orElse(null);
        }
        Identifier rl = Identifier.tryParse(id);
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
        return net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key) : null;
    }

    @Nullable
    private static <T> T lookup(net.minecraft.core.Registry<T> registry, String id) {
        Identifier rl = Identifier.tryParse(id);
        return rl == null || !registry.containsKey(rl) ? null : registry.getValue(rl);
    }
}

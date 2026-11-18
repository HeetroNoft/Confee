package fr.heetstudio.confee.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heetstudio.confee.ui.screen.Textures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Item / block icons for identifier fields, suggestions and pickers, and the icons of categories and decorations
 * (an item ID, or a texture when the text ends with {@code .png}).
 * <p>Internal: may change without notice.</p>
 */
public final class ItemIcons {

    private static final Map<String, ItemStack> CACHE = new HashMap<>();

    private ItemIcons() {
    }

    /** Stack showing the ID of the registry ({@code minecraft:item} / {@code minecraft:block}), or empty. */
    public static ItemStack stack(@Nullable String registry, String id) {
        if (registry == null) {
            return ItemStack.EMPTY;
        }
        return CACHE.computeIfAbsent(registry + "|" + id, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(id.trim());
            if (rl == null) {
                return ItemStack.EMPTY;
            }
            if ("minecraft:block".equals(registry)) {
                if (!BuiltInRegistries.BLOCK.containsKey(rl)) {
                    return ItemStack.EMPTY;
                }
                Block block = BuiltInRegistries.BLOCK.get(rl);
                return block.asItem() == Items.AIR ? ItemStack.EMPTY : new ItemStack(block.asItem());
            }
            if ("minecraft:item".equals(registry) && BuiltInRegistries.ITEM.containsKey(rl)) {
                return new ItemStack(BuiltInRegistries.ITEM.get(rl));
            }
            return ItemStack.EMPTY;
        });
    }

    /** Draws an item at {@code size} pixels (16 = native size). */
    public static void drawItem(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        if (stack.isEmpty()) {
            return;
        }
        if (size == 16) {
            g.renderItem(stack, x, y);
            return;
        }
        float scale = size / 16f;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    /**
     * Icon of a category / decoration: an item ID, or a texture ({@code ns:textures/....png}) drawn in a
     * {@code size}×{@code size} box. Unknown items draw nothing.
     */
    public static void drawIcon(GuiGraphics g, @Nullable String spec, int x, int y, int size) {
        if (spec == null) {
            return;
        }
        if (spec.endsWith(".png")) {
            ResourceLocation id = ResourceLocation.tryParse(spec);
            int[] texSize = id == null ? new int[]{0, 0} : Textures.size(spec);
            if (texSize[0] > 0) {
                RenderSystem.enableBlend();
                g.blit(id, x, y, size, size, 0, 0, texSize[0], texSize[1], texSize[0], texSize[1]);
                RenderSystem.disableBlend();
            }
            return;
        }
        drawItem(g, stack("minecraft:item", spec), x, y, size);
    }
}

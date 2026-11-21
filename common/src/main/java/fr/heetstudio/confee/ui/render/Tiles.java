package fr.heetstudio.confee.ui.render;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heetstudio.confee.core.StackText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Version-specific drawing behind {@code ConfeeDraw.tiled} and {@code ConfeeIcons.drawStack}.
 * <p>Internal: may change without notice.</p>
 */
public final class Tiles {

    private Tiles() {
    }

    /**
     * Repeats a texture over an area, clipped to it.
     *
     * @param texture a block or item texture of the atlas ({@code minecraft:block/stone_bricks}) or a texture file
     *                ({@code examplemod:textures/gui/tile.png})
     * @return false when the texture does not exist
     */
    public static boolean tiled(GuiGraphicsExtractor g, String texture, int x, int y, int width, int height) {
        Identifier id = Identifier.tryParse(texture);
        if (id == null || width <= 0 || height <= 0) {
            return false;
        }
        g.enableScissor(x, y, x + width, y + height);
        try {
            if (texture.endsWith(".png")) {
                int[] size = fr.heetstudio.confee.ui.screen.Textures.size(texture);
                if (size[0] <= 0) {
                    return false;
                }
                for (int ty = y; ty < y + height; ty += size[1]) {
                    for (int tx = x; tx < x + width; tx += size[0]) {
                        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, tx, ty, 0, 0, size[0], size[1], size[0], size[1], size[0], size[1]);
                    }
                }
                return true;
            }
            TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS).getSprite(id);
            if (sprite == null || sprite.contents().name().getPath().equals("missingno")) {
                return false;
            }
            for (int ty = y; ty < y + height; ty += 16) {
                for (int tx = x; tx < x + width; tx += 16) {
                    g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, tx, ty, 16, 16);
                }
            }
            return true;
        } finally {
            g.disableScissor();
        }
    }

    /** The stack written as SNBT or a plain item ID, or empty when it cannot be built. */
    public static ItemStack stack(String snbtOrId, int count) {
        StackText text;
        try {
            text = StackText.parse(snbtOrId);
        } catch (IllegalArgumentException e) {
            return ItemStack.EMPTY;
        }
        Identifier id = Identifier.tryParse(text.id());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        int n = count > 0 ? count : text.count();
        ItemStack plain = new ItemStack(item, n);
        if (text.extra() == null) {
            return plain;
        }
        try {
            // components need the registries (enchantments...): the world's, or the vanilla ones on the title screen
            net.minecraft.core.HolderLookup.Provider lookup = Minecraft.getInstance().level != null
                    ? Minecraft.getInstance().level.registryAccess()
                    : net.minecraft.data.registries.VanillaRegistries.createLookup();
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("id", id.toString());
            tag.putInt("count", n);
            tag.put("components", TagParser.parseCompoundFully(text.extra()));
            ItemStack stack = ItemStack.CODEC.parse(lookup.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag).result().orElse(ItemStack.EMPTY);
            return stack.isEmpty() ? plain : stack;
        } catch (CommandSyntaxException | RuntimeException e) {
            return plain; // invalid extra data: the plain item is drawn
        }
    }

    /** Item with the vanilla count and enchantment glint. */
    public static void drawStack(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        g.item(stack, x, y);
        g.itemDecorations(Minecraft.getInstance().font, stack, x, y);
    }
}

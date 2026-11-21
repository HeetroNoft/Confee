package fr.heetstudio.confee.api.client;

import fr.heetstudio.confee.ui.RegistryIcons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Icons and names of registry entries, as Confee shows them (items, blocks, spawn eggs for mobs, enchanted books,
 * effect icons, buckets for fluids). They also work from the title screen, without a world. <b>Client only.</b>
 *
 * <pre>{@code
 * ConfeeIcons.drawItem(g, "minecraft:diamond_sword", x, y, 16);
 * ConfeeIcons.draw(g, "minecraft:entity_type", "minecraft:zombie", x, y, 16); // zombie spawn egg
 * }</pre>
 */
public final class ConfeeIcons {

    private ConfeeIcons() {
    }

    /** Draws an item (or block item) in a {@code size} × {@code size} box; false when the ID is unknown. */
    public static boolean drawItem(GuiGraphics g, String itemId, int x, int y, int size) {
        return draw(g, "minecraft:item", itemId, x, y, size);
    }

    /**
     * Draws the icon of a registry entry in a {@code size} × {@code size} box.
     *
     * @param registry {@code minecraft:item}, {@code minecraft:block}, {@code minecraft:entity_type},
     *                 {@code minecraft:enchantment}, {@code minecraft:mob_effect} or {@code minecraft:fluid}
     * @return false when the entry has no icon (unknown ID, tag, other registry): nothing is drawn
     */
    public static boolean draw(GuiGraphics g, String registry, String id, int x, int y, int size) {
        try {
            return RegistryIcons.draw(g, registry, id, x, y, size);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Translated name of an entry ("Diamond Sword", "Zombie"...), or null when the game knows none. */
    @Nullable
    public static Component name(String registry, String id) {
        try {
            return RegistryIcons.name(registry, id);
        } catch (RuntimeException e) {
            return null;
        }
    }
}

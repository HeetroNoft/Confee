package fr.heetstudio.confee.ui;

import fr.heetstudio.confee.ConfeeLog;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Since Minecraft 26.1 the components of an item (its name, model...) are bound when the game data is loaded, that is
 * in a world: outside a world no item stack can be built. Confee's screens show items from the title screen too, so
 * the items' components are bound once from the vanilla registries, as the game does when it loads its data; joining
 * a world binds them again with the world's data.
 * <p>Internal: may change without notice.</p>
 */
final class ItemComponents {

    private static boolean bindingTried;

    private ItemComponents() {
    }

    /** Binds the items' components if the game has not done it yet. */
    static synchronized void ensureBound() {
        if (bindingTried || bound()) {
            return;
        }
        bindingTried = true;
        try {
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup()).stream()
                    .filter(pending -> pending.key().equals(Registries.ITEM))
                    .forEach(DataComponentInitializers.PendingComponents::apply);
        } catch (RuntimeException | LinkageError e) {
            // e.g. NeoForge refuses the placeholder tag sets of the vanilla registries: only what an icon needs
            ConfeeLog.info("Items prepared with their model and name only outside a world ({})", e.toString());
            bindModelAndName();
        }
    }

    /** Fallback: each unbound item gets its model and name, enough to draw it; a world binds its full components. */
    private static void bindModelAndName() {
        for (Item item : BuiltInRegistries.ITEM) {
            var holder = item.builtInRegistryHolder();
            try {
                holder.components();
            } catch (NullPointerException unbound) {
                try {
                    holder.bindComponents(DataComponentMap.builder()
                            .set(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(item))
                            .set(DataComponents.ITEM_NAME, Component.translatable(item.getDescriptionId()))
                            .set(DataComponents.MAX_STACK_SIZE, 64)
                            .build());
                } catch (RuntimeException e) {
                    // this item is left without an icon
                }
            }
        }
    }

    /** A stack of the item, or an empty stack when the item cannot be built (its components are not bound). */
    static ItemStack stack(Item item) {
        ensureBound();
        try {
            return new ItemStack(item);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private static boolean bound() {
        try {
            Items.STONE.components();
            return true;
        } catch (NullPointerException e) {
            return false;
        }
    }
}

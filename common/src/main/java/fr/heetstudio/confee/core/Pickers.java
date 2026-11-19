package fr.heetstudio.confee.core;

import fr.heetstudio.confee.api.PickerStyle;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Which ID options have a picker (registries with icons, mods) and which picker styles they offer. Safe on a dedicated
 * server (no client class).
 * <p>Internal: may change without notice.</p>
 */
public final class Pickers {

    /** Concrete styles, in the order of the switch buttons. */
    public static final List<PickerStyle> ALL = List.of(PickerStyle.GRID, PickerStyle.LIST, PickerStyle.DETAILED);

    private static final Set<String> ICON_REGISTRIES = Set.of("minecraft:item", "minecraft:block",
            "minecraft:entity_type", "minecraft:enchantment", "minecraft:mob_effect", "minecraft:fluid");

    private Pickers() {
    }

    /** Whether IDs of this registry have icons and a picker. */
    public static boolean hasPicker(@Nullable String registry) {
        return registry != null && (ICON_REGISTRIES.contains(registry) || PseudoRegistries.isModList(registry));
    }

    /** Styles the option allows (all when the dev chose none). */
    public static List<PickerStyle> allowed(@Nullable Option<?> option) {
        return option == null || option.pickerStyles().isEmpty() ? ALL : option.pickerStyles();
    }

    /** Style shown when the player has not chosen one: the dev's, else the default of the kind of entry. */
    public static PickerStyle defaultStyle(@Nullable Option<?> option, String registry) {
        List<PickerStyle> allowed = allowed(option);
        PickerStyle wanted = option == null ? PickerStyle.AUTO : option.pickerDefault();
        if (wanted == PickerStyle.AUTO) {
            wanted = PseudoRegistries.isModList(registry) ? PickerStyle.LIST : PickerStyle.GRID;
        }
        return allowed.contains(wanted) ? wanted : allowed.get(0);
    }

    /** Style the picker opens with: the player's last choice for this kind of entry when allowed, else the default. */
    public static PickerStyle initial(@Nullable Option<?> option, String registry, @Nullable PickerStyle remembered) {
        return remembered != null && allowed(option).contains(remembered) ? remembered : defaultStyle(option, registry);
    }
}

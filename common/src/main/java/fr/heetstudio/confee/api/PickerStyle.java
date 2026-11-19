package fr.heetstudio.confee.api;

/**
 * Layout of the picker opened by the button of an ID field (items, blocks, entities, mods...). See
 * {@link fr.heetstudio.confee.api.annotation.Picker}.
 */
public enum PickerStyle {
    /** The default of the kind of entry: {@link #GRID} for items, blocks and the other registries, {@link #LIST} for mods. */
    AUTO,
    /** Icons only, many per row; the name and the ID are in the tooltip. */
    GRID,
    /** One row per entry: icon, name and ID. */
    LIST,
    /** Two lines per entry: icon, name and ID, then the description (mods) or the ID. */
    DETAILED
}

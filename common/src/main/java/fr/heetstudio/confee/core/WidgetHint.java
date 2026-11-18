package fr.heetstudio.confee.core;

/**
 * Preferred widget for an option (spec 5.1). {@link #AUTO} lets the UI pick from the type.
 * <p>Internal: may change without notice.</p>
 */
public enum WidgetHint {
    AUTO,
    TOGGLE,
    SLIDER,
    FIELD,
    DROPDOWN,
    CYCLE,
    /** Multi-line text area. */
    MULTILINE,
    /** Number field with − and + buttons. */
    STEPPER,
    /** String holding a key ({@code key.keyboard.g}), edited by pressing it. */
    KEYBIND,
    /** List of enum values without duplicates, shown as check boxes. */
    CHECKBOXES
}

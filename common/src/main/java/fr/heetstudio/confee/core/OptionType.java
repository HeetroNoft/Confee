package fr.heetstudio.confee.core;

/**
 * Value types supported by the internal model (spec 5.1).
 * <p>Internal: may change without notice.</p>
 */
public enum OptionType {
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    STRING,
    ENUM,
    LIST,
    COLOR,
    IDENTIFIER,
    /** {@code Map<String, scalar>}: keys chosen by the player, values of {@link Option#elementType()}. */
    MAP,
    /** List of objects with fixed fields ({@link Option#objectFields()}). */
    OBJECT_LIST;

    public boolean isNumeric() {
        return this == INT || this == LONG || this == FLOAT || this == DOUBLE;
    }

    public boolean isIntegral() {
        return this == INT || this == LONG || this == COLOR;
    }
}

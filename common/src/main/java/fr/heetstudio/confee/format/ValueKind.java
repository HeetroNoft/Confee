package fr.heetstudio.confee.format;

/**
 * Kind of a value as written in a file. Java types of {@link ParsedValue#value()}:
 * <ul>
 *   <li>{@link #BOOLEAN} → Boolean</li>
 *   <li>{@link #INTEGER} → Long</li>
 *   <li>{@link #FLOAT} → Double</li>
 *   <li>{@link #STRING} → String</li>
 *   <li>{@link #DATETIME} → String (raw text, read-only)</li>
 *   <li>{@link #LIST} → List of Boolean / Long / Double / String, all of {@link ParsedValue#elementKind()}</li>
 *   <li>{@link #COMPLEX} → anything else (mixed lists, lists of tables...), read-only</li>
 * </ul>
 * <p>Internal: may change without notice.</p>
 */
public enum ValueKind {
    BOOLEAN,
    INTEGER,
    FLOAT,
    STRING,
    DATETIME,
    LIST,
    COMPLEX;

    public boolean isScalar() {
        return this == BOOLEAN || this == INTEGER || this == FLOAT || this == STRING;
    }
}

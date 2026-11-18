package fr.heetstudio.confee.format;

/**
 * The file content cannot be parsed. Carries a 1-based line and column for the log / UI.
 * <p>Internal: may change without notice.</p>
 */
public final class FormatException extends Exception {

    private final int line;
    private final int column;

    public FormatException(String message, int line, int column) {
        super(message + " (line " + line + ", column " + column + ")");
        this.line = line;
        this.column = column;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }
}

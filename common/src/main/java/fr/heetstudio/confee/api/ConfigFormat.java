package fr.heetstudio.confee.api;

/**
 * File formats a Confee config can be written in (spec 7.1). TOML is the default.
 */
public enum ConfigFormat {
    TOML("toml"),
    JSON("json"),
    JSON5("json5"),
    YAML("yml"),
    PROPERTIES("properties");

    private final String extension;

    ConfigFormat(String extension) {
        this.extension = extension;
    }

    /** File extension, without the leading dot. */
    public String extension() {
        return extension;
    }
}

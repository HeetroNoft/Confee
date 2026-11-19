package fr.heetstudio.confee.format;

import fr.heetstudio.confee.api.ConfigFormat;
import fr.heetstudio.confee.format.json.JsonAdapter;
import fr.heetstudio.confee.format.toml.TomlAdapter;
import fr.heetstudio.confee.format.yaml.YamlAdapter;
import fr.heetstudio.confee.format.cfg.CfgAdapter;
import fr.heetstudio.confee.format.ini.IniAdapter;
import fr.heetstudio.confee.format.properties.PropertiesAdapter;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Format adapters by file extension (spec 7.1).
 * <p>Internal: may change without notice.</p>
 */
public final class Formats {

    public static final FormatAdapter TOML = new TomlAdapter();
    public static final FormatAdapter JSON = new JsonAdapter("JSON", false, true);
    public static final FormatAdapter JSONC = new JsonAdapter("JSONC", false, false);
    public static final FormatAdapter JSON5 = new JsonAdapter("JSON5", true, true);
    public static final FormatAdapter YAML = new YamlAdapter();
    public static final FormatAdapter PROPERTIES = new PropertiesAdapter();
    public static final FormatAdapter INI = new IniAdapter();
    public static final FormatAdapter CFG = new CfgAdapter();
    public static final FormatAdapter HOCON = new fr.heetstudio.confee.format.hocon.HoconAdapter();

    private static final Map<String, FormatAdapter> BY_EXTENSION = Map.ofEntries(
            Map.entry("toml", TOML),
            Map.entry("json", JSON),
            Map.entry("jsonc", JSONC),
            Map.entry("json5", JSON5),
            Map.entry("yml", YAML),
            Map.entry("yaml", YAML),
            Map.entry("properties", PROPERTIES),
            Map.entry("ini", INI),
            Map.entry("cfg", CFG),
            Map.entry("conf", HOCON),
            Map.entry("hocon", HOCON));

    private Formats() {
    }

    public static Optional<FormatAdapter> forFile(Path file) {
        return Optional.ofNullable(BY_EXTENSION.get(extension(file)));
    }

    /** Adapter of a Confee config format. */
    public static FormatAdapter forConfee(ConfigFormat format) {
        return switch (format) {
            case TOML -> TOML;
            case JSON -> JSON;
            case JSON5 -> JSON5;
            case YAML -> YAML;
            case PROPERTIES -> PROPERTIES;
        };
    }

    public static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

package fr.heetstudio.confee.api.builder;

import fr.heetstudio.confee.api.ConfeeConfig;
import fr.heetstudio.confee.api.ConfigFormat;
import fr.heetstudio.confee.api.ConfigScope;
import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.core.ConfeeEnvironment;
import fr.heetstudio.confee.core.ConfeeRegistry;
import fr.heetstudio.confee.core.ConfigHandle;
import fr.heetstudio.confee.core.ConfigSchema;
import fr.heetstudio.confee.core.ConfigSource;
import fr.heetstudio.confee.core.Section;

import java.util.Objects;

/**
 * Declares a config in code (spec 6.2). Obtained from {@code Confee.builder(modId)}.
 *
 * <pre>{@code
 * ConfigBuilder b = Confee.builder("examplemod").name("client").scope(ConfigScope.CLIENT);
 * ConfigValue<Boolean> showHud = b.bool("showHud", true).comment("Show the mod HUD").build();
 * CategoryBuilder dash = b.category("dash");
 * ConfigValue<Double> distance = dash.doubleRange("distance", 4.0, 1.0, 10.0).slider(0.5).build();
 * ConfeeConfig<Void> config = b.build(); // registers and loads
 * }</pre>
 */
public final class ConfigBuilder extends SectionBuilder {

    private final String modId;
    private final ConfeeEnvironment environment;
    private String name;
    private ConfigScope scope = ConfigScope.COMMON;
    private ConfigFormat format = ConfigFormat.TOML;
    private Theme theme = Theme.DEFAULT;
    private fr.heetstudio.confee.api.Layout layout = fr.heetstudio.confee.api.Layout.DEFAULT;
    private boolean built;

    private ConfigBuilder(String modId, ConfeeEnvironment environment) {
        super(null, Section.root(null));
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("[Confee] modId must not be empty");
        }
        this.modId = modId;
        this.environment = environment;
    }

    /** Internal: use {@code Confee.builder(modId)}. */
    public static ConfigBuilder create(String modId) {
        return new ConfigBuilder(modId, ConfeeEnvironment.current());
    }

    /** Internal, for tests. */
    public static ConfigBuilder create(String modId, ConfeeEnvironment environment) {
        return new ConfigBuilder(modId, environment);
    }

    /** Config name: the file becomes {@code config/<modId>-<name>.toml}. */
    public ConfigBuilder name(String name) {
        checkNotBuilt();
        this.name = name == null || name.isEmpty() ? null : name;
        return this;
    }

    public ConfigBuilder scope(ConfigScope scope) {
        checkNotBuilt();
        this.scope = Objects.requireNonNull(scope);
        return this;
    }

    public ConfigBuilder format(ConfigFormat format) {
        checkNotBuilt();
        this.format = Objects.requireNonNull(format);
        return this;
    }

    /** Shortcut for a theme that only changes the accent color (ARGB). */
    public ConfigBuilder accentColor(int argb) {
        checkNotBuilt();
        this.theme = theme.toBuilder().accent(argb).build();
        return this;
    }

    /** Full theme (spec 11): every token can be customized. */
    public ConfigBuilder theme(Theme theme) {
        checkNotBuilt();
        this.theme = Objects.requireNonNull(theme);
        return this;
    }

    /** Layout of the screen (panel width, preview width, sidebar, banner, background). */
    public ConfigBuilder layout(fr.heetstudio.confee.api.Layout layout) {
        checkNotBuilt();
        this.layout = Objects.requireNonNull(layout);
        return this;
    }

    /** Header comment written at the top of the file. */
    public ConfigBuilder comment(String comment) {
        checkNotBuilt();
        section.setComment(comment);
        return this;
    }

    /**
     * Registers and loads the config. The file is created with defaults and comments if it does not exist.
     *
     * @throws IllegalStateException if a config with the same mod ID and name is already registered
     */
    public ConfeeConfig<Void> build() {
        checkNotBuilt();
        built = true;
        fr.heetstudio.confee.core.AnnotationConfigs.validateConditions(section, environment.development(), "config " + modId);
        ConfigSchema schema = new ConfigSchema(modId, name, scope, format,
                environment.configDir().resolve(ConfigSchema.fileName(modId, name, format)),
                theme, ConfigSource.CONFEE, section, layout);
        ConfigHandle<Void> handle = new ConfigHandle<>(schema, null, scope != ConfigScope.CLIENT || environment.client());
        ConfeeRegistry.register(handle);
        handle.load();
        return handle;
    }

    String modId() {
        return modId;
    }

    ConfeeEnvironment environment() {
        return environment;
    }

    void checkNotBuilt() {
        if (built) {
            throw new IllegalStateException("[Confee] Config builder of '" + modId + "' was already built");
        }
    }
}

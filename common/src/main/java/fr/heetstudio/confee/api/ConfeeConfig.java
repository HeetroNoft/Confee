package fr.heetstudio.confee.api;

import org.jetbrains.annotations.Nullable;

/**
 * A registered config (spec 6.3). Obtained from {@link Confee#register(Class)} or {@code ConfigBuilder#build()}.
 *
 * @param <T> the annotated class, or {@link Void} for a builder config
 */
public interface ConfeeConfig<T> {

    /** Instance of the annotated class holding the current values; {@code null} for a builder config. */
    @Nullable
    T get();

    /** Writes the current values to the file. Never throws: errors are logged. */
    void save();

    /** Reads the file again. Listeners are notified for every value that changed. */
    void reload();

    /** Restores every default value in memory. Call {@link #save()} to write the file. */
    void resetAll();

    /** Called after each save made from the config screen. */
    void onChange(Runnable callback);

    /**
     * Config screen of this config, for a mod that opens it from its own button (spec 6.3). Client only.
     *
     * @param parent screen to go back to
     * @throws IllegalStateException on a dedicated server
     */
    net.minecraft.client.gui.screens.Screen createScreen(net.minecraft.client.gui.screens.Screen parent);

    String modId();

    /** Config name ({@code client}, {@code common}...), or {@code null} for the mod's main config. */
    @Nullable
    String name();

    ConfigScope scope();
}

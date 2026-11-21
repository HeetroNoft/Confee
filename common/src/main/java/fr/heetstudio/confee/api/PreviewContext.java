package fr.heetstudio.confee.api;

import org.jetbrains.annotations.Nullable;

/**
 * What the screen knows when a {@link PreviewRenderer} draws: the pending values of the other options of the same
 * config (edited, not saved yet) and the theme of the screen. Client only, given by Confee.
 *
 * <pre>{@code
 * public void render(GuiGraphicsExtractor g, int x, int y, int width, int height, Object value, PreviewContext context, float delta) {
 *     boolean outline = Boolean.TRUE.equals(context.value("outline.enabled"));
 *     g.fill(x, y, x + width, y + height, context.theme().panelBgDeep());
 * }
 * }</pre>
 */
public interface PreviewContext {

    /**
     * Pending value of an option of the same config: the value shown on the screen, even when it is not saved yet.
     *
     * @param path dotted path of the option, e.g. {@code outline.rare}
     * @return the value (Boolean, Integer, Double, String, an enum, a List...), or null when no option has this path
     */
    @Nullable
    Object value(String path);

    /**
     * Same as {@link #value(String)}, cast to a type; {@code fallback} when the option is unknown or of another type.
     * <p>Example: {@code double factor = context.value("health.factor", Double.class, 1.0);}</p>
     */
    default <T> T value(String path, Class<T> type, T fallback) {
        Object value = value(path);
        return type.isInstance(value) ? type.cast(value) : fallback;
    }

    /** Theme of the screen, to draw with the same colors as Confee. */
    Theme theme();

    /**
     * Dotted path of the option being previewed, e.g. {@code rarity.elite}: one renderer class can serve several
     * options, or a whole section ({@code @Preview} on a {@code @Section} class).
     */
    default String path() {
        return "";
    }
}

package fr.heetstudio.confee.api;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Optional preview of an option, shown in a third column of the config screen when the player hovers or focuses the
 * option (hidden on narrow screens). Any combination of an image, a custom rendering and a text.
 *
 * <pre>{@code
 * Preview.image("examplemod:textures/preview/hud.png")
 *        .withText("The HUD is drawn in the top-left corner.")
 *        .withRenderer(() -> new HudPreview());   // client only, gets the value being edited
 * }</pre>
 */
public final class Preview {

    @Nullable
    private final String image;
    @Nullable
    private final String text;
    @Nullable
    private final Supplier<? extends PreviewRenderer> renderer;

    private Preview(@Nullable String image, @Nullable String text, @Nullable Supplier<? extends PreviewRenderer> renderer) {
        this.image = blankToNull(image);
        this.text = blankToNull(text);
        this.renderer = renderer;
    }

    /**
     * An image of your mod's resources, e.g. {@code examplemod:textures/preview/hud.png}. For enum options,
     * {@code {value}} is replaced by the lower-case name of the value being edited
     * ({@code examplemod:textures/preview/mode_{value}.png}).
     */
    public static Preview image(String resource) {
        return new Preview(Objects.requireNonNull(resource), null, null);
    }

    /** A text: plain text (§ formatting codes allowed) or a translation key. */
    public static Preview text(String text) {
        return new Preview(null, Objects.requireNonNull(text), null);
    }

    /** Custom rendering, created on the client only. */
    public static Preview renderer(Supplier<? extends PreviewRenderer> renderer) {
        return new Preview(null, null, Objects.requireNonNull(renderer));
    }

    /** Any combination; null when all three are missing. */
    @Nullable
    public static Preview of(@Nullable String image, @Nullable String text, @Nullable Supplier<? extends PreviewRenderer> renderer) {
        Preview preview = new Preview(image, text, renderer);
        return preview.image == null && preview.text == null && renderer == null ? null : preview;
    }

    public Preview withImage(String resource) {
        return new Preview(resource, text, renderer);
    }

    public Preview withText(String text) {
        return new Preview(image, text, renderer);
    }

    public Preview withRenderer(Supplier<? extends PreviewRenderer> renderer) {
        return new Preview(image, text, renderer);
    }

    @Nullable
    public String image() {
        return image;
    }

    @Nullable
    public String text() {
        return text;
    }

    @Nullable
    public Supplier<? extends PreviewRenderer> renderer() {
        return renderer;
    }

    @Nullable
    private static String blankToNull(@Nullable String s) {
        return s == null || s.isBlank() ? null : s;
    }
}

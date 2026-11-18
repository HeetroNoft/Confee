package fr.heetstudio.confee.api;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Element of the config screen that is not an option: title, separator, text, image or action button. Never written
 * in the file.
 *
 * <p>With the annotation API, declare a public (non-static) field of this type: it is shown at its place among the
 * options.</p>
 * <pre>{@code
 * public Decoration dashTitle = Decoration.header("Dash");
 * public Decoration wiki = Decoration.button("Open the wiki", () -> Util.getPlatform().openUri("https://..."));
 * }</pre>
 * <p>With the builder: {@code builder.decoration(Decoration.separator())}, or the shortcuts {@code header(...)},
 * {@code separator()}, {@code info(...)}, {@code image(...)}, {@code button(...)}.</p>
 *
 * <p>Texts are translation keys or plain text ({@code §} formatting codes work). Images are textures, e.g.
 * {@code examplemod:textures/gui/banner.png}. Icons are an item ID or a texture.</p>
 */
public final class Decoration {

    public enum Kind { HEADER, SEPARATOR, TEXT, IMAGE, BUTTON }

    /** Look of a {@link Kind#TEXT} block. */
    public enum Style { INFO, WARNING, PLAIN }

    private final Kind kind;
    @Nullable
    private final String text;
    @Nullable
    private final String image;
    private final int height;
    @Nullable
    private final Runnable action;
    private final Style style;
    @Nullable
    private final String icon;
    @Nullable
    private final String visibleIf;

    private Decoration(Kind kind, @Nullable String text, @Nullable String image, int height, @Nullable Runnable action,
                       Style style, @Nullable String icon, @Nullable String visibleIf) {
        this.kind = kind;
        this.text = text;
        this.image = image;
        this.height = height;
        this.action = action;
        this.style = style;
        this.icon = icon;
        this.visibleIf = visibleIf;
    }

    /** Title in the option list. */
    public static Decoration header(String text) {
        return new Decoration(Kind.HEADER, Objects.requireNonNull(text), null, 0, null, Style.PLAIN, null, null);
    }

    /** Thin horizontal line. */
    public static Decoration separator() {
        return new Decoration(Kind.SEPARATOR, null, null, 0, null, Style.PLAIN, null, null);
    }

    /** Information block (light frame). */
    public static Decoration info(String text) {
        return new Decoration(Kind.TEXT, Objects.requireNonNull(text), null, 0, null, Style.INFO, null, null);
    }

    /** Warning block (yellow frame and "!" icon). */
    public static Decoration warning(String text) {
        return new Decoration(Kind.TEXT, Objects.requireNonNull(text), null, 0, null, Style.WARNING, null, null);
    }

    /** Paragraph without frame. */
    public static Decoration text(String text) {
        return new Decoration(Kind.TEXT, Objects.requireNonNull(text), null, 0, null, Style.PLAIN, null, null);
    }

    /**
     * Image, as wide as possible (at most the panel width) for the given height, keeping its ratio.
     *
     * @param texture e.g. {@code examplemod:textures/gui/banner.png}
     * @param height  in GUI pixels
     */
    public static Decoration image(String texture, int height) {
        if (height <= 0) {
            throw new IllegalArgumentException("[Confee] Decoration.image: the height must be positive");
        }
        return new Decoration(Kind.IMAGE, null, Objects.requireNonNull(texture), height, null, Style.PLAIN, null, null);
    }

    /**
     * Button running {@code action} on the client when pressed (open a web page, another screen...). The action is
     * never run on a dedicated server. An exception is caught and logged.
     */
    public static Decoration button(String text, Runnable action) {
        return new Decoration(Kind.BUTTON, Objects.requireNonNull(text), null, 0, Objects.requireNonNull(action),
                Style.PLAIN, null, null);
    }

    /** Copy with an icon (item ID or texture) before the text of a header, text block or button. */
    public Decoration icon(String icon) {
        return new Decoration(kind, text, image, height, action, style, icon, visibleIf);
    }

    /** Copy shown only when the condition holds (same syntax as {@code @VisibleIf}). */
    public Decoration visibleIf(String condition) {
        return new Decoration(kind, text, image, height, action, style, icon,
                condition == null || condition.isBlank() ? null : condition.trim());
    }

    public Kind kind() {
        return kind;
    }

    @Nullable
    public String text() {
        return text;
    }

    @Nullable
    public String image() {
        return image;
    }

    public int height() {
        return height;
    }

    @Nullable
    public Runnable action() {
        return action;
    }

    public Style style() {
        return style;
    }

    @Nullable
    public String icon() {
        return icon;
    }

    @Nullable
    public String visibleIf() {
        return visibleIf;
    }
}

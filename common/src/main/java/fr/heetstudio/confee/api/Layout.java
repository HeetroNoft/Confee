package fr.heetstudio.confee.api;

import org.jetbrains.annotations.Nullable;

/**
 * Layout of a config screen (all optional). Annotation: {@code @Layout}; builder: {@code ConfigBuilder.layout(...)}.
 *
 * <pre>{@code
 * Layout.builder().panelWidth(460).banner("examplemod:textures/gui/banner.png", 40).build()
 * }</pre>
 */
public final class Layout {

    public enum Sidebar {
        /** Sidebar when there are 2+ categories and room for it, else a dropdown. */
        AUTO,
        /** Never a sidebar: the categories are in a dropdown above the options. */
        HIDDEN
    }

    public static final int DEFAULT_PANEL_WIDTH = 400;
    public static final int DEFAULT_PREVIEW_WIDTH = 150;
    public static final Layout DEFAULT = builder().build();

    private final int panelWidth;
    private final int previewWidth;
    private final Sidebar sidebar;
    @Nullable
    private final String banner;
    private final int bannerHeight;
    @Nullable
    private final String background;

    private Layout(Builder b) {
        this.panelWidth = b.panelWidth;
        this.previewWidth = b.previewWidth;
        this.sidebar = b.sidebar;
        this.banner = b.banner;
        this.bannerHeight = b.bannerHeight;
        this.background = b.background;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Maximum width of the option panel, in GUI pixels. */
    public int panelWidth() {
        return panelWidth;
    }

    public int previewWidth() {
        return previewWidth;
    }

    public Sidebar sidebar() {
        return sidebar;
    }

    /** Banner texture shown at the top of the option panel, or null. */
    @Nullable
    public String banner() {
        return banner;
    }

    public int bannerHeight() {
        return bannerHeight;
    }

    /** Texture tiled behind the panels, or null. */
    @Nullable
    public String background() {
        return background;
    }

    public static final class Builder {
        private int panelWidth = DEFAULT_PANEL_WIDTH;
        private int previewWidth = DEFAULT_PREVIEW_WIDTH;
        private Sidebar sidebar = Sidebar.AUTO;
        private String banner;
        private int bannerHeight = 48;
        private String background;

        private Builder() {
        }

        /** Maximum width of the option panel (200 to 1000 GUI pixels; default 400). */
        public Builder panelWidth(int width) {
            if (width < 200 || width > 1000) {
                throw new IllegalArgumentException("[Confee] Layout.panelWidth must be between 200 and 1000, got " + width);
            }
            this.panelWidth = width;
            return this;
        }

        /** Width of the preview column (100 to 400; default 150). */
        public Builder previewWidth(int width) {
            if (width < 100 || width > 400) {
                throw new IllegalArgumentException("[Confee] Layout.previewWidth must be between 100 and 400, got " + width);
            }
            this.previewWidth = width;
            return this;
        }

        public Builder sidebar(Sidebar sidebar) {
            this.sidebar = sidebar == null ? Sidebar.AUTO : sidebar;
            return this;
        }

        /** Banner texture at the top of the option panel, {@code height} GUI pixels high. */
        public Builder banner(String texture, int height) {
            if (height <= 0) {
                throw new IllegalArgumentException("[Confee] Layout.banner: the height must be positive");
            }
            this.banner = texture == null || texture.isBlank() ? null : texture;
            this.bannerHeight = height;
            return this;
        }

        /** Texture tiled behind the panels. */
        public Builder background(String texture) {
            this.background = texture == null || texture.isBlank() ? null : texture;
            return this;
        }

        public Layout build() {
            return new Layout(this);
        }
    }
}

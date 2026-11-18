package fr.heetstudio.confee.api;

import org.jetbrains.annotations.Nullable;

/**
 * Colors of a config screen (spec 10.2 and 11). All colors are ARGB ints.
 * <p>Every token can be overridden. {@code accentDark}, {@code accentLight} and {@code onAccentText}
 * are derived from {@code accent} unless set explicitly.</p>
 *
 * <pre>{@code
 * Theme theme = Theme.builder().accent(0xFF2D7DD2).build();
 * }</pre>
 */
public final class Theme {

    /** Confee's default theme (Bedrock green). */
    public static final Theme DEFAULT = builder().build();

    private final int accent;
    private final int accentDark;
    private final int accentLight;
    private final int onAccentText;
    private final int panelBg;
    private final int panelBgDeep;
    private final int buttonFace;
    private final int buttonFaceHover;
    private final int buttonShadow;
    private final int outline;
    private final int textOnDark;
    private final int textOnLight;
    private final int textDisabled;
    private final int warning;
    private final int error;
    private final int toggleOffTrack;
    private final int titleBarBg;
    private final int titleBarText;

    private Theme(Builder b) {
        this.accent = b.accent;
        this.accentDark = b.accentDark != null ? b.accentDark : ColorMath.shiftLightness(b.accent, -15);
        this.accentLight = b.accentLight != null ? b.accentLight : ColorMath.shiftLightness(b.accent, 8);
        // Spec 10.2: pick whichever of white / textOnLight contrasts best with the accent
        this.onAccentText = b.onAccentText != null ? b.onAccentText
                : ColorMath.contrast(b.accent, 0xFFFFFFFF) >= ColorMath.contrast(b.accent, b.textOnLight)
                ? 0xFFFFFFFF : b.textOnLight;
        this.panelBg = b.panelBg;
        this.panelBgDeep = b.panelBgDeep;
        this.buttonFace = b.buttonFace;
        this.buttonFaceHover = b.buttonFaceHover;
        this.buttonShadow = b.buttonShadow;
        this.outline = b.outline;
        this.textOnDark = b.textOnDark;
        this.textOnLight = b.textOnLight;
        this.textDisabled = b.textDisabled;
        this.warning = b.warning;
        this.error = b.error;
        this.toggleOffTrack = b.toggleOffTrack;
        this.titleBarBg = b.titleBarBg;
        this.titleBarText = b.titleBarText;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder pre-filled with this theme's base tokens (derived tokens are derived again). */
    public Builder toBuilder() {
        return new Builder()
                .accent(accent)
                .panelBg(panelBg).panelBgDeep(panelBgDeep)
                .buttonFace(buttonFace).buttonFaceHover(buttonFaceHover).buttonShadow(buttonShadow)
                .outline(outline)
                .textOnDark(textOnDark).textOnLight(textOnLight).textDisabled(textDisabled)
                .warning(warning).error(error).toggleOffTrack(toggleOffTrack)
                .titleBarBg(titleBarBg).titleBarText(titleBarText);
    }

    public int accent() { return accent; }
    public int accentDark() { return accentDark; }
    public int accentLight() { return accentLight; }
    public int onAccentText() { return onAccentText; }
    public int panelBg() { return panelBg; }
    public int panelBgDeep() { return panelBgDeep; }
    public int buttonFace() { return buttonFace; }
    public int buttonFaceHover() { return buttonFaceHover; }
    public int buttonShadow() { return buttonShadow; }
    public int outline() { return outline; }
    public int textOnDark() { return textOnDark; }
    public int textOnLight() { return textOnLight; }
    public int textDisabled() { return textDisabled; }
    public int warning() { return warning; }
    public int error() { return error; }
    public int toggleOffTrack() { return toggleOffTrack; }
    public int titleBarBg() { return titleBarBg; }
    public int titleBarText() { return titleBarText; }

    public static final class Builder {
        private int accent = 0xFF3C8527;
        @Nullable private Integer accentDark;
        @Nullable private Integer accentLight;
        @Nullable private Integer onAccentText;
        private int panelBg = 0xFF313233;
        private int panelBgDeep = 0xFF1E1E1F;
        private int buttonFace = 0xFFD0D1D4;
        private int buttonFaceHover = 0xFFE6E8EB;
        private int buttonShadow = 0xFF58585A;
        private int outline = 0xFF1E1E1F;
        private int textOnDark = 0xFFFFFFFF;
        private int textOnLight = 0xFF1E1E1F;
        private int textDisabled = 0xFF6E6E70;
        private int warning = 0xFFF7D23B;
        private int error = 0xFFE4433A;
        private int toggleOffTrack = 0xFF8C8D90;
        private int titleBarBg = 0xFFE7E8EC;
        private int titleBarText = 0xFF1E1E1F;

        private Builder() {
        }

        public Builder accent(int argb) { this.accent = argb; return this; }
        public Builder accentDark(int argb) { this.accentDark = argb; return this; }
        public Builder accentLight(int argb) { this.accentLight = argb; return this; }
        public Builder onAccentText(int argb) { this.onAccentText = argb; return this; }
        public Builder panelBg(int argb) { this.panelBg = argb; return this; }
        public Builder panelBgDeep(int argb) { this.panelBgDeep = argb; return this; }
        public Builder buttonFace(int argb) { this.buttonFace = argb; return this; }
        public Builder buttonFaceHover(int argb) { this.buttonFaceHover = argb; return this; }
        public Builder buttonShadow(int argb) { this.buttonShadow = argb; return this; }
        public Builder outline(int argb) { this.outline = argb; return this; }
        public Builder textOnDark(int argb) { this.textOnDark = argb; return this; }
        public Builder textOnLight(int argb) { this.textOnLight = argb; return this; }
        public Builder textDisabled(int argb) { this.textDisabled = argb; return this; }
        public Builder warning(int argb) { this.warning = argb; return this; }
        public Builder error(int argb) { this.error = argb; return this; }
        public Builder toggleOffTrack(int argb) { this.toggleOffTrack = argb; return this; }
        public Builder titleBarBg(int argb) { this.titleBarBg = argb; return this; }
        public Builder titleBarText(int argb) { this.titleBarText = argb; return this; }

        public Theme build() {
            return new Theme(this);
        }
    }

    /** HSL / luminance helpers for token derivation. */
    static final class ColorMath {

        private ColorMath() {
        }

        /** Shifts HSL lightness by {@code points} percentage points, clamped to [0, 100]. Alpha is kept. */
        static int shiftLightness(int argb, double points) {
            int a = argb >>> 24;
            double r = ((argb >> 16) & 0xFF) / 255.0;
            double g = ((argb >> 8) & 0xFF) / 255.0;
            double b = (argb & 0xFF) / 255.0;
            double max = Math.max(r, Math.max(g, b));
            double min = Math.min(r, Math.min(g, b));
            double l = (max + min) / 2;
            double h = 0;
            double s = 0;
            double d = max - min;
            if (d > 0) {
                s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
                if (max == r) {
                    h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
                } else if (max == g) {
                    h = ((b - r) / d + 2) / 6;
                } else {
                    h = ((r - g) / d + 4) / 6;
                }
            }
            l = Math.max(0, Math.min(1, l + points / 100.0));
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            int nr = channel(p, q, h + 1.0 / 3);
            int ng = channel(p, q, h);
            int nb = channel(p, q, h - 1.0 / 3);
            return (a << 24) | (nr << 16) | (ng << 8) | nb;
        }

        private static int channel(double p, double q, double t) {
            if (t < 0) t += 1;
            if (t > 1) t -= 1;
            double v;
            if (t < 1.0 / 6) v = p + (q - p) * 6 * t;
            else if (t < 0.5) v = q;
            else if (t < 2.0 / 3) v = p + (q - p) * (2.0 / 3 - t) * 6;
            else v = p;
            return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
        }

        /** WCAG relative luminance in [0, 1]. */
        static double relativeLuminance(int argb) {
            return 0.2126 * linear((argb >> 16) & 0xFF)
                    + 0.7152 * linear((argb >> 8) & 0xFF)
                    + 0.0722 * linear(argb & 0xFF);
        }

        /** WCAG contrast ratio between two opaque colors, in [1, 21]. */
        static double contrast(int a, int b) {
            double la = relativeLuminance(a);
            double lb = relativeLuminance(b);
            return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
        }

        private static double linear(int channel) {
            double c = channel / 255.0;
            return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }
    }
}

package fr.heetstudio.confee.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThemeTest {

    @Test
    void defaultThemeUsesSpecTokens() {
        Theme t = Theme.DEFAULT;
        assertEquals(0xFF3C8527, t.accent());
        assertEquals(0xFF313233, t.panelBg());
        assertEquals(0xFFFFFFFF, t.onAccentText(), "white text on the default green");
    }

    @Test
    void derivedAccentShadesMoveLightnessOnly() {
        Theme t = Theme.builder().accent(0xFF3C8527).build();
        assertTrue(Theme.ColorMath.relativeLuminance(t.accentDark()) < Theme.ColorMath.relativeLuminance(t.accent()));
        assertTrue(Theme.ColorMath.relativeLuminance(t.accentLight()) > Theme.ColorMath.relativeLuminance(t.accent()));
        assertEquals(0xFF, t.accentDark() >>> 24, "alpha kept");
    }

    @Test
    void lightAccentGetsDarkText() {
        Theme t = Theme.builder().accent(0xFFF2E86D).build();
        assertEquals(t.textOnLight(), t.onAccentText());
    }

    @Test
    void midLuminanceAccentGetsTheMoreReadableText() {
        // Luminance ~0.3: white would only reach ~2.8:1, dark text reaches ~6:1
        int accent = 0xFF2DA8A0;
        Theme t = Theme.builder().accent(accent).build();
        assertEquals(t.textOnLight(), t.onAccentText());
        assertTrue(Theme.ColorMath.contrast(accent, t.onAccentText()) >= 4.5);
    }

    @Test
    void explicitTokensOverrideDerivation() {
        Theme t = Theme.builder().accent(0xFF2D7DD2).accentDark(0xFF000000).onAccentText(0xFF123456)
                .panelBg(0xFF101010).build();
        assertEquals(0xFF000000, t.accentDark());
        assertEquals(0xFF123456, t.onAccentText());
        assertEquals(0xFF101010, t.panelBg());
    }

    @Test
    void lightnessShiftOnGrayIsExact() {
        // #808080 is L = 50.2 %; -15 points -> ~35.2 % -> 0x5A
        assertEquals(0xFF5A5A5A, Theme.ColorMath.shiftLightness(0xFF808080, -15));
        assertEquals(0xFFFFFFFF, Theme.ColorMath.shiftLightness(0xFFF0F0F0, 50), "clamped at 100 %");
    }
}

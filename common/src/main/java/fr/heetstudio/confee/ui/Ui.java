package fr.heetstudio.confee.ui;

import fr.heetstudio.confee.api.Theme;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * What every widget needs: font, theme colors, clock and the click sound.
 * <p>Internal: may change without notice.</p>
 */
public final class Ui {

    /** Spec 10.3: tooltips appear after ~500 ms. */
    public static final long TOOLTIP_DELAY_MS = 500;
    /** A tooltip hidden less than this ago: the next one shows without the delay. */
    public static final long TOOLTIP_WARM_MS = 1000;
    /** Fade-in of a tooltip. */
    public static final long TOOLTIP_FADE_MS = 80;

    private final Font font;
    private final Theme theme;

    public Ui(Font font, Theme theme) {
        this.font = font;
        this.theme = theme;
    }

    public Font font() {
        return font;
    }

    public Theme theme() {
        return theme;
    }

    /**
     * Secondary text on dark panels (headers, hints, empty states): softer than the main text but still readable
     * (about 6.4:1 on the default panel, WCAG AA). {@code textDisabled} is only for disabled controls.
     */
    public int secondaryText() {
        return fr.heetstudio.confee.ui.render.Draw.mix(theme.textOnDark(), theme.panelBg(), 0.35f);
    }

    /** Height of the window in GUI pixels. */
    public int screenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    public long now() {
        return Util.getMillis();
    }

    /** Vanilla click sound on every action (spec 10.3). */
    public void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}

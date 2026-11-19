package fr.heetstudio.confee.ui.screen;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Colors stored as integers in the files of other mods (spec 8.2 bis, V1.1): detected by the key name and the value
 * range, written back as an integer of the same kind.
 * <p>Internal: may change without notice.</p>
 */
final class IntColors {

    private static final String[] WORDS = {"color", "colour", "couleur", "tint"};

    private IntColors() {
    }

    /** Key containing color, colour, couleur or tint (case-insensitive). */
    static boolean isColorKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        for (String word : WORDS) {
            if (k.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The color of an integer value: between 0 and {@code 0xFFFFFFFF}, or a negative signed 32-bit ARGB. RGB values
     * ({@code <= 0xFFFFFF}) are shown opaque. Null when the value is out of range.
     */
    @Nullable
    static Integer argb(@Nullable Object value) {
        if (!(value instanceof Integer || value instanceof Long)) {
            return null;
        }
        long v = ((Number) value).longValue();
        if (v < Integer.MIN_VALUE || v > 0xFFFFFFFFL) {
            return null;
        }
        return v >= 0 && v <= 0xFFFFFFL ? (int) (0xFF000000L | v) : (int) v;
    }

    /** ARGB when the value does not fit in RGB (above {@code 0xFFFFFF}, or negative). */
    static boolean hasAlpha(Object value) {
        long v = ((Number) value).longValue();
        return v < 0 || v > 0xFFFFFFL;
    }

    /**
     * The picked color as the file's integer: RGB without alpha; ARGB signed when the original was negative or the
     * value is an {@code int}, unsigned when it is a {@code long} that was not negative.
     */
    static Object toValue(int argb, boolean alpha, boolean signed, boolean isLong) {
        if (!alpha) {
            int rgb = argb & 0xFFFFFF;
            return isLong ? (Object) (long) rgb : (Object) rgb;
        }
        if (!isLong) {
            return argb;
        }
        return signed ? (long) argb : argb & 0xFFFFFFFFL;
    }
}

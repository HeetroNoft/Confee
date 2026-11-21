package fr.heetstudio.confee.ui;

import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.OptionType;
import fr.heetstudio.confee.core.Validated;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Text ↔ value conversions for fields and labels, and display names. Pure logic, no Minecraft classes.
 * <p>Internal: may change without notice.</p>
 */
public final class ValueText {

    private ValueText() {
    }

    /** {@code dashDistance} → {@code Dash Distance}, {@code max_speed} → {@code Max Speed}, {@code HARD} → {@code Hard} (spec 13). */
    public static String humanize(String key) {
        if (key.isEmpty()) {
            return key;
        }
        StringBuilder words = new StringBuilder();
        boolean allUpper = key.equals(key.toUpperCase(Locale.ROOT));
        char prev = 0;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_' || c == '-' || c == ' ' || c == '.') {
                words.append(' ');
            } else {
                boolean boundary = !allUpper && i > 0 && Character.isUpperCase(c)
                        && (Character.isLowerCase(prev) || Character.isDigit(prev)
                        || (i + 1 < key.length() && Character.isLowerCase(key.charAt(i + 1)) && Character.isUpperCase(prev)));
                boolean digitBoundary = i > 0 && Character.isDigit(c) != Character.isDigit(prev)
                        && prev != '_' && prev != '-' && Character.isLetter(prev);
                if (boundary || digitBoundary) {
                    words.append(' ');
                }
                words.append(c);
            }
            prev = c;
        }
        StringBuilder out = new StringBuilder();
        for (String word : words.toString().trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            boolean acronym = !allUpper && word.length() > 1 && word.equals(word.toUpperCase(Locale.ROOT));
            out.append(acronym ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    /** Number for labels: integers as is, decimals without trailing zeros ({@code 4.0} → {@code 4}). */
    public static String number(Object value) {
        if (value instanceof Float f) {
            return new BigDecimal(Float.toString(f)).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                return d.toString();
            }
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    /** Text put in a field for a value. */
    public static String toFieldText(Option<?> option, Object value) {
        return switch (option.type()) {
            case FLOAT, DOUBLE -> number(value);
            case COLOR -> color((Integer) value, option.colorAlpha());
            default -> String.valueOf(value);
        };
    }

    public static String color(int argb, boolean alpha) {
        return alpha ? String.format("#%08X", argb) : String.format("#%06X", argb & 0xFFFFFF);
    }

    /** Error kinds shown under a field (translated by the UI). */
    public enum Problem { NOT_A_NUMBER, NOT_AN_INTEGER, OUT_OF_RANGE, PATTERN, MAX_LENGTH, IDENTIFIER, COLOR, INVALID, REFUSED }

    /**
     * Result of parsing field text.
     *
     * @param value   the parsed value when valid
     * @param problem why the text is refused, or null
     * @param reason  message of the mod's validator when {@code problem} is {@link Problem#REFUSED}
     */
    public record Parsed(@Nullable Object value, @Nullable Problem problem, @Nullable String reason) {

        public Parsed(@Nullable Object value, @Nullable Problem problem) {
            this(value, problem, null);
        }

        public boolean ok() {
            return problem == null;
        }
    }

    /**
     * Parses field text for an option of a scalar type (spec 10.3: invalid values are not applied).
     * Out of range values are refused here instead of clamped, so the player sees why.
     */
    public static Parsed parse(Option<?> option, String text) {
        return parse(option, option.type(), text);
    }

    /** Same, for one element of a list option. */
    public static Parsed parseElement(Option<?> listOption, String text) {
        return parse(null, listOption.elementType(), text);
    }

    /** Text of a scalar of the given type (map values, object fields), without option constraints. */
    public static Parsed parseScalar(OptionType type, String text) {
        return parse(null, type, text);
    }

    private static Parsed parse(@Nullable Option<?> option, OptionType type, String text) {
        String t = text.trim();
        Object raw;
        switch (type) {
            case INT, LONG -> {
                try {
                    raw = Long.parseLong(t);
                } catch (NumberFormatException e) {
                    return new Parsed(null, looksDecimal(t) ? Problem.NOT_AN_INTEGER : Problem.NOT_A_NUMBER);
                }
                if (type == OptionType.INT && (long) raw != (int) (long) raw) {
                    return new Parsed(null, Problem.OUT_OF_RANGE);
                }
                if (type == OptionType.INT) {
                    raw = (int) (long) raw;
                }
            }
            case FLOAT, DOUBLE -> {
                try {
                    if (t.isEmpty() || t.endsWith(".") || t.startsWith(".") || t.contains("e") || t.contains("E")) {
                        throw new NumberFormatException();
                    }
                    double d = Double.parseDouble(t);
                    if (Double.isNaN(d) || Double.isInfinite(d)) {
                        throw new NumberFormatException();
                    }
                    raw = type == OptionType.FLOAT ? (Object) (float) d : (Object) d;
                } catch (NumberFormatException e) {
                    return new Parsed(null, Problem.NOT_A_NUMBER);
                }
            }
            case COLOR -> raw = text.trim();
            case BOOLEAN -> raw = t;
            default -> raw = text;
        }
        if (option == null) {
            return new Parsed(raw, null);
        }
        Validated<?> validated = option.validate(raw);
        return switch (validated.status()) {
            case OK -> new Parsed(validated.value(), null);
            case CORRECTED -> new Parsed(null, Problem.OUT_OF_RANGE);
            case REPLACED_BY_DEFAULT -> validated.reason() != null ? new Parsed(null, Problem.REFUSED, validated.reason())
                    : new Parsed(null, problemFor(option, raw));
        };
    }

    private static Problem problemFor(Option<?> option, Object raw) {
        return switch (option.type()) {
            case IDENTIFIER -> Problem.IDENTIFIER;
            case COLOR -> Problem.COLOR;
            case STRING -> option.maxLength() > 0 && raw instanceof String s && s.length() > option.maxLength()
                    ? Problem.MAX_LENGTH : Problem.PATTERN;
            default -> Problem.INVALID;
        };
    }

    private static boolean looksDecimal(String t) {
        try {
            Double.parseDouble(t);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Characters a number field accepts (spec 10.3: digits, '-', '.'). */
    public static boolean isNumberText(String text, boolean decimal) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = Character.isDigit(c) || c == '-' || (decimal && c == '.');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** Rounds a slider value to {@code min + k * step}, clamped to [min, max]. */
    public static double snap(double value, double min, double max, double step) {
        double snapped = step > 0 ? min + Math.round((value - min) / step) * step : value;
        snapped = Math.max(min, Math.min(max, snapped));
        // remove floating point noise (0.1 + 0.2)
        return BigDecimal.valueOf(snapped).setScale(10, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    /** Message of a mod's validator: a translation key of its language files, or plain text. */
    public static net.minecraft.network.chat.Component reason(String reason) {
        return net.minecraft.locale.Language.getInstance().has(reason)
                ? net.minecraft.network.chat.Component.translatable(reason) : net.minecraft.network.chat.Component.literal(reason);
    }
}

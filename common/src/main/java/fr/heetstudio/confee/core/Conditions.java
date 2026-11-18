package fr.heetstudio.confee.core;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Function;

/**
 * Conditions between options ({@code @EnabledIf} / {@code @VisibleIf}): {@code path} (boolean option is true),
 * {@code !path} (false), {@code path=VALUE} (value equals, case-insensitive for enums and booleans).
 * <p>Internal: may change without notice.</p>
 */
public final class Conditions {

    private Conditions() {
    }

    /** Path of the option a condition refers to. */
    public static String target(String condition) {
        String c = condition.startsWith("!") ? condition.substring(1) : condition;
        int eq = c.indexOf('=');
        return (eq < 0 ? c : c.substring(0, eq)).trim();
    }

    /**
     * @param valueOf current (possibly pending) value of an option
     * @return true when there is no condition, the condition holds, or its option does not exist
     */
    public static boolean test(@Nullable String condition, Section root, Function<Option<?>, Object> valueOf) {
        if (condition == null) {
            return true;
        }
        Option<?> option = root.findOption(target(condition)).orElse(null);
        if (option == null) {
            return true;
        }
        Object value = valueOf.apply(option);
        if (condition.startsWith("!")) {
            return Boolean.FALSE.equals(value);
        }
        int eq = condition.indexOf('=');
        if (eq < 0) {
            return Boolean.TRUE.equals(value);
        }
        String expected = condition.substring(eq + 1).trim();
        String actual = value instanceof Enum<?> e ? e.name() : String.valueOf(value);
        if (value instanceof Number n && !(value instanceof Float || value instanceof Double)) {
            actual = Long.toString(n.longValue());
        }
        return actual.equalsIgnoreCase(expected) || actual.toLowerCase(Locale.ROOT).equals(expected.toLowerCase(Locale.ROOT));
    }

    /**
     * Checks that every condition of the tree refers to an existing option.
     *
     * @throws IllegalStateException with every problem found
     */
    public static void validate(Section root) {
        StringBuilder problems = new StringBuilder();
        root.forEachOption(option -> {
            for (String condition : new String[]{option.enabledIf(), option.visibleIf()}) {
                check(root, condition, "'" + option.path() + "'", problems);
            }
        });
        validateDecorations(root, root, problems);
        if (problems.length() > 0) {
            throw new IllegalStateException("[Confee] Invalid conditions:" + problems
                    + "\n  Fix: use the dotted path of an option of the same config, e.g. \"dash.enabled\" or \"!dash.enabled\"");
        }
    }

    private static void validateDecorations(Section root, Section section, StringBuilder problems) {
        for (Section.Placed placed : section.decorations()) {
            check(root, placed.decoration().visibleIf(), "a decoration of '" + (section.isRoot() ? "(root)" : section.path()) + "'", problems);
        }
        for (Node child : section.children()) {
            if (child instanceof Section sub) {
                validateDecorations(root, sub, problems);
            }
        }
    }

    private static void check(Section root, @Nullable String condition, String who, StringBuilder problems) {
        if (condition == null || root.findOption(target(condition)).isPresent()) {
            return;
        }
        String target = target(condition);
        problems.append("\n  ").append(who).append(" depends on unknown option '").append(target).append("'");
        // "did you mean": an option with the same last key elsewhere
        String last = target.substring(target.lastIndexOf('.') + 1);
        StringBuilder guess = new StringBuilder();
        root.forEachOption(o -> {
            if (guess.length() == 0 && o.key().equalsIgnoreCase(last)) {
                guess.append(o.path());
            }
        });
        if (guess.length() > 0) {
            problems.append(" (did you mean '").append(guess).append("'?)");
        }
    }
}

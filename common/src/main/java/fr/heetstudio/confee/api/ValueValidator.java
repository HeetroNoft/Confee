package fr.heetstudio.confee.api;

import org.jetbrains.annotations.Nullable;

/**
 * Your own check of a value ({@code @Validator(MyCheck.class)} or {@code OptionBuilder.validator(...)}), run after
 * Confee's checks (type, range, pattern...). On a list of simple values ({@code List<String>}, {@code List<Integer>}...)
 * it checks each value; on any other option, the whole value.
 *
 * <p>A refused value is treated like an out-of-pattern text: in the file, the default value is used (with a warning in
 * the log); on the screen, the field shows your message in red and "Save" is disabled.</p>
 *
 * <p>Runs on the server too: do not use client classes. Keep it fast, it runs on every keystroke in the screen.</p>
 *
 * <pre>{@code
 * public class SnbtCheck implements ValueValidator<String> {
 *     public String validate(String value) {
 *         try {
 *             TagParser.parseTag(value);
 *             return null;
 *         } catch (CommandSyntaxException e) {
 *             return "examplemod.error.snbt"; // translation key, or plain text
 *         }
 *     }
 * }
 * }</pre>
 *
 * @param <T> type of the value checked (each value of a list)
 */
@FunctionalInterface
public interface ValueValidator<T> {

    /**
     * @return null when the value is valid; otherwise the message shown under the field: a translation key of your
     * language files, or plain text
     */
    @Nullable
    String validate(T value);
}

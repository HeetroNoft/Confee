package fr.heetstudio.confee.ui;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.api.SuggestionProvider;
import fr.heetstudio.confee.core.Option;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Suggestions for text fields: the mod's own values ({@code @Suggestions}) and the IDs of a built-in registry
 * ({@code minecraft:block}...), filtered with a forgiving match ("dmd" finds "diamond").
 * <p>Internal: may change without notice.</p>
 */
public final class Suggestions {

    /** Maximum results kept (the list scrolls). */
    public static final int MAX = 50;
    private static final Map<String, List<String>> CACHE = new HashMap<>();

    private Suggestions() {
    }

    /**
     * Suggester of an option (registry and/or mod values), or null when it has none. The candidates are read once,
     * when the screen opens.
     */
    /**
     * Greyed hint of an empty field whose suggestions come from a world ({@code SuggestionProvider#needsWorld}) while
     * no world is loaded, else null.
     */
    @Nullable
    public static net.minecraft.network.chat.Component worldHint(Option<?> option) {
        SuggestionProvider provider = option.suggestions();
        if (provider == null || net.minecraft.client.Minecraft.getInstance().level != null) {
            return null;
        }
        try {
            return provider.needsWorld() ? net.minecraft.network.chat.Component.translatable("confee.suggestions.needs_world") : null;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Nullable
    public static Function<String, List<String>> forOption(Option<?> option) {
        return forSources(option.registry(), option.allowTags(), option.suggestions(), option.path());
    }

    @Nullable
    public static Function<String, List<String>> forSources(@Nullable String registry, boolean tags,
                                                            @Nullable SuggestionProvider provider, String where) {
        return forSources(registry, tags, provider, where, null);
    }

    /** @param accept the IDs the option accepts (its filters), or null for every ID of the registry */
    public static Function<String, List<String>> forSources(@Nullable String registry, boolean tags,
                                                            @Nullable SuggestionProvider provider, String where,
                                                            @Nullable java.util.function.Predicate<String> accept) {
        if (registry == null && provider == null) {
            return null;
        }
        Set<String> all = new LinkedHashSet<>();
        if (provider != null) {
            try {
                Collection<String> values = provider.suggestions();
                if (values != null) {
                    values.stream().filter(v -> v != null && !v.isBlank()).forEach(all::add);
                }
            } catch (RuntimeException | LinkageError e) {
                ConfeeLog.warn("Suggestions of '{}' failed: {}", where, e.toString());
            }
        }
        if (registry != null) {
            for (String id : RegistryEntries.ids(registry)) {
                if (accept == null || accept.test(id)) {
                    all.add(id);
                }
            }
            if (tags) {
                all.addAll(RegistryEntries.tags(registry));
            }
        }
        List<String> candidates = List.copyOf(all);
        return text -> filter(candidates, text, MAX);
    }

    /** Best matches of {@code text}: starts with it, then word starts, then contains, then letters in order. */
    public static List<String> filter(List<String> candidates, String text, int max) {
        String q = text.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return candidates.size() > max ? List.copyOf(candidates.subList(0, max)) : candidates;
        }
        record Match(String value, int score) {
        }
        List<Match> matches = new ArrayList<>();
        for (String candidate : candidates) {
            int score = score(candidate.toLowerCase(Locale.ROOT), q);
            if (score >= 0) {
                matches.add(new Match(candidate, score));
            }
        }
        matches.sort(Comparator.comparingInt(Match::score).thenComparingInt(m -> m.value().length())
                .thenComparing(Match::value));
        List<String> out = new ArrayList<>(Math.min(max, matches.size()));
        for (int i = 0; i < matches.size() && i < max; i++) {
            out.add(matches.get(i).value());
        }
        return out;
    }

    /** Lower is better; -1 = no match. */
    public static int score(String candidate, String q) {
        String path = candidate.substring(candidate.indexOf(':') + 1);
        if (candidate.equals(q) || path.equals(q)) {
            return 0;
        }
        if (candidate.startsWith(q) || path.startsWith(q)) {
            return 1;
        }
        int index = candidate.indexOf(q);
        if (index > 0) {
            char before = candidate.charAt(index - 1);
            return before == '_' || before == ':' || before == '/' || before == '.' || before == ' ' ? 2 : 3;
        }
        // letters in order ("dmd" → diamond), the fewer gaps the better
        int gaps = 0;
        int last = -1;
        for (int i = 0; i < q.length(); i++) {
            int found = path.indexOf(q.charAt(i), last + 1);
            if (found < 0) {
                return -1;
            }
            if (last >= 0) {
                gaps += found - last - 1;
            }
            last = found;
        }
        // only close matches: a few skipped letters, not any word containing the letters somewhere
        return gaps <= q.length() + 1 ? 4 + gaps : -1;
    }

    /** Sorted IDs of a registry: see {@link RegistryEntries#ids}. */
    public static List<String> ids(String registry) {
        return RegistryEntries.ids(registry);
    }
}

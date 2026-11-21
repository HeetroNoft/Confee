package fr.heetstudio.confee.api;

import java.util.Collection;

/**
 * Gives the values suggested while typing in a text field ({@code @Suggestions(provider = ...)} or
 * {@code OptionBuilder.suggestions(...)}). Called on the client each time the screen is opened, so it can read the
 * game state (loaded mods, registries...). Confee filters and sorts the values itself.
 */
@FunctionalInterface
public interface SuggestionProvider {

    Collection<String> suggestions();

    /**
     * True when some suggestions come from a loaded world (scoreboard tags, teams, datapack entries...): from the
     * title screen, an empty field then shows a greyed "open a world for more suggestions" hint. Default false.
     */
    default boolean needsWorld() {
        return false;
    }
}

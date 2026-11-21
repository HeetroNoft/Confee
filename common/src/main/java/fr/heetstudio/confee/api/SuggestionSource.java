package fr.heetstudio.confee.api;

/**
 * Suggestions read by Confee from the loaded world ({@code @Suggestions(sources = ...)} or
 * {@code OptionBuilder.suggestions(SuggestionSource...)}). From the title screen there are none, and an empty field
 * shows "Open a world for more suggestions".
 */
public enum SuggestionSource {
    /** Scoreboard tags of the entities around the player ({@code /tag}). */
    SCOREBOARD_TAGS,
    /** Names of the scoreboard teams. */
    TEAMS,
    /** Names of the scoreboard objectives. */
    OBJECTIVES
}

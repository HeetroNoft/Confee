package fr.heetstudio.confee.ui;

import fr.heetstudio.confee.api.SuggestionSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Values of the built-in suggestion sources ({@link SuggestionSource}), read from the client world.
 * <p>Internal: may change without notice.</p>
 */
public final class WorldSuggestions {

    private WorldSuggestions() {
    }

    /** Values of the sources, sorted; empty without a world. */
    public static Collection<String> values(List<SuggestionSource> sources) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || sources.isEmpty()) {
            return List.of();
        }
        Set<String> out = new TreeSet<>();
        for (SuggestionSource source : sources) {
            switch (source) {
                case SCOREBOARD_TAGS -> {
                    for (Entity entity : level.entitiesForRendering()) {
                        out.addAll(entity.getTags());
                    }
                }
                case TEAMS -> level.getScoreboard().getPlayerTeams().forEach(team -> out.add(team.getName()));
                case OBJECTIVES -> out.addAll(level.getScoreboard().getObjectiveNames());
            }
        }
        return out;
    }
}

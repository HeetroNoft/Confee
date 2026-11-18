package fr.heetstudio.confee.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SuggestionsTest {

    private static final List<String> IDS = List.of("minecraft:diamond", "minecraft:diamond_axe", "minecraft:dirt",
            "minecraft:blue_diamond_ore", "mymod:raw_diamond", "minecraft:redstone");

    @Test
    void startsWithComesFirstThenWordStartThenContains() {
        assertEquals(List.of("minecraft:diamond", "minecraft:diamond_axe", "mymod:raw_diamond", "minecraft:blue_diamond_ore"),
                Suggestions.filter(IDS, "diamond", 10));
    }

    @Test
    void lettersInOrderAlsoMatch() {
        assertEquals("minecraft:diamond", Suggestions.filter(IDS, "dmd", 10).get(0));
        assertTrue(Suggestions.filter(IDS, "zzz", 10).isEmpty());
    }

    @Test
    void emptyTextListsEverythingUpToTheLimit() {
        assertEquals(3, Suggestions.filter(IDS, "", 3).size());
    }
}

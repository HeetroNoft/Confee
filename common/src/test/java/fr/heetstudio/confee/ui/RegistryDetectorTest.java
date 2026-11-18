package fr.heetstudio.confee.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Guessing the registry of other mods' values (spec 8.2 bis), with fake registries. */
class RegistryDetectorTest {

    private static final Map<String, List<String>> REGISTRIES = Map.of(
            "minecraft:item", List.of("minecraft:chest", "minecraft:diamond", "minecraft:stone", "minecraft:tnt", "mymod:gem"),
            "minecraft:block", List.of("minecraft:chest", "minecraft:grass", "minecraft:stone", "minecraft:tnt"),
            "minecraft:entity_type", List.of("minecraft:allay", "minecraft:creeper", "minecraft:zombie", "mymod:golem"),
            "minecraft:worldgen/biome", List.of("minecraft:desert", "minecraft:plains", "mymod:crystal_caves"),
            "minecraft:enchantment", List.of("minecraft:mending"),
            "minecraft:mob_effect", List.of("minecraft:speed"));

    private static final Function<String, List<String>> KNOWN = r -> REGISTRIES.getOrDefault(r, List.of());

    private static RegistryDetector.Detected detect(String key, Object value) {
        return RegistryDetector.detect(key, value, KNOWN);
    }

    @Test
    void valuesFindTheirRegistryWhenTheKeySaysNothing() {
        assertEquals(new RegistryDetector.Detected("minecraft:item", false), detect("blacklist", List.of("minecraft:tnt", "mymod:gem")));
        assertEquals(new RegistryDetector.Detected("minecraft:entity_type", false), detect("favorite", "mymod:golem"));
        assertEquals(new RegistryDetector.Detected("minecraft:enchantment", false), detect("bonus", "minecraft:mending"));
    }

    @Test
    void theKeyNameWinsWhenTheValuesFit() {
        // stone is both an item and a block: the key decides
        assertEquals("minecraft:block", detect("hiddenBlocks", List.of("minecraft:stone")).registry());
        assertEquals("minecraft:worldgen/biome", detect("eventBiome", "mymod:crystal_caves").registry());
        assertEquals("minecraft:entity_type", detect("spawnBlacklist_mobs", List.of("minecraft:creeper")).registry());
    }

    @Test
    void aKeyHintIsSkippedWhenTheValuesDoNotFit() {
        // "blockEntities": "entit" comes first, but chests are blocks, not entities
        assertEquals("minecraft:block", detect("blockEntities", List.of("minecraft:chest")).registry());
    }

    @Test
    void emptyListsRelyOnTheKeyOnly() {
        assertEquals("minecraft:item", detect("itemBlacklist", List.of()).registry());
        assertSame(RegistryDetector.NONE, detect("values", List.of()));
    }

    @Test
    void tagsAreAcceptedAndRemembered() {
        RegistryDetector.Detected d = detect("replacedOres", List.of("#minecraft:coal_ores", "minecraft:stone"));
        assertEquals("minecraft:item", d.registry());
        assertTrue(d.tags());
        assertEquals("minecraft:block", detect("blocks", List.of("#minecraft:logs")).registry());
    }

    @Test
    void textThatIsNotAnIdIsLeftAlone() {
        assertSame(RegistryDetector.NONE, detect("playerName", "Steve"));
        assertSame(RegistryDetector.NONE, detect("item", "diamond"));             // no namespace
        assertSame(RegistryDetector.NONE, detect("blacklist", List.of("minecraft:stone", "hello world")));
        assertSame(RegistryDetector.NONE, detect("blacklist", List.of("othermod:unknown")));
        assertSame(RegistryDetector.NONE, detect("color", "#FF8800"));            // a color, not a tag
    }
}

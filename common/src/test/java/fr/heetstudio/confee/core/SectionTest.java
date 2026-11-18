package fr.heetstudio.confee.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SectionTest {

    private static Option<Integer> intOption(String key) {
        return Option.<Integer>builder(key, OptionType.INT).defaultValue(0).build();
    }

    @Test
    void pathsFollowNesting() {
        Section root = Section.root(null);
        Option<Integer> top = root.add(intOption("top"));
        Section dash = root.add(new Section("dash", null, null));
        Option<Integer> distance = dash.add(intOption("distance"));

        assertEquals("top", top.path());
        assertEquals("dash", dash.path());
        assertEquals("dash.distance", distance.path());
        assertSame(distance, root.findOption("dash.distance").orElseThrow());
        assertTrue(root.findOption("dash.missing").isEmpty());
        assertTrue(root.findOption("dash").isEmpty());
    }

    @Test
    void pathsAreUpdatedWhenSectionIsAttachedAfterwards() {
        Section inner = new Section("inner", null, null);
        Option<Integer> leaf = inner.add(intOption("leaf"));
        Section outer = new Section("outer", null, null);
        outer.add(inner);
        Section.root(null).add(outer);
        assertEquals("outer.inner.leaf", leaf.path());
    }

    @Test
    void declarationOrderIsKept() {
        Section root = Section.root(null);
        root.add(intOption("b"));
        Section s = root.add(new Section("s", null, null));
        s.add(intOption("c"));
        root.add(intOption("a"));
        List<String> order = new ArrayList<>();
        root.forEachOption(o -> order.add(o.path()));
        assertEquals(List.of("b", "s.c", "a"), order);
    }

    @Test
    void duplicateKeysAreRejected() {
        Section root = Section.root(null);
        root.add(intOption("x"));
        assertThrows(IllegalArgumentException.class, () -> root.add(intOption("x")));
    }
}

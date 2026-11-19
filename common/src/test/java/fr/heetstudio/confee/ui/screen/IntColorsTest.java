package fr.heetstudio.confee.ui.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntColorsTest {

    @Test
    void detectsColorKeys() {
        assertTrue(IntColors.isColorKey("barColor"));
        assertTrue(IntColors.isColorKey("TEXT_COLOUR"));
        assertTrue(IntColors.isColorKey("couleurFond"));
        assertTrue(IntColors.isColorKey("waterTint"));
        assertFalse(IntColors.isColorKey("count"));
    }

    @Test
    void rangeAndAlpha() {
        assertEquals(0xFFFF0000, IntColors.argb(0xFF0000));
        assertFalse(IntColors.hasAlpha(0xFF0000));
        assertEquals(0x80FF0000, IntColors.argb(0x80FF0000L));
        assertTrue(IntColors.hasAlpha(0x80FF0000L));
        assertEquals(-16776961, IntColors.argb(-16776961));
        assertTrue(IntColors.hasAlpha(-1));
        assertNull(IntColors.argb(0x1_0000_0000L));
        assertNull(IntColors.argb((long) Integer.MIN_VALUE - 1));
        assertNull(IntColors.argb("#FF0000"));
    }

    @Test
    void writesTheSameKindOfInteger() {
        assertEquals(0x00FF00, IntColors.toValue(0xFF00FF00, false, false, false));
        assertEquals(0x00FF00L, IntColors.toValue(0xFF00FF00, false, false, true));
        assertEquals(0x8000FF00, IntColors.toValue(0x8000FF00, true, true, false));
        assertEquals(0x8000FF00L, IntColors.toValue(0x8000FF00, true, false, true));
        assertEquals((long) 0x8000FF00, IntColors.toValue(0x8000FF00, true, true, true));
    }
}

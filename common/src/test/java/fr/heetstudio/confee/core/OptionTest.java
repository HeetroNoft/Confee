package fr.heetstudio.confee.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptionTest {

    enum Difficulty { EASY, NORMAL, HARD }

    private static Option<Double> dashDistance() {
        return Option.<Double>builder("distance", OptionType.DOUBLE)
                .defaultValue(4.0).range(1.0, 10.0).step(0.5).widgetHint(WidgetHint.SLIDER).build();
    }

    @Test
    void outOfRangeIsClampedToNearestBound() {
        Option<Double> o = dashDistance();
        Validated<Double> high = o.validate(42.0);
        assertEquals(Validated.Status.CORRECTED, high.status());
        assertEquals(10.0, high.value());
        assertEquals(1.0, o.validate(-3).value());
        assertEquals(1.0, o.validate("0.5").value());
    }

    @Test
    void wrongTypeFallsBackToDefault() {
        Option<Double> o = dashDistance();
        Validated<Double> v = o.validate("not a number");
        assertEquals(Validated.Status.REPLACED_BY_DEFAULT, v.status());
        assertEquals(4.0, v.value());
        assertEquals(Validated.Status.REPLACED_BY_DEFAULT, o.validate(null).status());
        assertEquals(Validated.Status.REPLACED_BY_DEFAULT, o.validate(true).status());
    }

    @Test
    void integersRejectFractions() {
        Option<Integer> o = Option.<Integer>builder("count", OptionType.INT).defaultValue(20).build();
        assertEquals(7, o.validate(7L).value());
        assertEquals(7, o.validate(7.0).value());
        assertEquals(7, o.validate("7").value());
        assertEquals(Validated.Status.REPLACED_BY_DEFAULT, o.validate(7.5).status());
        assertEquals(Validated.Status.REPLACED_BY_DEFAULT, o.validate(Long.MAX_VALUE).status());
    }

    @Test
    void booleansAcceptOnlyTrueOrFalse() {
        Option<Boolean> o = Option.<Boolean>builder("showHud", OptionType.BOOLEAN).defaultValue(true).build();
        assertEquals(false, o.validate("FALSE").value());
        assertEquals(Validated.Status.REPLACED_BY_DEFAULT, o.validate("yes").status());
        assertEquals(Validated.Status.REPLACED_BY_DEFAULT, o.validate(1).status());
    }

    @Test
    void enumsMatchNamesCaseInsensitively() {
        Option<Difficulty> o = Option.<Difficulty>builder("difficulty", OptionType.ENUM)
                .enumClass(Difficulty.class).defaultValue(Difficulty.NORMAL).build();
        assertEquals(Difficulty.HARD, o.validate("hard").value());
        assertEquals(Difficulty.EASY, o.validate(Difficulty.EASY).value());
        assertEquals(Difficulty.NORMAL, o.validate("IMPOSSIBLE").value());
    }

    @Test
    void listsValidateEveryElement() {
        Option<List<String>> o = Option.<List<String>>builder("blacklist", OptionType.LIST)
                .elementType(OptionType.STRING).defaultValue(List.of()).build();
        assertEquals(List.of("a", "b"), o.validate(new ArrayList<>(List.of("a", "b"))).value());
        Validated<List<String>> bad = o.validate(List.of("a", 3));
        assertEquals(Validated.Status.REPLACED_BY_DEFAULT, bad.status());
        assertEquals(List.of(), bad.value());
    }

    @Test
    void colorsParseHexForms() {
        Option<Integer> o = Option.<Integer>builder("tint", OptionType.COLOR).defaultValue(0xFFFFFFFF).build();
        assertEquals(0xFF2D7DD2, o.validate("#2D7DD2").value());
        assertEquals(0x802D7DD2, o.validate("0x802D7DD2").value());
        assertEquals(Validated.Status.REPLACED_BY_DEFAULT, o.validate("#12345").status());
    }

    @Test
    void stringsCheckPatternAndLength() {
        Option<String> o = Option.<String>builder("code", OptionType.STRING)
                .defaultValue("abc").pattern("[a-z]+").maxLength(5).build();
        assertTrue(o.validate("hello").isOk());
        assertEquals("abc", o.validate("HELLO").value());
        assertEquals("abc", o.validate("toolong").value());
    }

    @Test
    void identifiersAreChecked() {
        Option<String> o = Option.<String>builder("item", OptionType.IDENTIFIER)
                .defaultValue("minecraft:stone").build();
        assertTrue(o.validate("mymod:some/path").isOk());
        assertTrue(o.validate("dirt").isOk());
        assertFalse(o.validate("Not Valid!").isOk());
    }

    @Test
    void setClampsAndNotifiesListenersOnlyOnChange() {
        Option<Double> o = dashDistance();
        List<String> calls = new ArrayList<>();
        o.addListener((oldV, newV) -> calls.add(oldV + "->" + newV));
        o.set(99.0);
        assertEquals(10.0, o.get());
        o.set(10.0);
        o.reset();
        assertEquals(List.of("4.0->10.0", "10.0->4.0"), calls);
        assertTrue(o.isDefault());
    }

    @Test
    void setRejectsInvalidValueAndKeepsCurrent() {
        Option<Integer> o = Option.<Integer>builder("count", OptionType.INT).defaultValue(20).build();
        o.set(30);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Option<Object> raw = (Option) o;
        raw.set("garbage");
        assertEquals(30, o.get());
    }

    @Test
    void failingListenerDoesNotBreakTheOthers() {
        Option<Integer> o = Option.<Integer>builder("count", OptionType.INT).defaultValue(1).build();
        List<Integer> seen = new ArrayList<>();
        o.addListener((a, b) -> { throw new RuntimeException("boom"); });
        o.addListener((a, b) -> seen.add(b));
        o.set(2);
        assertEquals(List.of(2), seen);
    }

    @Test
    void inconsistentDeclarationsFailWithClearMessage() {
        IllegalStateException slider = assertThrows(IllegalStateException.class, () ->
                Option.<Double>builder("x", OptionType.DOUBLE).defaultValue(1.0)
                        .widgetHint(WidgetHint.SLIDER).build());
        assertTrue(slider.getMessage().contains("slider"));

        assertThrows(IllegalStateException.class, () ->
                Option.<String>builder("x", OptionType.STRING).defaultValue("a").range(0, 1).build());
        assertThrows(IllegalStateException.class, () ->
                Option.<Integer>builder("x", OptionType.INT).defaultValue(50).range(0, 10).build());
        assertThrows(IllegalStateException.class, () ->
                Option.<Object>builder("x", OptionType.ENUM).build());
        assertThrows(IllegalArgumentException.class, () ->
                Option.<Object>builder("a.b", OptionType.INT));
    }
}

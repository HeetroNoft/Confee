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

    // ---------------------------------------------------------------- duplicates in lists

    private static Option<List<String>> blocks(boolean allowDuplicates) {
        return Option.<List<String>>builder("blocks", OptionType.LIST).elementType(OptionType.STRING)
                .defaultValue(List.of()).allowDuplicates(allowDuplicates).build();
    }

    @Test
    void listDuplicatesAreRemovedKeepingTheFirstOccurrence() {
        Validated<List<String>> v = blocks(false).validate(List.of("minecraft:dirt", "minecraft:stone", "minecraft:dirt"));
        assertEquals(Validated.Status.CORRECTED, v.status());
        assertEquals(List.of("minecraft:dirt", "minecraft:stone"), v.value());
        assertTrue(blocks(false).validate(List.of("a", "b")).isOk());
    }

    @Test
    void allowDuplicatesKeepsThem() {
        Option<List<String>> o = blocks(true);
        Validated<List<String>> v = o.validate(List.of("minecraft:dirt", "minecraft:dirt"));
        assertTrue(v.isOk());
        assertEquals(List.of("minecraft:dirt", "minecraft:dirt"), v.value());
        assertEquals(List.of("x", "x"), o.normalizeList(List.of("x", "x")));
        assertEquals(List.of("x"), blocks(false).normalizeList(List.of("x", "x")));
    }

    @Test
    void allowDuplicatesOnANonListIsRefused() {
        assertThrows(IllegalStateException.class, () -> Option.<String>builder("s", OptionType.STRING)
                .defaultValue("").allowDuplicates(true).build());
    }

    // ---------------------------------------------------------------- list options

    private static Option.Builder<List<Object>> list(OptionType element, List<?> defaults) {
        return Option.<List<Object>>builder("l", OptionType.LIST).elementType(element)
                .defaultValue(List.copyOf(defaults));
    }

    @Test
    void sizeTruncatesLongListsAndRefusesShortOnes() {
        Option<List<Object>> o = list(OptionType.STRING, List.of("a")).size(1, 2).build();
        Validated<List<Object>> long3 = o.validate(List.of("x", "y", "z"));
        assertEquals(Validated.Status.CORRECTED, long3.status());
        assertEquals(List.of("x", "y"), long3.value());
        Validated<List<Object>> empty = o.validate(List.of());
        assertEquals(Validated.Status.REPLACED_BY_DEFAULT, empty.status());
        assertEquals(List.of("a"), empty.value());
    }

    @Test
    void sortedListsAreSortedAndTheDefaultToo() {
        Option<List<Object>> o = list(OptionType.LONG, List.of(3L, 1L, 2L)).sorted(true).build();
        assertEquals(List.of(1L, 2L, 3L), o.getDefault());
        assertEquals(List.of(1L, 5L, 10L), o.validate(List.of(10, 1, 5)).value());
        assertEquals(List.of(1L, 5L), o.normalizeList(List.of(5L, 1L, 5L)));
    }

    @Test
    void ignoreCaseFindsDuplicatesWithoutCase() {
        Option<List<Object>> o = list(OptionType.STRING, List.of()).ignoreCase(true).build();
        assertEquals(List.of("Dirt", "stone"), o.validate(List.of("Dirt", "stone", "dirt")).value());
    }

    @Test
    void rangeAndPatternOnAListApplyToEachValue() {
        Option<List<Object>> range = list(OptionType.INT, List.of(1)).range(1, 64).build();
        assertNull(range.min(), "the list itself has no range");
        assertEquals(1, range.elementMin());
        Validated<List<Object>> v = range.validate(List.of(0, 100, 8));
        assertEquals(Validated.Status.CORRECTED, v.status());
        assertEquals(List.of(1, 64, 8), v.value());
        assertFalse(range.validateElement("100").isOk(), "the screen shows an error on the value");
        assertTrue(range.validateElement("8").isOk());

        Option<List<Object>> names = list(OptionType.STRING, List.of("steve")).pattern("[a-z]+").build();
        assertEquals(Validated.Status.REPLACED_BY_DEFAULT, names.validate(List.of("ok", "Not OK")).status());
        assertFalse(names.validateElement("Not OK").isOk());
    }

    @Test
    void misplacedListOptionsGiveClearErrors() {
        IllegalStateException size = assertThrows(IllegalStateException.class, () -> Option.<String>builder("s", OptionType.STRING)
                .defaultValue("").size(1, 3).build());
        assertTrue(size.getMessage().contains("@Size / size() is only allowed on List options"), size.getMessage());
        IllegalStateException ignoreCase = assertThrows(IllegalStateException.class,
                () -> list(OptionType.INT, List.of()).ignoreCase(true).build());
        assertTrue(ignoreCase.getMessage().contains("needs a List<String>"), ignoreCase.getMessage());
        IllegalStateException both = assertThrows(IllegalStateException.class,
                () -> list(OptionType.STRING, List.of()).ignoreCase(true).allowDuplicates(true).build());
        assertTrue(both.getMessage().contains("Fix: remove one of them"), both.getMessage());
        IllegalStateException regex = assertThrows(IllegalStateException.class,
                () -> list(OptionType.STRING, List.of()).pattern("[a-").build());
        assertTrue(regex.getMessage().contains("not a valid regular expression"), regex.getMessage());
        IllegalStateException rangeOnTexts = assertThrows(IllegalStateException.class,
                () -> list(OptionType.STRING, List.of()).range(1, 3).build());
        assertTrue(rangeOnTexts.getMessage().contains("Fix: for the number of values, use @Size"), rangeOnTexts.getMessage());
        Option<List<Object>> shortNames = list(OptionType.STRING, List.of("ab")).maxLength(3).build();
        assertFalse(shortNames.validateElement("abcd").isOk());
        assertEquals(0, shortNames.maxLength(), "the list itself has no max length");
        IllegalStateException badSize = assertThrows(IllegalStateException.class,
                () -> list(OptionType.STRING, List.of()).size(3, 1).build());
        assertTrue(badSize.getMessage().contains("0 <= min <= max"), badSize.getMessage());
        IllegalStateException defaults = assertThrows(IllegalStateException.class,
                () -> list(OptionType.STRING, List.of("a", "a")).build());
        assertTrue(defaults.getMessage().contains("Fix: remove the duplicates"), defaults.getMessage());
    }

    @Test
    void modIdNeedsTextAndHasNoTags() {
        Option<String> mod = Option.<String>builder("m", OptionType.STRING).defaultValue("sodium")
                .registry(PseudoRegistries.MOD).build();
        assertEquals(PseudoRegistries.MOD, mod.registry());
        IllegalStateException identifier = assertThrows(IllegalStateException.class,
                () -> Option.<String>builder("m", OptionType.IDENTIFIER).registry(PseudoRegistries.MOD).build());
        assertTrue(identifier.getMessage().contains("is not a ResourceLocation"), identifier.getMessage());
        IllegalStateException tags = assertThrows(IllegalStateException.class, () -> Option.<String>builder("m", OptionType.STRING)
                .defaultValue("").registry(PseudoRegistries.MOD).allowTags(true).build());
        assertTrue(tags.getMessage().contains("mods have no tags"), tags.getMessage());
    }

    @Test
    void replaceableModsComeFromTheirSource() {
        PseudoRegistries.register(PseudoRegistries.REPLACEABLE_MOD, () -> List.of("sodium", "iris"));
        assertEquals(List.of("sodium", "iris"), List.copyOf(PseudoRegistries.ids(PseudoRegistries.REPLACEABLE_MOD)));
        assertTrue(PseudoRegistries.isModList(PseudoRegistries.REPLACEABLE_MOD));
        assertFalse(PseudoRegistries.isPseudo("minecraft:item"));
    }

    @Test
    void pickerStylesResolveAndMistakesAreExplained() {
        Option<String> item = Option.<String>builder("i", OptionType.STRING).defaultValue("minecraft:stone")
                .registry("minecraft:item").build();
        assertEquals(fr.heetstudio.confee.api.PickerStyle.GRID, Pickers.defaultStyle(item, "minecraft:item"));
        assertEquals(Pickers.ALL, Pickers.allowed(item));
        Option<String> mod = Option.<String>builder("m", OptionType.STRING).defaultValue("sodium")
                .registry(PseudoRegistries.MOD).build();
        assertEquals(fr.heetstudio.confee.api.PickerStyle.LIST, Pickers.defaultStyle(mod, PseudoRegistries.MOD));

        Option<String> listOnly = Option.<String>builder("i", OptionType.STRING).defaultValue("minecraft:stone")
                .registry("minecraft:item")
                .pickerStyles(List.of(fr.heetstudio.confee.api.PickerStyle.LIST, fr.heetstudio.confee.api.PickerStyle.DETAILED))
                .build();
        assertEquals(fr.heetstudio.confee.api.PickerStyle.LIST, Pickers.defaultStyle(listOnly, "minecraft:item"),
                "AUTO (grid) is not allowed: the first allowed style");
        assertEquals(fr.heetstudio.confee.api.PickerStyle.DETAILED,
                Pickers.initial(listOnly, "minecraft:item", fr.heetstudio.confee.api.PickerStyle.DETAILED));
        assertEquals(fr.heetstudio.confee.api.PickerStyle.LIST,
                Pickers.initial(listOnly, "minecraft:item", fr.heetstudio.confee.api.PickerStyle.GRID),
                "a remembered style the dev does not allow is ignored");

        IllegalStateException noPicker = assertThrows(IllegalStateException.class, () -> Option.<String>builder("s", OptionType.STRING)
                .defaultValue("").pickerDefault(fr.heetstudio.confee.api.PickerStyle.LIST).build());
        assertTrue(noPicker.getMessage().contains("needs an ID option that has a picker"), noPicker.getMessage());
        IllegalStateException notAllowed = assertThrows(IllegalStateException.class, () -> Option.<String>builder("i", OptionType.STRING)
                .defaultValue("minecraft:stone").registry("minecraft:item")
                .pickerStyles(List.of(fr.heetstudio.confee.api.PickerStyle.LIST))
                .pickerDefault(fr.heetstudio.confee.api.PickerStyle.GRID).build());
        assertTrue(notAllowed.getMessage().contains("is not in the allowed styles"), notAllowed.getMessage());
        IllegalStateException auto = assertThrows(IllegalStateException.class, () -> Option.<String>builder("i", OptionType.STRING)
                .defaultValue("minecraft:stone").registry("minecraft:item")
                .pickerStyles(List.of(fr.heetstudio.confee.api.PickerStyle.AUTO)).build());
        assertTrue(auto.getMessage().contains("AUTO is not a picker style"), auto.getMessage());
    }

    @Test
    void reorderableIsForListsAndNotWithSorted() {
        Option<List<Object>> plain = list(OptionType.STRING, List.of()).build();
        assertFalse(plain.reorderable(), "no move buttons by default");
        assertTrue(list(OptionType.STRING, List.of()).reorderable(true).build().reorderable());
        IllegalStateException onText = assertThrows(IllegalStateException.class, () -> Option.<String>builder("s", OptionType.STRING)
                .defaultValue("").reorderable(true).build());
        assertTrue(onText.getMessage().contains("only allowed on List options"), onText.getMessage());
        IllegalStateException withSorted = assertThrows(IllegalStateException.class,
                () -> list(OptionType.STRING, List.of()).reorderable(true).sorted(true).build());
        assertTrue(withSorted.getMessage().contains("cannot be used together"), withSorted.getMessage());
    }

    @Test
    void filtersMustFitTheRegistry() {
        Option<String> monsters = Option.<String>builder("b", OptionType.STRING).defaultValue("minecraft:zombie")
                .registry("minecraft:entity_type").filters(List.of(fr.heetstudio.confee.api.EntityFilter.MONSTER)).build();
        assertEquals(List.of(fr.heetstudio.confee.api.EntityFilter.MONSTER), monsters.filters());
        // an ItemFilter works on blocks too (the block's item)
        Option.<String>builder("f", OptionType.STRING).defaultValue("minecraft:stone")
                .registry("minecraft:block").filters(List.of(fr.heetstudio.confee.api.ItemFilter.NATURAL_BLOCKS)).build();
        IllegalStateException wrong = assertThrows(IllegalStateException.class, () -> Option.<String>builder("b", OptionType.STRING)
                .defaultValue("minecraft:zombie").registry("minecraft:item")
                .filters(List.of(fr.heetstudio.confee.api.EntityFilter.MONSTER)).build());
        assertTrue(wrong.getMessage().contains("EntityFilter.MONSTER does not fit the registry minecraft:item"), wrong.getMessage());
        IllegalStateException none = assertThrows(IllegalStateException.class, () -> Option.<String>builder("b", OptionType.STRING)
                .defaultValue("x").filters(List.of(fr.heetstudio.confee.api.BiomeFilter.NETHER)).build());
        assertTrue(none.getMessage().contains("an option without registry"), none.getMessage());
    }
}

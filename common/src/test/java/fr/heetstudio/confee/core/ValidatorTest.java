package fr.heetstudio.confee.core;

import fr.heetstudio.confee.api.ConfigValue;
import fr.heetstudio.confee.api.ValueValidator;
import fr.heetstudio.confee.api.annotation.Config;
import fr.heetstudio.confee.api.annotation.Validator;
import fr.heetstudio.confee.api.builder.ConfigBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@code @Validator} / {@code OptionBuilder.validator}: the mod's own check of a value. */
class ValidatorTest {

    @TempDir
    Path dir;
    ConfeeEnvironment env;

    @BeforeEach
    void setUp() {
        ConfeeRegistry.clear();
        env = new ConfeeEnvironment(dir, true, true);
    }

    /** Accepts {@code {...}} only (a stand-in for an SNBT check). */
    public static class Braces implements ValueValidator<String> {
        @Override
        public String validate(String value) {
            return value.startsWith("{") && value.endsWith("}") ? null : "validatormod.error.braces";
        }
    }

    @Config(modId = "validatormod")
    public static class Annotated {
        @Validator(Braces.class)
        public String single = "{}";
        @Validator(Braces.class)
        public List<String> loot = new ArrayList<>(List.of("{a}"));
    }

    private Option<?> option(ConfigHandle<?> handle, String path) {
        return handle.schema().root().findOption(path).orElseThrow();
    }

    @Test
    void scalarValueIsChecked() {
        ConfigHandle<Annotated> handle = AnnotationConfigs.create(Annotated.class, env);
        Validated<?> ok = option(handle, "single").validate("{x}");
        assertTrue(ok.isOk());
        Validated<?> refused = option(handle, "single").validate("x");
        assertEquals(Validated.Status.REPLACED_BY_DEFAULT, refused.status());
        assertEquals("{}", refused.value());
        assertEquals("validatormod.error.braces", refused.reason());
    }

    @Test
    void eachValueOfAListIsChecked() {
        ConfigHandle<Annotated> handle = AnnotationConfigs.create(Annotated.class, env);
        Option<?> loot = option(handle, "loot");
        assertTrue(loot.validate(List.of("{a}", "{b}")).isOk());
        assertEquals("validatormod.error.braces", loot.validateElement("b").reason());
        assertTrue(loot.validateElement("{b}").isOk());
        assertEquals(List.of("{a}"), loot.validate(List.of("{a}", "b")).value());
    }

    @Test
    void refusedValueInTheFileUsesTheDefault() throws Exception {
        Files.writeString(dir.resolve("validatormod.toml"), "single = \"nope\"\nloot = [\"{x}\"]\n");
        ConfigHandle<Annotated> handle = AnnotationConfigs.create(Annotated.class, env);
        handle.load();
        assertEquals("{}", handle.get().single);
        assertEquals(List.of("{x}"), handle.get().loot);
    }

    @Test
    void builderValidatorAndBrokenValidator() {
        ConfigBuilder b = ConfigBuilder.create("buildermod", env);
        ConfigValue<Integer> even = b.intValue("even", 2).validator((Integer i) -> i % 2 == 0 ? null : "Must be even").build();
        ConfigValue<String> broken = b.string("broken", "a").validator((String s) -> {
            throw new IllegalStateException("bug in the mod");
        }).build();
        b.build();
        even.set(3);
        assertEquals(2, even.get(), "refused: the value is kept");
        even.set(4);
        assertEquals(4, even.get());
        broken.set("b");
        assertEquals("b", broken.get(), "a validator that throws accepts the value");
    }
}

package fr.heetstudio.confee.core;

import fr.heetstudio.confee.api.ConfigValue;
import fr.heetstudio.confee.api.builder.ConfigBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The development report lists the widget and the constraints of each option, and the missing translations. */
class DevReportTest {

    enum Mode { FAST, SLOW }

    @TempDir
    Path dir;

    @BeforeEach
    void setUp() {
        ConfeeRegistry.clear();
    }

    @Test
    void reportDescribesEveryOption() {
        ConfigBuilder b = ConfigBuilder.create("reportmod", new ConfeeEnvironment(dir, true, true));
        b.bool("showHud", true).build();
        b.doubleRange("speed", 1.0, 0.0, 5.0).slider(0.5).build();
        b.enumValue("mode", Mode.FAST).build();
        b.itemList("loot", "minecraft:diamond").maxSize(9).build();
        b.list("stacks", Integer.class, List.of(16)).range(1, 64).sorted().build();
        ConfigHandle<?> handle = (ConfigHandle<?>) b.build();
        String report = DevReport.build(handle.schema(), null);

        assertTrue(report.contains("showHud") && report.contains("toggle"), report);
        assertTrue(report.contains("slider") && report.contains("range 0.0 ~ 5.0") && report.contains("step 0.5"), report);
        assertTrue(report.contains("segmented buttons (2 values)"), report);
        assertTrue(report.contains("list of string IDs") && report.contains("size 0 ~ 9")
                && report.contains("registry minecraft:item"), report);
        assertTrue(report.contains("each value 1 ~ 64") && report.contains("sorted"), report);
        assertTrue(report.contains("no assets/reportmod/lang/en_us.json") && report.contains("\"Show Hud\""), report);
    }
}

package fr.heetstudio.confee.testmod;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.platform.Services;
import fr.heetstudio.confee.ui.ConfeeScreens;
import fr.heetstudio.confee.ui.screen.ConfigScreen;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Development helper shared by the Fabric and Forge test mods. With {@code -Dconfee.testmod.screenshots=true}:
 * checks that the loader's mod list gives each mod its Config button, opens every Confee screen, drives it with real
 * input events, saves screenshots in {@code screenshots/confee-*.png} and quits.
 */
public final class ScreenshotScript {

    /** {@code until}: the step is repeated (every 5 ticks, at most {@code waitTicks} times) until it holds. */
    private record Step(int waitTicks, Consumer<Minecraft> action, @org.jetbrains.annotations.Nullable java.util.function.Predicate<Minecraft> until) {
    }

    private int attempts;

    /** Asks the loader's mod list (Mod Menu / Forge) which screen its Config button opens for a mod (null = no button). */
    private final java.util.function.BiFunction<String, Screen, Screen> configScreen;
    private final List<Step> steps = new ArrayList<>();
    private int index = -1;
    private int wait;
    private Screen fileSelect;
    private Screen rawSelect;

    public ScreenshotScript(java.util.function.BiFunction<String, Screen, Screen> configScreen) {
        this.configScreen = configScreen;
        script();
    }

    public static boolean enabled() {
        return Boolean.getBoolean("confee.testmod.screenshots");
    }

    /** Call at the end of every client tick. */
    public void tick(Minecraft mc) {
        if (index < 0) {
            if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) {
                return;
            }
            index = 0;
            wait = 40;
            return;
        }
        if (wait-- > 0 || index >= steps.size()) {
            return;
        }
        Step step = steps.get(index);
        if (step.until() != null) {
            if (!step.until().test(mc) && attempts++ < step.waitTicks()) {
                wait = 5;
                return;
            }
            attempts = 0;
            index++;
            step.action().accept(mc);
            wait = 5;
            return;
        }
        index++;
        step.action().accept(mc);
        wait = step.waitTicks();
    }

    private boolean forge() {
        return Services.PLATFORM.getPlatformName().equals("Forge");
    }

    private void script() {
        step(5, mc -> {
            // spec 8.1 / 15: Confee screens for Confee and raw mods; mods with their own screen keep it
            for (String id : List.of("confee", ConfeeTestmod.MOD_ID, "confee_rawmod", "forge", "minecraft", "modmenu",
                    "cloth-config", "cloth_config", "yet_another_config_lib_v3")) {
                if (Services.PLATFORM.isModLoaded(id)) {
                    Screen screen;
                    try {
                        screen = configScreen.apply(id, mc.screen);
                    } catch (RuntimeException | LinkageError e) {
                        // a broken third-party screen must not stop the checks
                        ConfeeLog.error("[testmod] Config button of " + id + " failed", e instanceof RuntimeException re ? re : new RuntimeException(e));
                        continue;
                    }
                    ConfeeLog.info("[testmod] mod list Config button for {}: {}", id,
                            screen == null ? "none" : screen.getClass().getName());
                }
            }
        });
        step(15, mc -> {
            scale(mc, 2);
            fileSelect = ConfeeScreens.forMod(mc.screen, ConfeeTestmod.MOD_ID);
            mc.setScreen(fileSelect);
        });
        shot("01-file-select");
        step(15, mc -> mc.setScreen(ConfeeTestmod.annotated.createScreen(fileSelect)));
        shot("02-general");
        step(10, mc -> mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, -3));
        shot("03-general-scrolled");
        step(10, mc -> key(mc, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_CONTROL));
        shot("04-dash");
        step(10, mc -> {
            key(mc, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_CONTROL);
            key(mc, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_CONTROL);
        });
        shot("05-advanced");
        step(10, mc -> {
            key(mc, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_CONTROL);
            config(mc).focusOption("volume");
            type(mc, "500");
        });
        shot("06-field-error");
        step(10, mc -> {
            config(mc).focusOption("quality");
            key(mc, GLFW.GLFW_KEY_ENTER, 0);
        });
        shot("07-dropdown");
        step(10, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            config(mc).focusOption("title");
            type(mc, "!");
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
        });
        shot("08-unsaved-modal");
        step(10, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            key(mc, GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL);
            type(mc, "dash");
        });
        shot("09-search");
        // preview column, conditions, collections, specialized fields (builder config)
        step(15, mc -> {
            mc.setScreen(ConfeeTestmod.builder.createScreen(fileSelect));
            config(mc).focusOption("quality");
        });
        shot("29-preview-image");
        step(10, mc -> config(mc).focusOption("particleCount"));
        shot("30-preview-renderer");
        step(10, mc -> {
            config(mc).focusOption("showHud");
            key(mc, GLFW.GLFW_KEY_ENTER, 0);
            config(mc).focusOption("showHud");
        });
        shot("31-condition-disabled");
        step(10, mc -> {
            config(mc).focusOption("item");
            for (int i = 0; i < 20; i++) {
                key(mc, GLFW.GLFW_KEY_BACKSPACE, 0);
            }
            type(mc, "dia");
        });
        shot("32-registry-suggestions");
        step(10, mc -> {
            config(mc).focusOption("motd");
        });
        shot("33-multiline");
        step(10, mc -> config(mc).focusOption("allowedDifficulties"));
        shot("34-enum-list");
        step(10, mc -> config(mc).focusOption("biomeWeights"));
        shot("35-map");
        step(10, mc -> {
            config(mc).focusOption("waypoints");
            mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, -10);
        });
        shot("36-object-list");

        // customization: banner, header, info, description, icons (annotation config)
        step(15, mc -> mc.setScreen(ConfeeTestmod.annotated.createScreen(fileSelect)));
        shot("40-general-top");
        step(10, mc -> {
            config(mc).focusOption("item");
            for (int i = 0; i < 20; i++) {
                key(mc, GLFW.GLFW_KEY_BACKSPACE, 0);
            }
            type(mc, "dmnd");
        });
        step(3, mc -> {
            // mouse over the suggestion list: the preview must stay on "item", not the option under the list
            ConfeeWidget w = config(mc).focusedWidget();
            int[] list = w.floatingBounds();
            if (list != null) {
                moveMouse(mc, list[0] + 40, list[1] + 30);
            }
        });
        shot("41-suggestions-icons");
        step(10, mc -> {
            moveMouse(mc, 2, 2);
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            clickRightEdge(mc);
            type(mc, "ore");
        });
        shot("42-item-picker");
        step(10, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            config(mc).focusOption("tint");
            clickRightEdge(mc);
        });
        shot("43-color-picker");
        step(10, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            config(mc).focusOption("allowedQualities");
            key(mc, GLFW.GLFW_KEY_RIGHT, 0);
            key(mc, GLFW.GLFW_KEY_SPACE, 0);
        });
        shot("44-checkboxes");
        step(10, mc -> {
            config(mc).focusOption("dashKey");
            key(mc, GLFW.GLFW_KEY_ENTER, 0);
        });
        shot("45-keybind-listening");
        step(10, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            config(mc).focusOption("lives");
            key(mc, GLFW.GLFW_KEY_UP, 0);
            key(mc, GLFW.GLFW_KEY_UP, 0);
            config(mc).focusOption("temperature");
            key(mc, GLFW.GLFW_KEY_RIGHT, 0);
        });
        shot("46-stepper-custom-widget");
        step(5, mc -> {
            // hover the name of "volume" (grayed out by its condition after showHud is turned off)
            config(mc).focusOption("showHud");
            key(mc, GLFW.GLFW_KEY_ENTER, 0);
        });
        step(5, mc -> mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, -1));
        step(5, mc -> {
            ConfeeWidget w = config(mc).focusedWidget();
            // the toggle of showHud is focused; "volume" is two rows below, its name just above its field
            moveMouse(mc, w.x + 12, w.y + 82);
        });
        step(15, mc -> { });
        shot("47-tooltip");
        step(5, mc -> moveMouse(mc, 2, 2));
        // typed registries: entities (spawn eggs), biome tags outside a world, effect picker
        step(10, mc -> {
            config(mc).focusOption("favoriteMob");
            for (int i = 0; i < 30; i++) {
                key(mc, GLFW.GLFW_KEY_BACKSPACE, 0);
            }
            type(mc, "cre");
        });
        shot("48-entity-suggestions");
        step(10, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            config(mc).focusOption("spawnBiomes");
            for (int i = 0; i < 30; i++) {
                key(mc, GLFW.GLFW_KEY_BACKSPACE, 0);
            }
            type(mc, "#minecraft:is_m");
        });
        shot("49-biome-tags");
        step(10, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            config(mc).focusOption("bonusEffect");
        });
        step(5, mc -> {
            // after the panel scrolled to the field
            clickRightEdge(mc);
            type(mc, "sp");
        });
        shot("50-effect-picker");
        step(5, mc -> key(mc, GLFW.GLFW_KEY_ESCAPE, 0));
        step(15, mc -> mc.setScreen(ConfeeTestmod.themed.createScreen(fileSelect)));
        shot("10-themed");

        // universal editor (spec 8) on the raw test mod, which does not use Confee
        step(15, mc -> {
            rawSelect = ConfeeScreens.forMod(mc.screen, "confee_rawmod");
            mc.setScreen(rawSelect);
        });
        shot("14-raw-file-select");
        step(15, mc -> mc.setScreen(ConfeeScreens.openExternal(rawSelect, "confee_rawmod", rawFile(mc))));
        shot("15-raw-general");
        // other mods' files: IDs and colors are recognized
        step(10, mc -> {
            config(mc).focusOption("spawnBlacklist");
            type(mc, "a");
        });
        shot("51-raw-detected");
        step(10, mc -> {
            key(mc, GLFW.GLFW_KEY_BACKSPACE, 0);
            config(mc).focusOption("highlight");
        });
        step(5, mc -> clickRightEdge(mc));
        shot("52-raw-color-picker");
        step(5, mc -> key(mc, GLFW.GLFW_KEY_ESCAPE, 0));
        step(10, mc -> key(mc, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_CONTROL));
        shot("16-raw-render");
        step(10, mc -> {
            key(mc, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_CONTROL);
            key(mc, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_CONTROL);
            config(mc).focusOption("maxParticles");
            type(mc, "0");
            // Escape -> "unsaved changes" -> Enter on the primary "Save"
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            key(mc, GLFW.GLFW_KEY_ENTER, 0);
        });
        step(10, mc -> {
            logLines(rawFile(mc), "maxParticles");
            ConfeeLog.info("[testmod] backup exists: {}",
                    Files.exists(mc.gameDirectory.toPath().resolve("config/confee/backups/confee_rawmod.toml")));
            mc.setScreen(ConfeeScreens.openExternal(rawSelect, "confee_rawmod", rawFile(mc)));
        });
        shot("18-raw-after-save");
        step(15, mc -> mc.setScreen(ConfeeScreens.openExternal(rawSelect, "confee_rawmod",
                mc.gameDirectory.toPath().resolve("config/confee_rawmod/notes.txt"))));
        shot("19-unreadable");
        // other formats (spec 7.1)
        step(15, mc -> mc.setScreen(ConfeeScreens.forMod(fileSelect, "confee_rawmod")));
        shot("25-all-formats");
        step(15, mc -> mc.setScreen(ConfeeScreens.openExternal(rawSelect, "confee_rawmod",
                mc.gameDirectory.toPath().resolve("config/confee_rawmod/settings.yml"))));
        shot("26-yaml");
        step(15, mc -> {
            mc.setScreen(ConfeeScreens.openExternal(rawSelect, "confee_rawmod",
                    mc.gameDirectory.toPath().resolve("config/confee_rawmod/legacy.cfg")));
        });
        step(5, mc -> key(mc, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_CONTROL));
        step(5, mc -> {
            config(mc).focusOption("maxParticles");
            type(mc, "0");
        });
        shot("27-forge-cfg-bounds");
        step(15, mc -> mc.setScreen(ConfeeScreens.openExternal(rawSelect, "confee_rawmod",
                mc.gameDirectory.toPath().resolve("config/confee_rawmod-server.properties"))));
        shot("28-properties");
        step(15, mc -> mc.setScreen(ConfeeScreens.forMod(fileSelect, "confee")));
        shot("20-confee-settings");

        if (forge()) {
            forgeSteps();
        }
        worldSteps();

        step(15, mc -> {
            scale(mc, 3);
            mc.setScreen(ConfeeTestmod.annotated.createScreen(fileSelect));
        });
        shot("12-scale3");
        step(20, mc -> {
            // narrow window: the sidebar folds into a category dropdown (spec 10.6)
            mc.getWindow().setWindowed(640, 720);
            mc.resizeDisplay();
        });
        shot("13-narrow-collapsed");
        step(5, Minecraft::stop);
    }

    /**
     * Spec 10.8: in a world, world registries (biomes) and tags come from the world itself. Creates a flat creative
     * world "confee-test" (deleted first), compares with the lists read from the files, then leaves the world.
     */
    private void worldSteps() {
        String biomes = "minecraft:worldgen/biome";
        int[] offWorld = new int[2];
        step(5, mc -> {
            offWorld[0] = fr.heetstudio.confee.ui.RegistryEntries.ids(biomes).size();
            offWorld[1] = fr.heetstudio.confee.ui.RegistryEntries.tags(biomes).size();
            ConfeeLog.info("[testmod] outside a world: {} biomes, {} biome tags", offWorld[0], offWorld[1]);
            Path save = mc.gameDirectory.toPath().resolve("saves/confee-test");
            deleteTree(save);
            String name = "confee-test";
            mc.createWorldOpenFlows().createFreshLevel(name,
                    new net.minecraft.world.level.LevelSettings(name, net.minecraft.world.level.GameType.CREATIVE, false,
                            net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(),
                            net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(0L, false, false),
                    registries -> registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                            .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value()
                            .createWorldDimensions());
        });
        waitUntil(2400, mc -> mc.level != null && mc.player != null && mc.screen == null);
        step(40, mc -> {
            int inWorld = fr.heetstudio.confee.ui.RegistryEntries.ids(biomes).size();
            int inWorldTags = fr.heetstudio.confee.ui.RegistryEntries.tags(biomes).size();
            ConfeeLog.info("[testmod] in a world: {} biomes, {} biome tags (files: {}, {})", inWorld, inWorldTags,
                    offWorld[0], offWorld[1]);
            mc.setScreen(ConfeeTestmod.annotated.createScreen(null));
        });
        step(10, mc -> {
            config(mc).focusOption("spawnBiomes");
            for (int i = 0; i < 30; i++) {
                key(mc, GLFW.GLFW_KEY_BACKSPACE, 0);
            }
            type(mc, "#minecraft:is_");
        });
        shot("53-world-biome-tags");
        step(10, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            config(mc).focusOption("favoriteMob");
        });
        shot("54-world-preview");
        step(20, mc -> {
            // leave without saving the edits, then back to the title screen
            mc.setScreen(null);
            if (mc.level != null) {
                mc.level.disconnect();
            }
            mc.clearLevel();
            mc.setScreen(new TitleScreen());
        });
        waitUntil(600, mc -> mc.level == null && mc.getSingleplayerServer() == null);
    }

    private void waitUntil(int maxChecks, java.util.function.Predicate<Minecraft> condition) {
        steps.add(new Step(maxChecks, mc -> {
            if (!condition.test(mc)) {
                ConfeeLog.warn("[testmod] condition still false after waiting");
            }
        }, condition));
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    ConfeeLog.warn("[testmod] cannot delete {}", p);
                }
            });
        } catch (IOException e) {
            ConfeeLog.warn("[testmod] cannot delete {}", dir);
        }
    }

    /** Forge/NeoForge config specs (spec 8.4). */
    private void forgeSteps() {
        step(15, mc -> {
            rawSelect = ConfeeScreens.forMod(fileSelect, "confee_rawmod");
            mc.setScreen(rawSelect);
        });
        shot("21-forge-file-select");
        step(15, mc -> {
            // first entry: the CLIENT spec
            key(mc, GLFW.GLFW_KEY_ENTER, 0);
        });
        shot("22-forge-client-spec");
        step(10, mc -> {
            // zoom uses the GaugeWidget registered by the test mod for this Forge config
            config(mc).focusOption("zoom");
            key(mc, GLFW.GLFW_KEY_RIGHT, 0);
            key(mc, GLFW.GLFW_KEY_RIGHT, 0);
            key(mc, GLFW.GLFW_KEY_RIGHT, 0);
            config(mc).focusOption("mode");
            key(mc, GLFW.GLFW_KEY_RIGHT, 0);
        });
        shot("23-forge-edited");
        step(100, mc -> {
            // save, then give Forge's file watcher time to reload the config (spec 8.4: applied while running)
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            key(mc, GLFW.GLFW_KEY_ENTER, 0);
        });
        step(10, mc -> {
            logLines(mc.gameDirectory.toPath().resolve("config/confee_rawmod-forge-client.toml"), "zoom", "mode");
            mc.setScreen(ConfeeScreens.forMod(fileSelect, "forge"));
        });
        shot("24-forge-own-configs");
    }

    private void step(int waitTicks, Consumer<Minecraft> action) {
        steps.add(new Step(waitTicks, action, null));
    }

    private void shot(String name) {
        step(5, mc -> Screenshot.grab(mc.gameDirectory, "confee-" + name + ".png", mc.getMainRenderTarget(), message -> {
        }));
    }

    private static Path rawFile(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve("config/confee_rawmod.toml");
    }

    private static void logLines(Path file, String... prefixes) {
        try {
            for (String line : Files.readAllLines(file)) {
                for (String prefix : prefixes) {
                    if (line.trim().startsWith(prefix)) {
                        ConfeeLog.info("[testmod] {}: {}", file.getFileName(), line.trim());
                    }
                }
            }
        } catch (IOException e) {
            ConfeeLog.error("[testmod] cannot read " + file, e);
        }
    }

    /** Clicks the right end of the focused field (color square / picker button). */
    private static void clickRightEdge(Minecraft mc) {
        ConfeeWidget w = config(mc).focusedWidget();
        if (w != null) {
            double x = w.x + w.width - 4;
            double y = w.y + w.height / 2.0;
            mc.screen.mouseClicked(x, y, 0);
            mc.screen.mouseReleased(x, y, 0);
        }
    }

    /** Moves the cursor (GUI coordinates): GLFW position + the game's mouse handler (dev names). */
    private static void moveMouse(Minecraft mc, double guiX, double guiY) {
        double scale = mc.getWindow().getGuiScale();
        long window = mc.getWindow().getWindow();
        GLFW.glfwSetCursorPos(window, guiX * scale, guiY * scale);
        try {
            java.lang.reflect.Method onMove = net.minecraft.client.MouseHandler.class
                    .getDeclaredMethod("onMove", long.class, double.class, double.class);
            onMove.setAccessible(true);
            onMove.invoke(mc.mouseHandler, window, guiX * scale, guiY * scale);
        } catch (ReflectiveOperationException e) {
            ConfeeLog.warn("[testmod] cannot move the mouse: {}", e.toString());
        }
    }

    private static ConfigScreen config(Minecraft mc) {
        return (ConfigScreen) mc.screen;
    }

    private static void key(Minecraft mc, int key, int modifiers) {
        mc.screen.keyPressed(key, 0, modifiers);
    }

    private static void type(Minecraft mc, String text) {
        for (char c : text.toCharArray()) {
            mc.screen.charTyped(c, 0);
        }
    }

    private static void scale(Minecraft mc, int scale) {
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
    }
}

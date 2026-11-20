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
        // Forge and NeoForge share the config spec system (spec 8.4)
        return Services.PLATFORM.getPlatformName().endsWith("Forge");
    }

    private void script() {
        step(5, mc -> {
            // spec 8.1 / 15: Confee screens for Confee and raw mods; mods with their own screen keep it
            for (String id : List.of("confee", ConfeeTestmod.MOD_ID, "confee_rawmod", "forge", "neoforge", "minecraft", "modmenu",
                    "cloth-config", "cloth_config", "yet_another_config_lib_v3", "configured")) {
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
            // forceConfeeEditor: replaces the screen of a mod that has its own, read on each click (no restart)
            var forced = fr.heetstudio.confee.ConfeeSettings.forceConfeeEditorValue();
            var mappings = fr.heetstudio.confee.ConfeeSettings.fileMappingsValue();
            List<String> before = forced.get();
            List<String> mappingsBefore = mappings.get();
            List<String> mapped = new java.util.ArrayList<>(mappingsBefore);
            // Cloth Config has no config file: map one so that Confee can replace its screen (no restart needed)
            mapped.add("cloth-config=confee_rawmod.toml");
            mapped.add("cloth_config=confee_rawmod.toml");
            mappings.set(mapped);
            for (String id : Services.PLATFORM.getModsWithOwnConfigScreen()) {
                if (id.equals("confee")) {
                    continue;
                }
                try {
                    forced.set(List.of(id));
                    Screen on = configScreen.apply(id, mc.screen);
                    forced.set(List.of());
                    Screen off = configScreen.apply(id, mc.screen);
                    ConfeeLog.info("[testmod] forceConfeeEditor {}: on={} off={}", id,
                            on == null ? "none" : on.getClass().getName(), off == null ? "none" : off.getClass().getName());
                } catch (RuntimeException | LinkageError e) {
                    ConfeeLog.error("[testmod] forceConfeeEditor check of " + id + " failed", e instanceof RuntimeException re ? re : new RuntimeException(e));
                }
            }
            forced.set(before);
            mappings.set(mappingsBefore);
        });
        step(15, mc -> {
            scale(mc, 2);
            // pickers open on their default style (the styles remembered by a previous run are forgotten)
            fr.heetstudio.confee.ConfeeSettings.pickerStylesValue().set(List.of());
            fileSelect = ConfeeScreens.forMod(mc.screen, ConfeeTestmod.MOD_ID);
            mc.setScreen(fileSelect);
        });
        shot("01-file-select");
        step(15, mc -> mc.setScreen(ConfeeTestmod.annotated.createScreen(fileSelect)));
        shot("02-general");
        step(10, mc -> mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, -3));
        shot("03-general-scrolled");
        // the wheel over a text area that has nothing to scroll moves the panel (it used to get stuck)
        step(10, mc -> {
            config(mc).focusOption("motd");
        });
        int[] wheelBefore = {0};
        step(5, mc -> {
            ConfeeWidget w = config(mc).focusedWidget();
            wheelBefore[0] = w == null ? 0 : w.y;
            if (w != null) {
                mc.screen.mouseScrolled(w.x + w.width / 2.0, w.y + w.height / 2.0, 0, -2);
            }
        });
        step(5, mc -> {
            // positions are laid out again on the next frame
            ConfeeWidget after = config(mc).focusedWidget();
            ConfeeLog.info("[testmod] wheel over the text area: widget y {} -> {} (panel {})", wheelBefore[0],
                    after == null ? -1 : after.y, after != null && after.y != wheelBefore[0] ? "scrolled" : "DID NOT SCROLL");
        });
        shot("03b-wheel-over-text-area");
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
        // the preview follows the highlighted item while the list is open (nothing chosen yet)
        step(10, mc -> {
            key(mc, GLFW.GLFW_KEY_DOWN, 0);
            key(mc, GLFW.GLFW_KEY_DOWN, 0);
        });
        shot("07b-dropdown-preview");
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
        // list options: @Sorted (no arrows), @Size(max), @Range / @Pattern on a list
        step(10, mc -> config(mc).focusOption("labels"));
        shot("30b-list-options");
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
            mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, -10);
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
        step(5, mc -> mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, -1));
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
        step(5, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            config(mc).focusOption("favoriteMod");
        });
        step(5, mc -> clickRightEdge(mc));
        // @ModId: the installed mods with their logos, as a list by default
        shot("50b-mod-picker");
        step(5, mc -> key(mc, GLFW.GLFW_KEY_TAB, 0));
        shot("50d-mod-picker-detailed");
        step(5, mc -> key(mc, GLFW.GLFW_KEY_TAB, 0));
        shot("50e-mod-picker-grid");
        step(5, mc -> {
            key(mc, GLFW.GLFW_KEY_TAB, 0); // back to the list: remembered for the next mod picker
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            config(mc).focusOption("reward");
        });
        step(5, mc -> clickRightEdge(mc));
        // @Picker(styles = {LIST, DETAILED}): no grid button
        shot("50f-item-picker-list");
        // filters: monsters only in the picker, a cow is refused, food and ingredients only
        step(5, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            config(mc).focusOption("boss");
        });
        step(5, mc -> clickRightEdge(mc));
        shot("50g-filter-monsters");
        step(5, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            config(mc).focusOption("boss");
            for (int i = 0; i < 30; i++) {
                key(mc, GLFW.GLFW_KEY_BACKSPACE, 0);
            }
            type(mc, "minecraft:cow");
        });
        shot("50h-filter-refused");
        step(5, mc -> {
            config(mc).focusOption("netherHome");
            for (int i = 0; i < 30; i++) {
                key(mc, GLFW.GLFW_KEY_BACKSPACE, 0);
            }
            type(mc, "minecraft:");
        });
        shot("50i-filter-nether-biomes");
        step(5, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0); // closes the suggestions
            config(mc).focusOption("snacks");
        });
        step(5, mc -> {
            // the "+ Add" button is focused: add an entry, then open its picker
            key(mc, GLFW.GLFW_KEY_ENTER, 0);
        });
        step(5, mc -> clickRightEdge(mc));
        shot("50j-filter-food");
        step(5, mc -> key(mc, GLFW.GLFW_KEY_ESCAPE, 0));
        step(5, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            config(mc).focusOption("favoriteMod");
            for (int i = 0; i < 20; i++) {
                key(mc, GLFW.GLFW_KEY_BACKSPACE, 0);
            }
            type(mc, "m");
        });
        shot("50c-mod-suggestions");
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
        // spec 8.2 bis: lists of objects and lists of lists of other mods' files, element by element
        step(15, mc -> mc.setScreen(ConfeeScreens.openExternal(rawSelect, "confee_rawmod",
                mc.gameDirectory.toPath().resolve("config/confee_rawmod/spawns.json"))));
        step(5, mc -> config(mc).openSection("spawns"));
        shot("60-raw-list-of-objects");
        step(5, mc -> config(mc).openSection("spawns.[0]"));
        shot("61-raw-list-element");
        step(5, mc -> {
            config(mc).focusOption("spawns.[0].weight");
            type(mc, "5");
        });
        step(5, mc -> config(mc).openSection("spawns.[0].night"));
        shot("62-raw-object-in-element");
        // "+ Add" copies the last element; the weight typed above is kept
        step(5, mc -> config(mc).runListAction("spawns", "add", 0));
        step(5, mc -> config(mc).openSection("spawns"));
        shot("63-raw-list-added");
        step(5, mc -> config(mc).runListAction("spawns", "up", 2));
        step(5, mc -> config(mc).runListAction("spawns", "remove", 0));
        shot("64-raw-list-moved-removed");
        step(5, mc -> config(mc).openSection("waves"));
        shot("65-raw-list-of-lists");
        step(5, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            key(mc, GLFW.GLFW_KEY_ENTER, 0);
        });
        step(10, mc -> logLines(mc.gameDirectory.toPath().resolve("config/confee_rawmod/spawns.json"),
                "mob", "weight", "waves"));
        step(15, mc -> mc.setScreen(ConfeeScreens.openExternal(rawSelect, "confee_rawmod",
                mc.gameDirectory.toPath().resolve("config/confee_rawmod/kits.yml"))));
        step(5, mc -> config(mc).openSection("kits"));
        shot("66-raw-yaml-list-of-mappings");
        step(5, mc -> config(mc).openSection("kits.[0]"));
        shot("67-raw-yaml-element");
        step(15, mc -> mc.setScreen(ConfeeScreens.openExternal(rawSelect, "confee_rawmod", rawFile(mc))));
        step(5, mc -> config(mc).openSection("presets"));
        shot("68-raw-toml-array-of-tables");
        // a widget given by another mod to a value of this file (ConfeeWidgets.fileId)
        step(15, mc -> mc.setScreen(ConfeeScreens.openExternal(rawSelect, "confee_rawmod",
                mc.gameDirectory.toPath().resolve("config/confee_rawmod/spawns.json"))));
        step(5, mc -> config(mc).openSection("spawns.[1]"));
        shot("69-raw-custom-widget");
        // HOCON (V1.1): includes, substitutions, concatenations, lists of objects
        step(15, mc -> mc.setScreen(ConfeeScreens.openExternal(rawSelect, "confee_rawmod",
                mc.gameDirectory.toPath().resolve("config/confee_rawmod/server.conf"))));
        shot("70-hocon-general");
        step(5, mc -> config(mc).openSection("server"));
        shot("71-hocon-server");
        step(5, mc -> {
            config(mc).openSection("network");
            config(mc).focusOption("network.port");
            type(mc, "0");
        });
        shot("72-hocon-included");
        step(5, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            key(mc, GLFW.GLFW_KEY_ENTER, 0);
        });
        step(10, mc -> logLines(mc.gameDirectory.toPath().resolve("config/confee_rawmod/network.conf"), "port"));
        step(15, mc -> mc.setScreen(ConfeeScreens.openExternal(rawSelect, "confee_rawmod",
                mc.gameDirectory.toPath().resolve("config/confee_rawmod/server.conf"))));
        step(5, mc -> config(mc).openSection("server"));
        shot("73-hocon-after-save");
        step(15, mc -> mc.setScreen(ConfeeScreens.forMod(fileSelect, "confee")));
        shot("20-confee-settings");
        // ignoredMods: "+ Add", then the mod picker (only the mods the universal editor handles)
        step(10, mc -> {
            key(mc, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_CONTROL);
            config(mc).focusOption("external.ignoredMods");
            key(mc, GLFW.GLFW_KEY_ENTER, 0);
        });
        step(5, mc -> clickRightEdge(mc));
        shot("20b-ignored-mods-picker");
        step(5, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            config(mc).focusOption("external.forceConfeeEditor");
            key(mc, GLFW.GLFW_KEY_ENTER, 0);
        });
        step(5, mc -> clickRightEdge(mc));
        shot("20c-force-editor-picker");
        step(5, mc -> {
            key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
            mc.setScreen(ConfeeScreens.forMod(fileSelect, "confee")); // drops the two empty entries (not saved)
        });

        if (forge()) {
            forgeSteps();
        }
        realFileSteps();
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
     * Real config files of other mods, edited in game like a player would (spec 8, 15): for each file put in
     * {@code config/confee_rawmod/real/} (copies of real files, never part of the repository), opens it in the
     * universal editor, types one character in its first text or number value, saves with Escape then Enter, and
     * checks that the file is exactly the original with only that value changed.
     */
    private void realFileSteps() {
        Path dir = Path.of("config", "confee_rawmod", "real");
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files;
        try (java.util.stream.Stream<Path> walk = Files.list(dir)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            ConfeeLog.warn("[testmod] cannot list {}", dir);
            return;
        }
        for (Path file : files) {
            String[] expected = new String[1];
            String[] optionPath = new String[1];
            step(10, mc -> {
                expected[0] = null;
                Path path = mc.gameDirectory.toPath().resolve(file);
                try {
                    var adapter = fr.heetstudio.confee.format.Formats.forFile(path).orElseThrow();
                    var source = fr.heetstudio.confee.format.SourceText.decode(Files.readAllBytes(path));
                    var doc = adapter.parse(source);
                    for (var value : doc.values()) {
                        boolean simplePath = value.path().stream().noneMatch(s -> s.contains(".") || s.startsWith("[") || s.isEmpty());
                        Object edited = !value.editable() || !simplePath ? null
                                : value.kind() == fr.heetstudio.confee.format.ValueKind.STRING ? value.value() + "1"
                                : value.kind() == fr.heetstudio.confee.format.ValueKind.INTEGER && Math.abs((Long) value.value()) < 100_000_000L
                                ? Long.parseLong(value.value() + "1") : null;
                        if (edited != null) {
                            expected[0] = doc.edit(java.util.Map.of(value, edited), adapter.values());
                            optionPath[0] = String.join(".", value.path());
                            break;
                        }
                    }
                } catch (Exception e) {
                    ConfeeLog.warn("[testmod] real file {}: cannot prepare: {}", file.getFileName(), e.getMessage());
                }
                if (expected[0] == null) {
                    ConfeeLog.info("[testmod] real file {}: no text or number value to edit", file.getFileName());
                    return;
                }
                mc.setScreen(ConfeeScreens.openExternal(rawSelect, "confee_rawmod", path));
                int dot = optionPath[0].lastIndexOf('.');
                if (dot > 0) {
                    config(mc).openSection(optionPath[0].substring(0, dot));
                }
            });
            step(5, mc -> {
                if (expected[0] == null) {
                    return;
                }
                if (!config(mc).focusOption(optionPath[0])) {
                    ConfeeLog.warn("[testmod] real file {}: option {} not shown", file.getFileName(), optionPath[0]);
                    expected[0] = null;
                    return;
                }
                type(mc, "1");
                key(mc, GLFW.GLFW_KEY_ESCAPE, 0);
                key(mc, GLFW.GLFW_KEY_ENTER, 0);
            });
            step(10, mc -> {
                if (expected[0] == null) {
                    return;
                }
                try {
                    String now = fr.heetstudio.confee.format.SourceText.decode(
                            Files.readAllBytes(mc.gameDirectory.toPath().resolve(file))).text();
                    ConfeeLog.info("[testmod] real file {}: {} edited in game, file {}", file.getFileName(), optionPath[0],
                            now.equals(expected[0]) ? "OK (only that value changed)" : "DIFFERENT from the expected text");
                } catch (IOException e) {
                    ConfeeLog.warn("[testmod] real file {}: cannot read back", file.getFileName());
                }
            });
        }
    }

    /**
     * Spec 10.8: in a world, world registries (biomes) and tags come from the world itself. Creates a flat creative
     * world "confee-test" (deleted first) with a data pack that adds a biome, compares with the lists read from the
     * files, then leaves the world.
     */
    private void worldSteps() {
        String biomes = "minecraft:worldgen/biome";
        int[] offWorld = new int[2];
        step(5, mc -> {
            offWorld[0] = fr.heetstudio.confee.ui.RegistryEntries.ids(biomes).size();
            offWorld[1] = fr.heetstudio.confee.ui.RegistryEntries.tags(biomes).size();
            ConfeeLog.info("[testmod] outside a world: {} biomes, {} biome tags", offWorld[0], offWorld[1]);
            // the run window may not have the focus: no pause menu over the world
            mc.options.pauseOnLostFocus = false;
            Path save = mc.gameDirectory.toPath().resolve("saves/confee-test");
            deleteTree(save);
            // a data pack of the world that adds a biome: only the world knows it
            try {
                Path pack = save.resolve("datapacks/confee_test_pack");
                Files.createDirectories(pack.resolve("data/confee_pack/worldgen/biome"));
                int packFormat = net.minecraft.SharedConstants.getCurrentVersion()
                        .packVersion(net.minecraft.server.packs.PackType.SERVER_DATA).major();
                Files.writeString(pack.resolve("pack.mcmeta"),
                        "{\"pack\": {\"pack_format\": " + packFormat + ", \"description\": \"Confee test biome\"}}");
                Files.writeString(pack.resolve("data/confee_pack/worldgen/biome/glow_meadow.json"), String.join("\n",
                        "{",
                        "  \"has_precipitation\": true, \"temperature\": 0.8, \"downfall\": 0.4,",
                        "  \"effects\": {\"sky_color\": 7907327, \"fog_color\": 12638463, \"water_color\": 4159204,",
                        "    \"water_fog_color\": 329011},",
                        "  \"spawners\": {}, \"spawn_costs\": {}, \"carvers\": {}, \"features\": []",
                        "}"));
            } catch (IOException e) {
                ConfeeLog.warn("[testmod] cannot write the test data pack: {}", e.getMessage());
            }
            ConfeeLog.info("[testmod] data pack biome outside a world: {}",
                    fr.heetstudio.confee.ui.RegistryEntries.ids(biomes).contains("confee_pack:glow_meadow"));
            String name = "confee-test";
            mc.createWorldOpenFlows().createFreshLevel(name,
                    new net.minecraft.world.level.LevelSettings(name, net.minecraft.world.level.GameType.CREATIVE, false,
                            net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(net.minecraft.world.level.WorldDataConfiguration.DEFAULT.enabledFeatures()),
                            net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(0L, false, false),
                    registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                            .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value()
                            .createWorldDimensions(),
                    new TitleScreen());
        });
        waitUntil(2400, mc -> mc.level != null && mc.player != null && mc.screen == null);
        step(40, mc -> {
            int inWorld = fr.heetstudio.confee.ui.RegistryEntries.ids(biomes).size();
            int inWorldTags = fr.heetstudio.confee.ui.RegistryEntries.tags(biomes).size();
            ConfeeLog.info("[testmod] in a world: {} biomes, {} biome tags (files: {}, {})", inWorld, inWorldTags,
                    offWorld[0], offWorld[1]);
            ConfeeLog.info("[testmod] data pack biome in the world: {}",
                    fr.heetstudio.confee.ui.RegistryEntries.ids(biomes).contains("confee_pack:glow_meadow"));
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
                mc.level.disconnect(net.minecraft.network.chat.Component.empty());
            }
            mc.disconnect(new TitleScreen(), false);
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
            mc.setScreen(ConfeeScreens.forMod(fileSelect,
                    Services.PLATFORM.isModLoaded("neoforge") ? "neoforge" : "forge"));
        });
        shot("24-forge-own-configs");
    }

    private void step(int waitTicks, Consumer<Minecraft> action) {
        steps.add(new Step(waitTicks, action, null));
    }

    private void shot(String name) {
        step(5, mc -> Screenshot.grab(mc.gameDirectory, "confee-" + name + ".png", mc.getMainRenderTarget(), 1, message -> {
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
            var click = new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0));
            mc.screen.mouseClicked(click, false);
            mc.screen.mouseReleased(click);
        }
    }

    /** Moves the cursor (GUI coordinates): GLFW position + the game's mouse handler (dev names). */
    private static void moveMouse(Minecraft mc, double guiX, double guiY) {
        double scale = mc.getWindow().getGuiScale();
        long window = mc.getWindow().handle();
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
        mc.screen.keyPressed(new net.minecraft.client.input.KeyEvent(key, 0, modifiers));
    }

    private static void type(Minecraft mc, String text) {
        for (char c : text.toCharArray()) {
            mc.screen.charTyped(new net.minecraft.client.input.CharacterEvent(c, 0));
        }
    }

    private static void scale(Minecraft mc, int scale) {
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
    }
}

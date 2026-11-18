package fr.heetstudio.confee.testmod;

import fr.heetstudio.confee.api.Decoration;
import fr.heetstudio.confee.api.annotation.Block;
import fr.heetstudio.confee.api.annotation.Category;
import fr.heetstudio.confee.api.annotation.Description;
import fr.heetstudio.confee.api.annotation.Icon;
import fr.heetstudio.confee.api.annotation.Item;
import fr.heetstudio.confee.api.annotation.Keybind;
import fr.heetstudio.confee.api.annotation.Layout;
import fr.heetstudio.confee.api.annotation.Stepper;
import fr.heetstudio.confee.api.annotation.Suggestions;
import fr.heetstudio.confee.api.annotation.Widget;
import fr.heetstudio.confee.api.annotation.Color;
import fr.heetstudio.confee.api.annotation.Comment;
import fr.heetstudio.confee.api.annotation.Config;
import fr.heetstudio.confee.api.annotation.EnabledIf;
import fr.heetstudio.confee.api.annotation.Hidden;
import fr.heetstudio.confee.api.annotation.Key;
import fr.heetstudio.confee.api.annotation.MaxLength;
import fr.heetstudio.confee.api.annotation.Multiline;
import fr.heetstudio.confee.api.annotation.Name;
import fr.heetstudio.confee.api.annotation.Pattern;
import fr.heetstudio.confee.api.annotation.Preview;
import fr.heetstudio.confee.api.annotation.Range;
import fr.heetstudio.confee.api.annotation.Registry;
import fr.heetstudio.confee.api.annotation.RequiresRestart;
import fr.heetstudio.confee.api.annotation.Section;
import fr.heetstudio.confee.api.annotation.Slider;
import fr.heetstudio.confee.api.annotation.Unit;
import fr.heetstudio.confee.api.annotation.VisibleIf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every option type and every annotation (spec 15). File: config/confee_testmod.toml */
@Config(modId = ConfeeTestmod.MOD_ID)
@Comment("Confee test mod: annotation API")
@Layout(banner = "confee_testmod:textures/gui/banner.png", bannerHeight = 36)
public class AnnotatedTestConfig {

    public enum Difficulty { PEACEFUL, EASY, NORMAL, HARD }

    public enum Quality { LOW, MEDIUM, HIGH, ULTRA, INSANE }

    public Decoration intro = Decoration.info("Every option type and annotation of Confee. Hover a name for its tooltip.");

    @Comment("A simple toggle")
    @Description("Shown under the name: the HUD is drawn over the game.")
    @Preview(text = "Shows the §aHUD§r over the game (formatted text).")
    public boolean showHud = true;

    @Name("Particle count")
    @RequiresRestart
    @Preview(renderer = ParticlePreview.class, text = "Live drawing: one dot per particle (max 200).")
    public int particleCount = 20;

    @Range(min = 0, max = 100)
    @Unit("%")
    @EnabledIf("showHud")
    @Comment("Only editable when the HUD is shown")
    @Description("Grayed out while the HUD is off.")
    public int volume = 70;

    @Range(min = 0, max = 64)
    @Stepper(step = 1)
    @Description("Stepper: − and + buttons, arrows, Shift = ×10.")
    public int lives = 3;

    @Range(min = -20, max = 50)
    @Widget(TemperatureWidget.class)
    @Description("A custom widget written by the mod.")
    public int temperature = 18;

    public Decoration fieldsTitle = Decoration.header("Text fields").icon("minecraft:writable_book");

    @Range(min = 0, max = 10)
    @Slider(step = 1)
    public int stepped = 5;

    @Range(min = 0)
    public long bigNumber = 5_000_000_000L;

    @Range(min = 0, max = 1)
    @Slider(step = 0.05)
    public float opacity = 0.8f;

    @Comment("Free text")
    public String title = "Hello Confee";

    @Multiline
    @Comment("Several lines of text")
    public String motd = "Welcome!\nHave fun.";

    @Pattern("[a-z_]+")
    @MaxLength(16)
    public String code = "abc";

    @Color
    public int tint = 0xFF3C8527;

    @Color(alpha = true)
    public int overlay = 0x802D7DD2;

    @Item
    @Comment("Item: icon, suggestions, picker and automatic preview")
    public ResourceLocation item = new ResourceLocation("minecraft", "diamond");

    @Block
    public ResourceLocation favoriteBlock = new ResourceLocation("minecraft", "grass_block");

    @Suggestions({"north", "south", "east", "west", "up", "down"})
    @Description("Suggestions given by the mod (press Down).")
    public String direction = "north";

    @fr.heetstudio.confee.api.annotation.Entity
    @Description("Entity: spawn egg icons, mods' entities included.")
    public String favoriteMob = "minecraft:axolotl";

    @fr.heetstudio.confee.api.annotation.Biome(tags = true)
    @Description("Biome or biome tag: from the world when one is loaded, else from the game and mod files.")
    public List<String> spawnBiomes = new ArrayList<>(List.of("minecraft:plains", "#minecraft:is_forest"));

    @fr.heetstudio.confee.api.annotation.Effect
    public String bonusEffect = "minecraft:speed";

    @fr.heetstudio.confee.api.annotation.Enchantment
    public String favoriteEnchantment = "minecraft:mending";

    @fr.heetstudio.confee.api.annotation.Sound
    public String alertSound = "minecraft:block.note_block.bell";

    @Keybind
    @Description("Click, then press a key.")
    public String dashKey = "key.keyboard.r";

    @Comment("Two values: segmented buttons")
    public Difficulty difficulty = Difficulty.NORMAL;

    @Comment("Five values: dropdown")
    @Preview(image = "confee_testmod:textures/preview/quality_{value}.png", text = "The image follows the selected value.")
    public Quality quality = Quality.HIGH;

    @VisibleIf("quality=INSANE")
    @Comment("Only visible with the INSANE quality")
    public boolean insaneWarning = true;

    public Decoration insaneNote = Decoration.warning("INSANE quality needs a strong computer.").visibleIf("quality=INSANE");

    @Description("Check boxes: several values of an enum.")
    public Set<Quality> allowedQualities = EnumSet.of(Quality.LOW, Quality.HIGH);

    public Decoration collectionsTitle = Decoration.header("Collections");

    @Block
    public List<String> blacklist = new ArrayList<>(List.of("minecraft:dirt", "minecraft:sand"));

    public List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3));

    @Comment("List of enum values")
    public List<Difficulty> allowedDifficulties = new ArrayList<>(List.of(Difficulty.EASY, Difficulty.NORMAL));

    @Comment("Key -> value map")
    public Map<String, Integer> biomeWeights = new LinkedHashMap<>(Map.of("plains", 10, "desert", 3));

    @Comment("List of objects")
    public List<Waypoint> waypoints = new ArrayList<>(List.of(new Waypoint("home", 0, 64, true)));

    @Hidden
    @Key("internal_counter")
    public int internalCounter = 0;

    public Decoration links = Decoration.button("Open the Minecraft wiki",
            () -> net.minecraft.Util.getPlatform().openUri("https://minecraft.wiki")).icon("minecraft:book");

    @Category(value = "dash", icon = "minecraft:feather")
    @Comment("Dash distance, in blocks")
    @Range(min = 1, max = 10)
    @Slider(step = 0.5)
    public double dashDistance = 4.0;

    @Category("dash")
    public boolean dashEnabled = true;

    @Category(value = "display", icon = "minecraft:spyglass")
    @Range(min = 0.5, max = 3)
    public double scale = 1.0;

    public Advanced advanced = new Advanced();

    /** Element of {@link #waypoints}. */
    @Section
    public static class Waypoint {
        public String name = "new";
        public int x = 0;
        public int y = 64;
        public boolean visible = true;

        public Waypoint() {
        }

        Waypoint(String name, int x, int y, boolean visible) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.visible = visible;
        }
    }

    @Section
    @Icon("minecraft:redstone")
    @Comment("Nested section")
    public static class Advanced {
        public boolean debug = false;

        @Range(min = 1, max = 64)
        public int threads = 4;
    }
}

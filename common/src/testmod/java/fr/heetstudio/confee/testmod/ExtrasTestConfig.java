package fr.heetstudio.confee.testmod;

import fr.heetstudio.confee.api.SuggestionProvider;
import fr.heetstudio.confee.api.ValueValidator;
import fr.heetstudio.confee.api.annotation.Comment;
import fr.heetstudio.confee.api.annotation.Config;
import fr.heetstudio.confee.api.annotation.Description;
import fr.heetstudio.confee.api.annotation.Preview;
import fr.heetstudio.confee.api.annotation.Range;
import fr.heetstudio.confee.api.annotation.Section;
import fr.heetstudio.confee.api.annotation.Suggestions;
import fr.heetstudio.confee.api.annotation.Validator;
import fr.heetstudio.confee.api.annotation.Widget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Options added in Confee 0.3.0: {@code writeDefaults}, {@code legacyFiles} (the fixture
 * {@code ConfeeTestmod-Extras.toml} is moved to {@code confee_testmod-extras.toml}), {@code @Validator}, previews
 * reading the pending values of other options, a custom widget using every {@code OptionWidget} hook, and a
 * suggestion provider that needs a world.
 */
@Config(modId = ConfeeTestmod.MOD_ID, name = "extras", writeDefaults = true, legacyFiles = "ConfeeTestmod-Extras.toml")
@Comment("Confee test mod: options added in 0.3.0")
public class ExtrasTestConfig {

    public Outline outline = new Outline();

    @Section
    @Comment("Outline of the mobs of each rarity")
    public static class Outline {
        @Description("Turn a rarity off: the previews below follow it before you save.")
        public boolean enabled = true;
        public boolean rare = true;
        public boolean ultra = true;
        @Preview(renderer = OutlinePreview.class, text = "Reads outline.enabled, outline.rare and outline.ultra pending values.")
        public boolean infernal = true;
    }

    @Range(min = 1, max = 10)
    @Description("Used by the outline preview (pending value).")
    public int outlineWidth = 2;

    @Validator(BracesCheck.class)
    @Description("@Validator on a list: each value must be written between { and }.")
    public List<String> lootSnbt = new ArrayList<>(List.of("{id:\"minecraft:diamond\",Count:1}"));

    @Validator(BracesCheck.class)
    public String singleSnbt = "{}";

    @Widget(LootLinesWidget.class)
    @Description("Custom widget: error, overlay, text capture, focused line, Confee buttons and item icons.")
    public List<String> lootItems = new ArrayList<>(List.of("minecraft:diamond", "minecraft:golden_apple", "minecraft:totem_of_undying"));

    @Suggestions(value = {"boss", "elite"}, provider = WorldTags.class)
    @Description("Suggestions that need a world: greyed hint in an empty field from the title screen.")
    public List<String> excludedTags = new ArrayList<>();

    /** Text between braces (stands for an SNBT check). Runs on the server too. */
    public static class BracesCheck implements ValueValidator<String> {
        @Override
        public String validate(String value) {
            return value.startsWith("{") && value.endsWith("}") ? null : "Write the value between { and }";
        }
    }

    /** Scoreboard tags of the loaded world (client only, called when the screen opens). */
    public static class WorldTags implements SuggestionProvider {
        @Override
        public Collection<String> suggestions() {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            return mc.level == null ? List.of() : new ArrayList<>(mc.level.getScoreboard().getObjectiveNames());
        }

        @Override
        public boolean needsWorld() {
            return true;
        }
    }
}

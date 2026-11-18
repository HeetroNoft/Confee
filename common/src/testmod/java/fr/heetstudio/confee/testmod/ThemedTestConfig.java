package fr.heetstudio.confee.testmod;

import fr.heetstudio.confee.api.ConfigScope;
import fr.heetstudio.confee.api.annotation.Comment;
import fr.heetstudio.confee.api.annotation.Config;
import fr.heetstudio.confee.api.annotation.Range;
import fr.heetstudio.confee.api.annotation.Theme;

/** Another accent color and panel tokens (spec 15). CLIENT scope: never written on a dedicated server. */
@Config(modId = ConfeeTestmod.MOD_ID, name = "themed", scope = ConfigScope.CLIENT)
@Theme(accent = 0xFF2D7DD2, panelBg = 0xFF23272E, panelBgDeep = 0xFF16191D)
@Comment("Confee test mod: custom theme")
@fr.heetstudio.confee.api.annotation.Layout(background = "minecraft:textures/block/deepslate_tiles.png", panelWidth = 320)
public class ThemedTestConfig {

    public boolean enabled = true;

    @Range(min = 0, max = 100)
    public int amount = 50;
}

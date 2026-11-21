package fr.heetstudio.confee.testmod;

import fr.heetstudio.confee.api.PreviewContext;
import fr.heetstudio.confee.api.PreviewRenderer;
import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.api.client.ConfeeDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * One preview for the whole {@code outline} section ({@code @Preview} on the section class): a zombie per rarity with
 * its outline (Glowing style) when it is on, read from the pending values; the hovered rarity ({@link PreviewContext#path()})
 * is highlighted, the others dimmed. Infernal mobs stand behind a stone brick wall: only their outline is seen.
 */
public class OutlinePreview implements PreviewRenderer {

    private static final String[] RARITIES = {"rare", "ultra", "infernal"};
    private static final int[] COLORS = {0xFF52A535, 0xFF2D7DD2, 0xFFE5533D};

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int height, Object value, PreviewContext context, float delta) {
        Theme theme = context.theme();
        boolean all = context.value("outline.enabled", Boolean.class, true);
        String hovered = context.path().substring(context.path().lastIndexOf('.') + 1);
        int cell = width / RARITIES.length;
        int mobH = height - 16;
        for (int i = 0; i < RARITIES.length; i++) {
            boolean on = all && context.value("outline." + RARITIES[i], Boolean.class, false);
            boolean focus = hovered.equals(RARITIES[i]) || hovered.equals("enabled");
            int cx = x + i * cell;
            if (i == 2) {
                // behind a wall: the outline is seen through it, not the mob
                ConfeeDraw.entity(g, "minecraft:zombie", cx, y + 2, cell, mobH, on ? COLORS[i] : 0, false);
                ConfeeDraw.tiled(g, "minecraft:block/stone_bricks", cx + 2, y + 2 + mobH / 3, cell - 4, mobH * 2 / 3);
                if (on) {
                    ConfeeDraw.entity(g, "minecraft:zombie", cx, y + 2, cell, mobH, COLORS[i], true);
                }
            } else {
                ConfeeDraw.entity(g, "minecraft:zombie", cx, y + 2, cell, mobH, on ? COLORS[i] : 0, false);
            }
            int label = on ? theme.textOnDark() : theme.textDisabled();
            ConfeeDraw.textFit(g, Component.literal(RARITIES[i]), cx + 2, y + height - 11, cell - 4,
                    focus ? label : theme.dimmed(label));
        }
    }

    @Override
    public int height(int width) {
        return width * 3 / 4;
    }
}

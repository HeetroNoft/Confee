package fr.heetstudio.confee.testmod;

import fr.heetstudio.confee.api.PreviewContext;
import fr.heetstudio.confee.api.PreviewRenderer;
import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.api.client.ConfeeDraw;
import fr.heetstudio.confee.api.client.ConfeeIcons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Preview reading the pending values of other options ({@link PreviewContext}): one zombie per rarity, outlined when
 * outlines are on for it, with the theme colors. Client only.
 */
public class OutlinePreview implements PreviewRenderer {

    private static final String[] RARITIES = {"rare", "ultra", "infernal"};
    private static final int[] COLORS = {0xFF52A535, 0xFF2D7DD2, 0xFFE5533D};

    @Override
    public void render(GuiGraphics g, int x, int y, int width, int height, Object value, PreviewContext context, float delta) {
        Theme theme = context.theme();
        boolean all = context.value("outline.enabled", Boolean.class, true);
        int thickness = context.value("outlineWidth", Integer.class, 1);
        int cell = width / RARITIES.length;
        int size = Math.min(cell - 12, height - 24);
        for (int i = 0; i < RARITIES.length; i++) {
            // the option of this preview gives its own value; the others come from the context
            boolean on = all && (i == 2 ? Boolean.TRUE.equals(value) : context.value("outline." + RARITIES[i], Boolean.class, false));
            int cx = x + i * cell + (cell - size) / 2;
            int cy = y + 4;
            if (on) {
                g.fill(cx - thickness, cy - thickness, cx + size + thickness, cy + size + thickness, COLORS[i]);
            }
            g.fill(cx, cy, cx + size, cy + size, theme.panelBg());
            ConfeeIcons.draw(g, "minecraft:entity_type", "minecraft:zombie", cx + 2, cy + 2, size - 4);
            ConfeeDraw.textFit(g, Component.literal(RARITIES[i]), x + i * cell + 2, y + height - 12, cell - 4,
                    on ? theme.textOnDark() : theme.textDisabled());
        }
    }

    @Override
    public int height(int width) {
        return width / 2;
    }
}

package fr.heetstudio.confee.testmod;

import fr.heetstudio.confee.api.PreviewContext;
import fr.heetstudio.confee.api.PreviewRenderer;
import fr.heetstudio.confee.api.client.ConfeeIcons;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Loot preview: each stack with its count and enchantment glint ({@link ConfeeIcons#drawStack}). Client only. */
public class LootPreview implements PreviewRenderer {

    @Override
    public void render(GuiGraphics g, int x, int y, int width, int height, Object value, PreviewContext context, float delta) {
        if (!(value instanceof List<?> stacks)) {
            return;
        }
        int perRow = Math.max(1, (width - 4) / 20);
        for (int i = 0; i < stacks.size(); i++) {
            int sx = x + 4 + (i % perRow) * 20;
            int sy = y + 4 + (i / perRow) * 20;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, context.theme().panelBg());
            ConfeeIcons.drawStack(g, String.valueOf(stacks.get(i)), 0, sx, sy);
        }
    }

    @Override
    public int height(int width) {
        return 48;
    }
}

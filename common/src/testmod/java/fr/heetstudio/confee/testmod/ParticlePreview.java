package fr.heetstudio.confee.testmod;

import fr.heetstudio.confee.api.PreviewRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Custom preview of {@code particleCount}: one dot per particle, gently floating. Client only. */
public class ParticlePreview implements PreviewRenderer {

    private float time;

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int height, Object value, float delta) {
        time += delta;
        int count = Math.min(200, Math.max(0, ((Number) value).intValue()));
        for (int i = 0; i < count; i++) {
            // deterministic pseudo-random positions, so the dots do not jump between frames
            int seed = i * 7919 + 13;
            int px = x + 2 + Math.floorMod(seed * 31, Math.max(1, width - 4));
            int base = Math.floorMod(seed * 17, Math.max(1, height - 4));
            int py = y + 2 + Math.floorMod(base - (int) (time * (0.3f + (i % 5) * 0.1f)), Math.max(1, height - 4));
            int color = 0xFF000000 | (i % 3 == 0 ? 0x52A535 : i % 3 == 1 ? 0x2D7DD2 : 0xF2C94C);
            g.fill(px, py, px + 2, py + 2, color);
        }
    }

    @Override
    public int height(int width) {
        return width / 2;
    }
}

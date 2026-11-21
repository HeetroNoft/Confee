package fr.heetstudio.confee.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heetstudio.confee.ui.screen.Textures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Mob drawing behind {@code ConfeeDraw.entity}: the vanilla model (inventory style) when a world is loaded, else a
 * front view drawn from the skin of humanoid mobs, with an optional 1 px outline following the silhouette (Glowing
 * style). The 3D model and the 2D view are version-specific.
 * <p>Internal: may change without notice.</p>
 */
public final class MobDrawing {

    /** Front view of a humanoid skin: texture, its height (64 or 32) and the width of the arms and legs (4 or 2). */
    private record Skin(String texture, int textureHeight, int limb) {
    }

    private static final Map<String, Skin> SKINS = Map.of(
            "minecraft:zombie", new Skin("minecraft:textures/entity/zombie/zombie.png", 64, 4),
            "minecraft:husk", new Skin("minecraft:textures/entity/zombie/husk.png", 64, 4),
            "minecraft:drowned", new Skin("minecraft:textures/entity/zombie/drowned.png", 64, 4),
            "minecraft:skeleton", new Skin("minecraft:textures/entity/skeleton/skeleton.png", 32, 2),
            "minecraft:stray", new Skin("minecraft:textures/entity/skeleton/stray.png", 32, 2),
            "minecraft:wither_skeleton", new Skin("minecraft:textures/entity/skeleton/wither_skeleton.png", 32, 2),
            "minecraft:player", new Skin("minecraft:textures/entity/player/wide/steve.png", 64, 4));

    private static final Map<String, Entity> ENTITIES = new HashMap<>();
    @Nullable
    private static ClientLevel entitiesLevel;

    private MobDrawing() {
    }

    /**
     * Draws a mob standing in a box, centered, feet at the bottom.
     *
     * @param outlineColor ARGB color of the outline, or {@code 0} for none
     * @param outlineOnly  draw only the outline (the mob hidden behind blocks)
     * @return false when nothing could be drawn (unknown mob)
     */
    public static boolean draw(GuiGraphics g, String entityId, int x, int y, int width, int height, int outlineColor,
                               boolean outlineOnly) {
        Skin skin = SKINS.get(entityId);
        boolean wantsOutline = outlineColor != 0 || outlineOnly;
        if (skin != null && Textures.size(skin.texture())[0] > 0 && (wantsOutline || Minecraft.getInstance().level == null)) {
            drawSkin(g, skin, x, y, width, height, outlineColor, outlineOnly);
            return true;
        }
        if (!outlineOnly && model(g, entityId, x, y, width, height)) {
            return true;
        }
        if (skin != null && Textures.size(skin.texture())[0] > 0) {
            drawSkin(g, skin, x, y, width, height, outlineColor, outlineOnly);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 3D model (a world is loaded)

    private static boolean model(GuiGraphics g, String entityId, int x, int y, int width, int height) {
        ClientLevel level = Minecraft.getInstance().level;
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (level == null || id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return false;
        }
        if (entitiesLevel != level) {
            ENTITIES.clear();
            entitiesLevel = level;
        }
        Entity entity = ENTITIES.computeIfAbsent(entityId, k -> {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
            return type.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
        });
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        // a little smaller than the box: arms and heads stick out of the bounding box
        float scale = 0.8f * Math.min(height / Math.max(0.1f, living.getBbHeight()), width / Math.max(0.1f, living.getBbWidth() * 1.6f));
        // facing the player, like the inventory with the mouse in front
        living.yBodyRot = 180;
        living.yBodyRotO = 180;
        living.setYRot(180);
        living.yHeadRot = 180;
        living.yHeadRotO = 180;
        living.setXRot(0);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        InventoryScreen.renderEntityInInventory(g, x + width / 2, y + height, Math.max(1, Math.round(scale)), new org.joml.Vector3f(), pose, null, living);
        return true;
    }

    // ------------------------------------------------------------------ 2D front view of a humanoid skin

    private static void drawSkin(GuiGraphics g, Skin skin, int x, int y, int width, int height, int outlineColor,
                                 boolean outlineOnly) {
        int limb = skin.limb();
        int modelW = 8 + 2 * limb;
        // model pixels -> screen pixels, a whole number when there is room (crisp texture)
        float s = Math.min((height - 2) / 32f, (width - 2) / (float) modelW);
        if (s >= 1) {
            s = (float) Math.floor(s);
        }
        int w = Math.round(modelW * s);
        int h = Math.round(32 * s);
        int ox = x + (width - w) / 2;
        int oy = y + height - 1 - h;
        // parts: {model x, model y, model w, model h, u, v}
        int[][] parts = {
                {limb, 0, 8, 8, 8, 8},                                  // head
                {limb, 8, 8, 12, 20, 20},                               // body
                {0, 8, limb, 12, 40 + limb, 16 + limb},                 // arm (viewer's left)
                {limb + 8, 8, limb, 12, 40 + limb, 16 + limb},          // arm (viewer's right)
                {limb + 4 - limb, 20, limb, 12, limb, 16 + limb},       // leg
                {limb + 4, 20, limb, 12, limb, 16 + limb},              // leg
        };
        if (limb == 2) {
            // skeleton legs are thin and set apart
            parts[4][0] = limb + 1;
            parts[5][0] = limb + 5;
        }
        int[][] rects = new int[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            int[] p = parts[i];
            int rx = ox + Math.round(p[0] * s);
            int ry = oy + Math.round(p[1] * s);
            rects[i] = new int[]{rx, ry, ox + Math.round((p[0] + p[2]) * s) - rx, oy + Math.round((p[1] + p[3]) * s) - ry};
        }
        if (outlineColor != 0 || outlineOnly) {
            outline(g, rects, ox - 1, oy - 1, w + 2, h + 2, outlineColor != 0 ? outlineColor : 0xFFFFFFFF);
        }
        if (outlineOnly) {
            return;
        }
        ResourceLocation texture = ResourceLocation.parse(skin.texture());
        int texH = skin.textureHeight();
        RenderSystem.enableBlend();
        for (int i = 0; i < parts.length; i++) {
            int[] p = parts[i];
            int[] r = rects[i];
            g.blit(net.minecraft.client.renderer.RenderType::guiTextured, texture, r[0], r[1], p[4], p[5], r[2], r[3], p[2], p[3], 64, texH);
        }
        // hat layer over the head
        int[] head = rects[0];
        g.blit(net.minecraft.client.renderer.RenderType::guiTextured, texture, head[0], head[1], 40, 8, head[2], head[3], 8, 8, 64, texH);
        RenderSystem.disableBlend();
    }

    /** 1 px ring around the union of the rectangles (pixels outside touching one inside), drawn by runs. */
    private static void outline(GuiGraphics g, int[][] rects, int bx, int by, int bw, int bh, int color) {
        boolean[][] inside = new boolean[bh + 2][bw + 2];
        for (int[] r : rects) {
            for (int py = r[1]; py < r[1] + r[3]; py++) {
                for (int px = r[0]; px < r[0] + r[2]; px++) {
                    int mx = px - bx + 1;
                    int my = py - by + 1;
                    if (my >= 0 && my < inside.length && mx >= 0 && mx < inside[0].length) {
                        inside[my][mx] = true;
                    }
                }
            }
        }
        for (int my = 0; my < inside.length; my++) {
            int runStart = -1;
            for (int mx = 0; mx <= inside[0].length; mx++) {
                boolean ring = mx < inside[0].length && !inside[my][mx] && touches(inside, mx, my);
                if (ring && runStart < 0) {
                    runStart = mx;
                } else if (!ring && runStart >= 0) {
                    Draw.rect(g, bx - 1 + runStart, by - 1 + my, mx - runStart, 1, color);
                    runStart = -1;
                }
            }
        }
    }

    private static boolean touches(boolean[][] inside, int x, int y) {
        return (y > 0 && inside[y - 1][x]) || (y < inside.length - 1 && inside[y + 1][x])
                || (x > 0 && inside[y][x - 1]) || (x < inside[0].length - 1 && inside[y][x + 1]);
    }
}

package fr.heetstudio.confee.ui;

import com.mojang.blaze3d.platform.NativeImage;
import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Logos of the installed mods (Fabric {@code icon}, Forge {@code logoFile}), loaded once into a texture and drawn
 * fitted in a square (wide logos keep their ratio).
 * <p>Internal: may change without notice.</p>
 */
public final class ModIcons {

    private record Icon(Identifier texture, int width, int height) {
    }

    /** Empty = the mod has no readable logo (not tried again). */
    private static final Map<String, Optional<Icon>> ICONS = new HashMap<>();

    private ModIcons() {
    }

    /** Draws the logo of the mod in a {@code size} box; false when it has none. */
    public static boolean draw(GuiGraphicsExtractor g, String modId, int x, int y, int size) {
        Icon icon = icon(modId);
        if (icon == null) {
            return false;
        }
        float scale = Math.min(size / (float) icon.width(), size / (float) icon.height());
        int w = Math.max(1, Math.round(icon.width() * scale));
        int h = Math.max(1, Math.round(icon.height() * scale));
        g.blit(RenderPipelines.GUI_TEXTURED, icon.texture(), x + (size - w) / 2, y + (size - h) / 2, 0f, 0f, w, h, icon.width(), icon.height(), icon.width(), icon.height());
        return true;
    }

    @Nullable
    private static Icon icon(String modId) {
        return ICONS.computeIfAbsent(modId, ModIcons::load).orElse(null);
    }

    private static Optional<Icon> load(String modId) {
        Optional<Path> path;
        try {
            path = Services.PLATFORM.getModIcon(modId);
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
        if (path.isEmpty() || !Files.isRegularFile(path.get())) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(path.get())) {
            NativeImage image = NativeImage.read(in);
            // a large logo drawn small looks better smoothed; pixel-art icons stay sharp
            boolean smooth = image.getWidth() > 64 || image.getHeight() > 64;
            DynamicTexture texture = new DynamicTexture(() -> "Confee mod icon " + modId, image) {
                {
                    if (smooth) {
                        sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                                .getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.LINEAR);
                    }
                }
            };
            String safe = modId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            Identifier location = Identifier.fromNamespaceAndPath("confee", "mod_icon/" + safe);
            Minecraft.getInstance().getTextureManager().register(location, texture);
            return Optional.of(new Icon(location, image.getWidth(), image.getHeight()));
        } catch (Exception e) {
            // not a PNG, unreadable file...: the mod is shown with its initials
            ConfeeLog.warn("Cannot read the logo of mod {}: {}", modId, e.toString());
            return Optional.empty();
        }
    }
}

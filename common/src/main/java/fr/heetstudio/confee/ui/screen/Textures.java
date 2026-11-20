package fr.heetstudio.confee.ui.screen;

import com.mojang.blaze3d.platform.NativeImage;
import fr.heetstudio.confee.ConfeeLog;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sizes of the textures used by previews, decorations, banners and icons (read once).
 * <p>Internal: may change without notice.</p>
 */
public final class Textures {

    private static final Map<String, int[]> SIZES = new ConcurrentHashMap<>();

    private Textures() {
    }

    /** Width and height of a texture, or {0, 0} when it does not exist (logged once). */
    public static int[] size(String resource) {
        return SIZES.computeIfAbsent(resource, Textures::read);
    }

    private static int[] read(String resource) {
        Identifier id = Identifier.tryParse(resource);
        if (id == null) {
            ConfeeLog.warn("Invalid texture id '{}'", resource);
            return new int[]{0, 0};
        }
        Optional<Resource> found = Minecraft.getInstance().getResourceManager().getResource(id);
        if (found.isEmpty()) {
            ConfeeLog.warn("Texture not found: {}", resource);
            return new int[]{0, 0};
        }
        try (InputStream in = found.get().open(); NativeImage image = NativeImage.read(in)) {
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (Exception e) {
            ConfeeLog.warn("Cannot read texture {}: {}", resource, e.getMessage());
            return new int[]{0, 0};
        }
    }
}

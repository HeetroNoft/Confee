package fr.heetstudio.confee.ui.screen;

import com.mojang.blaze3d.platform.NativeImage;
import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.api.Preview;
import fr.heetstudio.confee.api.PreviewRenderer;
import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.core.ConfigSchema;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.ui.EditSession;
import fr.heetstudio.confee.ui.Labels;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Third column of the config screen: preview of the hovered (or focused) option — image, custom rendering and text,
 * all optional. Hidden when the window is too narrow.
 * <p>Internal: may change without notice.</p>
 */
final class PreviewPanel {

    static final int WIDTH = fr.heetstudio.confee.api.Layout.DEFAULT_PREVIEW_WIDTH;
    private static final int PAD = 8;
    private static final int MAX_MEDIA_H = 140;

    private final Ui ui;
    private final ConfigSchema schema;
    private final EditSession session;
    private final Map<String, int[]> imageSizes = new HashMap<>();
    private final Map<Option<?>, PreviewRenderer> renderers = new HashMap<>();
    private final Set<Option<?>> brokenRenderers = new HashSet<>();
    private final int width;
    /** Pending values of the config and theme, for the renderers (spec 10.6). */
    private final fr.heetstudio.confee.api.PreviewContext context;

    PreviewPanel(Ui ui, ConfigSchema schema, EditSession session, int width) {
        this.ui = ui;
        this.schema = schema;
        this.session = session;
        this.width = width;
        this.context = new fr.heetstudio.confee.api.PreviewContext() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object value(String path) {
                return path == null ? null : schema.root().findOption(path).map(o -> session.get((Option) o)).orElse(null);
            }

            @Override
            public Theme theme() {
                return ui.theme();
            }
        };
    }

    int width() {
        return width;
    }

    /** Registry of an automatic preview (declared or guessed ID option without preview), or null. */
    @Nullable
    static String autoPreviewRegistry(ConfigSchema schema, Option<?> option, Object value) {
        if (option.preview() != null || (option.type() != fr.heetstudio.confee.core.OptionType.IDENTIFIER
                && option.type() != fr.heetstudio.confee.core.OptionType.STRING)) {
            return null;
        }
        fr.heetstudio.confee.ui.RegistryDetector.Detected detected = fr.heetstudio.confee.ui.RegistryDetector.of(schema, option, value);
        return detected == null ? null : detected.registry();
    }

    static boolean anyPreview(ConfigSchema schema) {
        boolean[] any = {false};
        schema.root().forEachOption(o -> any[0] |= (o.preview() != null || autoPreviewRegistry(schema, o, o.get()) != null) && !o.hidden());
        return any[0];
    }

    void render(GuiGraphicsExtractor g, int x, int top, int bottom, @Nullable Option<?> option, float delta) {
        render(g, x, top, bottom, option, null, delta);
    }

    /**
     * @param shown value to preview instead of the current one (the item hovered in an open dropdown), or null
     */
    void render(GuiGraphicsExtractor g, int x, int top, int bottom, @Nullable Option<?> option, @Nullable Object shown,
                float delta) {
        Theme t = ui.theme();
        Draw.rect(g, x, top, width, bottom - top, t.panelBg());
        g.enableScissor(x, top, x + width, bottom);
        int w = width - 2 * PAD;
        int y = top + PAD;
        Draw.text(g, ui.font(), Component.translatable("confee.preview.title"), x + PAD, y, ui.secondaryText());
        y += 14;
        if (option == null) {
            paragraph(g, Component.translatable("confee.preview.hint"), x + PAD, y, w, ui.secondaryText());
            g.disableScissor();
            return;
        }
        for (FormattedCharSequence line : ui.font().split(Labels.name(schema, option, option.path()), w)) {
            Draw.text(g, ui.font(), line, x + PAD, y, t.textOnDark());
            y += 10;
        }
        y += 4;
        Preview preview = option.preview();
        Object value = shown != null ? shown : session.get(option);
        String registry = autoPreviewRegistry(schema, option, value);
        if (registry != null) {
            String id = String.valueOf(value).trim();
            boolean tag = id.startsWith("#");
            boolean known = tag ? fr.heetstudio.confee.ui.RegistryEntries.tags(registry).contains(id)
                    : fr.heetstudio.confee.core.PseudoRegistries.isModList(registry)
                    ? fr.heetstudio.confee.platform.Services.PLATFORM.isModLoaded(id) // hidden sub-mods are known too
                    : fr.heetstudio.confee.ui.RegistryEntries.ids(registry).contains(id);
            if (!known) {
                paragraph(g, Component.translatable(tag ? "confee.preview.unknown_tag" : "confee.preview.unknown_item", id),
                        x + PAD, y, w, t.error());
            } else {
                if (fr.heetstudio.confee.ui.RegistryIcons.hasIcons(registry) && !tag) {
                    int size = Math.min(64, w);
                    Draw.rect(g, x + PAD, y, w, size + 12, t.panelBgDeep());
                    if (fr.heetstudio.confee.ui.RegistryIcons.draw(g, registry, id, x + PAD + (w - size) / 2, y + 6, size)) {
                        y += size + 18;
                    } else {
                        Draw.rect(g, x + PAD, y, w, size + 12, t.panelBg());
                    }
                }
                Component named = tag ? Component.translatable("confee.preview.tag") : fr.heetstudio.confee.ui.RegistryIcons.name(registry, id);
                if (named != null) {
                    y = paragraph(g, named, x + PAD, y, w, t.textOnDark());
                }
                paragraph(g, Component.literal(id), x + PAD, y + 1, w, ui.secondaryText());
            }
            g.disableScissor();
            return;
        }
        if (preview == null) {
            paragraph(g, Component.translatable("confee.preview.none"), x + PAD, y, w, ui.secondaryText());
            g.disableScissor();
            return;
        }
        if (preview.image() != null) {
            y = image(g, resolve(preview.image(), value), x + PAD, y, w) + 6;
        }
        if (preview.renderer() != null) {
            y = custom(g, option, preview, value, x + PAD, y, w, delta) + 6;
        }
        if (preview.text() != null) {
            String text = preview.text();
            Component component = I18n.exists(text) ? Component.translatable(text) : Component.literal(text);
            paragraph(g, component, x + PAD, y, w, t.textOnDark());
        }
        g.disableScissor();
    }

    /** {@code {value}} → lower-case enum name (or text of the value). */
    private static String resolve(String image, Object value) {
        String name = value instanceof Enum<?> e ? e.name() : String.valueOf(value);
        return image.replace("{value}", name.toLowerCase(Locale.ROOT));
    }

    private int image(GuiGraphicsExtractor g, String resource, int x, int y, int w) {
        int[] size = Textures.size(resource);
        Identifier id = Identifier.tryParse(resource);
        if (size[0] <= 0 || id == null) {
            return paragraph(g, Component.translatable("confee.preview.missing_image", resource), x, y, w, ui.theme().error());
        }
        float scale = Math.min(w / (float) size[0], MAX_MEDIA_H / (float) size[1]);
        int dw = Math.max(1, Math.round(size[0] * scale));
        int dh = Math.max(1, Math.round(size[1] * scale));
        int dx = x + (w - dw) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, id, dx, y, 0, 0, dw, dh, size[0], size[1], size[0], size[1]);
        return y + dh;
    }

    /** Width and height of a texture, or {0, 0} when it does not exist. */
    private static int[] readSize(String resource) {
        Identifier id = Identifier.tryParse(resource);
        if (id == null) {
            return new int[]{0, 0};
        }
        Optional<Resource> found = Minecraft.getInstance().getResourceManager().getResource(id);
        if (found.isEmpty()) {
            return new int[]{0, 0};
        }
        try (InputStream in = found.get().open(); NativeImage image = NativeImage.read(in)) {
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (Exception e) {
            ConfeeLog.warn("Cannot read preview image {}: {}", resource, e.getMessage());
            return new int[]{0, 0};
        }
    }

    private int custom(GuiGraphicsExtractor g, Option<?> option, Preview preview, Object value, int x, int y, int w, float delta) {
        if (brokenRenderers.contains(option)) {
            return paragraph(g, Component.translatable("confee.preview.error"), x, y, w, ui.theme().error());
        }
        try {
            PreviewRenderer renderer = renderers.computeIfAbsent(option, o -> preview.renderer().get());
            int h = Math.min(MAX_MEDIA_H, Math.max(8, renderer.height(w)));
            Draw.rect(g, x, y, w, h, ui.theme().panelBgDeep());
            g.enableScissor(x, y, x + w, y + h);
            renderer.render(g, x, y, w, h, value, context, delta);
            g.disableScissor();
            return y + h;
        } catch (RuntimeException | LinkageError e) {
            // a broken preview of another mod must not crash the screen (spec 14)
            ConfeeLog.error("Preview of " + option.path() + " failed", e instanceof RuntimeException re ? re : new RuntimeException(e));
            brokenRenderers.add(option);
            return y;
        }
    }

    private int paragraph(GuiGraphicsExtractor g, Component text, int x, int y, int w, int color) {
        List<FormattedCharSequence> lines = ui.font().split(text, w);
        for (FormattedCharSequence line : lines) {
            Draw.text(g, ui.font(), line, x, y, color);
            y += 10;
        }
        return y;
    }
}

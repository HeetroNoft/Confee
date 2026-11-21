# Screen

[← Back to the README](../README.md)

Confee generates the screen from your options. This page covers what you can add or change on it.

## Previews

Any option can have a preview, shown in a third column on the right of the screen (hidden when the window is too
narrow). It shows the hovered option, or the focused one. Everything is optional:

```java
@Preview(image = "examplemod:textures/preview/quality_{value}.png", text = "examplemod.preview.quality")
public Quality quality = Quality.HIGH;

b.intValue("particleCount", 20)
        .preview(Preview.renderer(ParticlePreview::new).withText("One dot per particle"))
        .build();
```

- `image`: a texture; `{value}` is replaced by the current value in lower case.
- `text`: a translation key, or plain text (`§` formatting codes work).
- `renderer`: a `PreviewRenderer` drawing the value live. It is client-only code, created only when the screen shows
  it; with the annotation, the class needs a public no-argument constructor. Errors are caught and shown as a message.

A renderer can also read the **pending values of the other options** of the config (edited, not saved yet) and the
theme of the screen, through the `PreviewContext`:

```java
public class OutlinePreview implements PreviewRenderer {
    public void render(GuiGraphics g, int x, int y, int width, int height, Object value,
                       PreviewContext context, float delta) {
        boolean rare = context.value("outline.rare", Boolean.class, true); // follows the screen, before "Save"
        g.fill(x, y, x + width, y + height, context.theme().panelBgDeep());
    }
}
```

Renderers written for Confee 0.2 (`render(g, x, y, width, height, value, delta)`) keep working.

`context.path()` is the path of the option being previewed, so one renderer class can serve several options. Put
`@Preview` on a `@Section` class (builder: `.category("outline").preview(...)`) to show one preview for all the options
of the section that have none of their own.

## Layout and decorations

```java
@Config(modId = "examplemod")
@Layout(panelWidth = 460, banner = "examplemod:textures/gui/banner.png", bannerHeight = 40)
public class ExampleConfig {
    public Decoration intro = Decoration.info("Settings of the example mod.");

    @Category(value = "dash", icon = "minecraft:feather")
    public boolean dashEnabled = true;

    public Decoration dashWarning = Decoration.warning("Dashing into lava is still a bad idea.").visibleIf("dash.dashEnabled");

    public Decoration wiki = Decoration.button("Open the wiki", () -> Util.getPlatform().openUri("https://example.com"))
            .icon("minecraft:book");
}
```

- `Decoration`: `header`, `separator`, `info`, `warning`, `text`, `image(texture, height)`, `button(text, action)`;
  then `.icon(...)` and `.visibleIf(...)`. Texts are translation keys or plain text. Buttons run on the client only.
- `@Layout` / `Layout.builder()`: `panelWidth` (200–1000, default 400), `previewWidth` (100–400, default 150),
  `sidebar` (`AUTO` or `HIDDEN`: categories in a dropdown), `banner` + `bannerHeight`, `background` (tiled texture).
- Icons are an item ID (`minecraft:feather`) or a texture ending with `.png`.

## Theme

Every color of the screen can be customized. A token you leave out keeps its default; `accentDark`, `accentLight` and
`onAccentText` are derived from the accent.

```java
@Theme(accent = 0xFF2D7DD2, panelBg = 0xFF23272E)
```

```java
Confee.builder("examplemod").theme(Theme.builder().accent(0xFF2D7DD2).panelBg(0xFF23272E).build());
```

Tokens: `accent`, `accentDark`, `accentLight`, `onAccentText`, `panelBg`, `panelBgDeep`, `buttonFace`,
`buttonFaceHover`, `buttonShadow`, `outline`, `textOnDark`, `textOnLight`, `textDisabled`, `warning`, `error`,
`toggleOffTrack`, `titleBarBg`, `titleBarText`.

## Your own widgets

Extend `OptionWidget<T>` (client only) and draw what you want; read and write the value with `value()` /
`setValue(...)`. The value is validated and saved with the other options.

```java
public class TemperatureWidget extends OptionWidget<Integer> {
    public void render(GuiGraphics g, int x, int y, int width, int height, int mouseX, int mouseY, float delta) {
        g.fill(x, y, x + width * value() / 50, y + height, theme().accent());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return setValue((int) ((mouseX - x()) * 50 / width()));
    }
}
```

Use it with `@Widget(TemperatureWidget.class)` on the field, or from your client initializer:
`ConfeeWidgets.forOption("examplemod", "temperature", TemperatureWidget::new)` (one option) or
`ConfeeWidgets.forType("examplemod", MyEnum.class, MyEnumWidget::new)` (every option of that type in your configs).
If your widget throws, it is replaced by a message and the game keeps running.

Optional methods to override:

| Method | What it does |
| --- | --- |
| `error()` | A message (or null): drawn in red under the widget, and "Save" is disabled while it is not null. |
| `capturesText()` | True while your widget is typing text: plain keys (`[`, `]`, Page Up/Down) are left to it. Let `keyPressed` return false for Ctrl shortcuts. |
| `focusBounds()` | The focused part of a tall widget (`{x, y, width, height}`): only that part is scrolled into view. Call `revealFocus()` after moving it with the keyboard. |
| `renderOverlay`, `overlayBounds`, `overlayClicked`, `overlayScrolled` | Content drawn over the next options while the widget has the focus (a suggestion list, a popup). |

`viewportHeight()` gives the height of the visible part of the panel, to keep a tall widget on the screen.

To match Confee's look, draw with `ConfeeDraw` and `ConfeeIcons`. They also work in preview renderers, and from the
title screen:

| Helper | What it draws |
| --- | --- |
| `ConfeeDraw.button`, `accentButton`, `text`, `textFit`, `focusRing` | Confee's buttons and text (theme colors, no shadow). |
| `ConfeeDraw.tiled(g, "minecraft:block/stone_bricks", x, y, w, h)` | A block texture (or a `.png`) repeated over an area. |
| `ConfeeDraw.entity(g, "minecraft:zombie", x, y, w, h, outlineColor[, outlineOnly])` | A mob: its model when a world is loaded, else a front view from its skin (humanoid mobs). With an outline color, a 1 px line around the silhouette like Glowing; `outlineOnly` draws just that line. |
| `ConfeeIcons.drawItem`, `draw(g, registry, id, ...)`, `name(registry, id)` | Icons and names of items, blocks, mobs, effects... |
| `ConfeeIcons.drawStack(g, snbtOrId, count, x, y)` | An item stack with its count and enchantment glint. |

`Theme.mix(a, b, amount)` and `theme.dimmed(color)` blend colors, to dim a text without drawing a layer over it.

`ConfeeTextField` is Confee's text field for your widgets (theme colors, error, suggestions with icons, items found by
their name with `setRegistry("minecraft:item")`). It takes the same `int` events as `OptionWidget` on every Minecraft
version; draw its suggestions from `renderOverlay`.

### Widgets for other mods' settings

Widgets also work for Forge configs (`ForgeConfigSpec`) shown by Confee: their config ID is `modid:client`,
`modid:common` or `modid:server`, so a companion mod can give a nicer widget to another mod's setting.

They also work for the files Confee's editor opens for mods that do not use Confee. Their config ID is
`ConfeeWidgets.fileId("othermod", "<path from the config folder>")`; numbers are `Long` or `Double`, and an element of
a list is a `[n]` path segment:

```java
ConfeeWidgets.forOption(ConfeeWidgets.fileId("othermod", "othermod/spawns.json"), "spawns.[0].weight", WeightWidget::new);
```

# Confee

Config library for Minecraft mods, with generated Bedrock-style config screens, plus an in-game editor for the config
files of mods that do not use it.

- **For mod developers:** declare a config with one annotated class or a builder. Confee writes the file, loads it, validates it, and gives your mod a config screen in Mod Menu and in the Forge/NeoForge mod list. You write no screen or integration code.
- **For players:** mods without a config screen get a **Config** button that edits their config files in game.

Minecraft 1.20.1 · Fabric, Quilt, Forge, NeoForge · Java 17 · LGPL-3.0

## A config in 10 lines

```java
@Config(modId = "examplemod")
public class ExampleConfig {
    @Comment("Show the HUD")
    public boolean showHud = true;

    @Category("dash") @Range(min = 1, max = 10) @Slider(step = 0.5)
    public double dashDistance = 4.0;
}

// in your mod initializer (Fabric, Quilt, Forge and NeoForge alike)
public static final ConfeeConfig<ExampleConfig> CONFIG = Confee.register(ExampleConfig.class);

// anywhere
if (CONFIG.get().showHud) { ... }
```

This creates `config/examplemod.toml`, with comments and range hints:

```toml
# Show the HUD
showHud = true

[dash]
# Range: 1.0 ~ 10.0
dashDistance = 4.0
```

Your mod's **Config** button opens the generated screen.

## Installing

Players install Confee like any library mod:

| Loader | File | Also needs |
|---|---|---|
| Fabric, Quilt | `confee-<version>+1.20.1-fabric.jar` | Fabric API. Mod Menu to see the Config buttons. |
| Forge, NeoForge (1.20.1) | `confee-<version>+1.20.1-forge.jar` | Nothing else. |

Mods that use Confee declare it as a required dependency. Embedding Confee in another jar (jar-in-jar) is not supported.

## Annotations

The public, non-static, non-transient fields of a `@Config` class are the options; their initial values are the
defaults. The class needs a constructor without arguments.

| Annotation | On | Effect |
|---|---|---|
| `@Config(modId, name = "", scope = COMMON, format = TOML)` | class | Declares the config. With a `name`, the file is `config/<modId>-<name>.<ext>`. |
| `@Theme(accent = 0xFF2D7DD2, ...)` | class | Colors of the screen, see [Theme](#theme). |
| `@Category("key")` | field | Puts the option in a category: a sidebar tab, and a table in the file. |
| `@Comment("...")` | field, class | Comment written in the file, and tooltip when no translation exists. |
| `@Name("...")` | field | Display name when no translation exists. |
| `@Range(min, max)` | number | Bounds. Values out of range are clamped. |
| `@Slider(step)` | number | Shown as a slider. Needs both bounds. |
| `@RequiresRestart` | field | Yellow "!" icon. The player is told to restart after saving. |
| `@Hidden` | field | Saved in the file, but not shown on the screen. |
| `@Pattern("regex")`, `@MaxLength(n)` | String | Text validation. |
| `@Color(alpha = false)` | int | Shown as a color field with a preview. |
| `@Key("name")` | field | Key in the file, when it differs from the field name. |
| `@Section` | nested static class | A field of this type becomes a sub-table. |
| `@Preview(image, text, renderer)` | field | Preview shown in a third column, see [Previews](#previews). |
| `@EnabledIf("path")`, `@VisibleIf("path")` | field | Grays out / hides the option unless the condition holds: `"showHud"`, `"!showHud"` or `"quality=HIGH"`. |
| `@Unit("%")` | number | Unit shown after the value (text or translation key). |
| `@Multiline` | String | Multi-line text area. |
| `@Registry("minecraft:item")` | ResourceLocation, String, List&lt;String&gt; | Autocompletion from a built-in registry. |
| `@Item`, `@Block` | ResourceLocation, String, List&lt;String&gt; | Item / block ID: icon, suggestions with icons, a searchable grid picker and an automatic preview. |
| `@Entity`, `@Biome`, `@Dimension`, `@Structure`, `@Enchantment`, `@Effect`, `@Sound`, `@Fluid` | ResourceLocation, String, List&lt;String&gt; | ID of that registry, entries added by mods included. See [Registry options](#registry-options). |
| `@Description("...")` | field | Text shown under the name on the screen (not written in the file: use `@Comment` for that). |
| `@Suggestions({"a", "b"}, provider = ...)` | String, ResourceLocation, List&lt;String&gt; | Values suggested while typing (fixed and/or from a `SuggestionProvider`). |
| `@Stepper(step = 1)` | number | Field with − and + buttons. |
| `@Keybind` | String | A key such as `key.keyboard.g`, set by pressing it. |
| `@Widget(MyWidget.class)` | field | Your own widget, see [Your own widgets](#your-own-widgets). |
| `@Category("dash", icon = "minecraft:feather")` | field | Category with an icon (item ID or texture). |
| `@Icon("minecraft:compass")` | `@Section` field or class | Icon of the sub-section. |
| `@Layout(...)` | class | Screen layout, see [Layout and decorations](#layout-and-decorations). |

Supported field types: `boolean`, `int`, `long`, `float`, `double` (and their wrappers), `String`, any enum,
`ResourceLocation`, `List` of `String`, `Integer`, `Long`, `Float`, `Double`, `Boolean` or an enum,
`Set<E>` / `EnumSet<E>` of an enum (check boxes), `Map<String, V>` where `V` is one of those types (key → value
editor), and `List<T>` where `T` is a `@Section` class with a public no-argument constructor and only simple fields
(list of objects). Enums with 2 to 4 values are shown as segmented buttons; larger enums are shown as a dropdown.
Color fields (`@Color`) open a color picker when their color square is clicked.

A public field of type `Decoration` is not an option: it adds a title, text, image or button at its place on the
screen (see [Layout and decorations](#layout-and-decorations)).

`@Category` is only allowed in the `@Config` class, not inside a `@Section`.

## Builder

```java
ConfigBuilder b = Confee.builder("examplemod")
        .name("client")
        .scope(ConfigScope.CLIENT)
        .accentColor(0xFF2D7DD2);

SHOW_HUD = b.bool("showHud", true).comment("Show the HUD").build();

CategoryBuilder dash = b.category("dash").comment("Dash settings");
DASH_DISTANCE = dash.doubleRange("distance", 4.0, 1.0, 10.0).slider(0.5).build();
dash.enumValue("difficulty", Difficulty.NORMAL).build();

CONFIG = b.build(); // registers and loads
```

Option methods: `bool`, `intValue` / `intRange`, `longValue` / `longRange`, `floatValue` / `floatRange`,
`doubleValue` / `doubleRange`, `string`, `enumValue`, `enumSet` (check boxes), `list`, `map`, `objectList`, `color`,
`identifier`, `item`, `block`, `keybind`.

```java
b.map("biomeWeights", Integer.class, Map.of("plains", 10, "desert", 3)).build();

ObjectSpec waypoint = ObjectSpec.builder().string("name", "new").intValue("y", 64).bool("visible", true).build();
b.objectList("waypoints", waypoint, List.of(waypoint.element("name", "home", "y", 64, "visible", true))).build();
```

Option modifiers: `.comment()`, `.name()`, `.description()`, `.range()`, `.slider(step)`, `.stepper(step)`,
`.requiresRestart()`, `.hidden()`, `.pattern()`, `.maxLength()`, `.alpha()`, `.preview()`, `.enabledIf()`,
`.visibleIf()`, `.unit()`, `.multiline()`, `.registry()`, `.item()`, `.block()`, `.suggestions(...)`,
`.listener((oldValue, newValue) -> ...)`.

Screen elements: `b.header("...")`, `b.separator()`, `b.info("...")`, `b.image(texture, height)`,
`b.button("...", action)`, or `b.decoration(Decoration...)`. Categories: `b.category("dash").icon("minecraft:feather")`.
Layout: `b.layout(Layout.builder()...build())`.

Each `build()` returns a `ConfigValue<T>` with `get()`, `set(value)`, `getDefault()`, `reset()` and
`addListener(...)`.

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

## Registry options

`@Registry("namespace:registry")` (or a shortcut such as `@Entity`) turns a text field into an ID field: suggestions
while typing, an icon when the type has one (items, blocks, spawn eggs for entities, enchanted books, effect icons,
buckets for fluids), a grid picker for those, and an automatic preview.

- Entries added by other mods are included.
- World registries (biomes, structures, dimensions...) come from the world when one is loaded (datapacks included),
  otherwise from the data files of the game and of every mod, so the screen also works from the main menu.
- `tags = true` (or `.tags()`) also suggests and accepts tags such as `#minecraft:is_forest`. The field must be a
  `String` or `List<String>`.

```java
@Entity
public String favoriteMob = "minecraft:axolotl";

@Biome(tags = true)
public List<String> spawnBiomes = new ArrayList<>(List.of("minecraft:plains", "#minecraft:is_forest"));

b.list("spawnBiomes", String.class, List.of("minecraft:plains")).biome().tags().build();
```

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

Widgets also work for Forge configs (`ForgeConfigSpec`) shown by Confee: their config ID is `modid:client`,
`modid:common` or `modid:server`, so a companion mod can give a nicer widget to another mod's setting.

## Using a config

```java
ConfeeConfig<T> config = ...;
config.get();                          // the annotated instance (null for a builder config)
config.save();                         // writes the file
config.reload();                       // reads the file again; listeners are notified
config.resetAll();                     // defaults in memory, then save() to write them
config.onChange(() -> ...);            // called after the player saves from the screen
config.createScreen(parentScreen);     // to open the screen from your own button (client only)
Confee.get("examplemod", "client");    // look up a registered config
```

Listeners are called whenever a value changes. `onChange` callbacks run after a save from the screen, then the file is
written.

### Scopes

- `CLIENT`: loaded only on the physical client.
- `COMMON`: loaded on both sides, each with its own local file.
- `SERVER`: reserved for the future server-to-client synchronization. It throws for now.

### Formats

`format = ConfigFormat.TOML` (default), `JSON`, `JSON5`, `YAML` or `PROPERTIES`.

- Comments and range hints are written in every format except JSON, which has no comments.
- In `.properties` files, keys are flat (`dash.distance=4.0`) and lists are comma-separated.

### Robustness

Confee never crashes the game because of a file:

- An out-of-range value is clamped.
- A value of the wrong type falls back to the default.
- A broken file is copied to `<file>.broken-<date>`, then recreated with the default values.
- Unknown keys are kept when the file is saved.
- Files are written atomically.

A mistake in a declaration, such as `@Slider` without `@Range`, throws a clear error in a development environment: every
mistake of the class is listed at once, each with how to fix it. In production, it logs a warning and skips the field.

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

## Translations

The screen looks for these keys in your mod's language files:

- `config.<modId>.<configName>.<path>`: option or category name. Use `config.<modId>.<path>` for a config without a name.
- the same key followed by `.tooltip`: the tooltip of the name.
- the same key followed by `.description`: the text under the name.

Without a translation, the screen uses `@Name` / `.name()`, then the key made readable (`dashDistance` becomes
"Dash Distance"). Tooltips fall back to the comment, texts under the name to `@Description` / `.description()`.

## Universal editor

Confee gives a **Config** button to mods that have no config screen, when their config files are found in `config/`.

**Files Confee finds for a mod:**

- `<modid>.*`, `<modid>-*.*`, `<modid>_*.*`, and the files of `config/<modid>/`.
- Case, `-`, `_` and the mod's display name are ignored when matching.

**Formats it can edit:**

- TOML, JSON, JSONC, JSON5, YAML, properties, INI, and Forge legacy `.cfg`.
- Forge/NeoForge config specs (`ForgeConfigSpec`) are edited through Forge itself: real defaults, ranges, comments and translations. Changes apply while the game runs. Server configs are read-only for now.

**How files are changed:**

- Only the text of the values you change is replaced. The order, comments and formatting of the file are kept.
- A copy of the original file is saved in `config/confee/backups/` before the first change. Reset buttons restore its values.
- Complex values (dates, lists of objects, multi-line text...) are shown read-only, with a button to open the file.

A mod that already has a config screen keeps it: Confee never replaces it.

**Declaring your files:** if your mod does not use Confee but you want to point Confee to your files:

```java
Confee.registerExternalFile("examplemod", Path.of("examplemod/settings.json")); // relative to config/
```

**Player settings** live in `config/confee.toml`, also editable from Confee's own Config button:

- `injectConfigButtons`
- `backupExternalFiles`
- `fileMappings`, as `"modid=path"` entries for files Confee could not match
- `ignoredMods`

## API stability

Only the `fr.heetstudio.confee.api` package (and its sub-packages) is public API. Everything else is internal and can
change in any version.

## Building from source

Each Minecraft version has its own branch (`1.20.1`, …). On Windows, `scripts\confee.ps1` picks a suitable JDK and runs
the usual tasks:

```powershell
.\scripts\confee.ps1 fabric        # game client, Fabric + Mod Menu + test mods
.\scripts\confee.ps1 forge         # game client, Forge + test mods
.\scripts\confee.ps1 test          # unit tests
.\scripts\confee.ps1 jars          # build the jars
.\scripts\confee.ps1 publish-dry   # what would be published to Modrinth / CurseForge, without publishing
```

## License

[LGPL-3.0](LICENSE). Author: Heet.

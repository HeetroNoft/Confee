# Builder

[← Back to the README](../README.md)

The builder declares a config from code instead of an annotated class. It gives the same file and the same screen.

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

Each option's `build()` returns a `ConfigValue<T>` with `get()`, `set(value)`, `getDefault()`, `reset()` and
`addListener(...)`. Keep it in a field to read the value. `CONFIG.get()` returns `null` for a builder config.

## Option methods

| Kind | Methods |
| --- | --- |
| Booleans | `bool` |
| Numbers | `intValue` / `intRange`, `longValue` / `longRange`, `floatValue` / `floatRange`, `doubleValue` / `doubleRange` |
| Text | `string`, `keybind` |
| Choices | `enumValue`, `enumSet` (check boxes) |
| Colors | `color` |
| IDs | `identifier`, `item`, `block`, `modId` |
| Collections | `list`, `map`, `objectList` |
| List shortcuts | `itemList`, `blockList`, `modList`, `idList`, `sortedList` (see [Lists](lists.md)) |

```java
b.map("biomeWeights", Integer.class, Map.of("plains", 10, "desert", 3)).build();

ObjectSpec waypoint = ObjectSpec.builder().string("name", "new").intValue("y", 64).bool("visible", true).build();
b.objectList("waypoints", waypoint, List.of(waypoint.element("name", "home", "y", 64, "visible", true))).build();
```

## Option modifiers

Each modifier matches an annotation of the same name, described in [Annotations](annotations.md).

| Kind | Modifiers |
| --- | --- |
| Texts | `.comment()`, `.name()`, `.description()` |
| Numbers | `.range()`, `.slider(step)`, `.stepper(step)`, `.unit()`, `.alpha()` (colors) |
| Text | `.pattern()`, `.maxLength()`, `.multiline()`, `.suggestions(...)` |
| Your own check | `.validator((String s) -> s.isBlank() ? "examplemod.error.empty" : null)` (each value, on a list), `.onFileError(OnFileError.KEEP)` |
| Item stacks | `.itemStack()`, `.itemStack(count, components, maxCount)` on a String or List of String |
| World suggestions | `.suggestions(SuggestionSource.SCOREBOARD_TAGS, SuggestionSource.TEAMS)` |
| IDs | `.registry()`, `.item()`, `.block()`, `.modId()`, `.picker()`, `.pickerStyles()` |
| Lists | `.allowDuplicates()`, `.size()`, `.maxSize()`, `.sorted()`, `.reorderable()`, `.ignoreCase()` |
| Behavior | `.enabledIf()`, `.visibleIf()`, `.requiresRestart()`, `.hidden()`, `.preview()` |
| Changes | `.listener((oldValue, newValue) -> ...)` |

## Screen elements

- Decorations: `b.header("...")`, `b.separator()`, `b.info("...")`, `b.image(texture, height)`,
  `b.button("...", action)`, or `b.decoration(Decoration...)`.
- Categories with an icon: `b.category("dash").icon("minecraft:feather")`.
- Layout: `b.layout(Layout.builder()...build())`.
- Colors: `.accentColor(...)` or `.theme(Theme.builder()...build())`.

See [Screen](screen.md) for what each one does.

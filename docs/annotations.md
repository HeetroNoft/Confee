# Annotations

[← Back to the README](../README.md)

The public, non-static, non-transient fields of a `@Config` class are the options; their initial values are the
defaults. The class needs a constructor without arguments.

## On the class

| Annotation | Effect |
| --- | --- |
| `@Config(modId, name = "", scope = COMMON, format = TOML)` | Declares the config. With a `name`, the file is `config/<modId>-<name>.<ext>`. Scopes and formats: see [Using a config](using-a-config.md#scopes). |
| `@Theme(accent = 0xFF2D7DD2, ...)` | Colors of the screen, see [Theme](screen.md#theme). |
| `@Layout(...)` | Screen layout, see [Layout and decorations](screen.md#layout-and-decorations). |
| `@Comment("...")` | Comment written at the top of the file (on a `@Section` class: above its sub-table). |

## Organizing options

| Annotation | On | Effect |
| --- | --- | --- |
| `@Category("key")` | field | Puts the option in a category: a sidebar tab, and a table in the file. Only allowed in the `@Config` class, not inside a `@Section`. |
| `@Category("key", icon = "minecraft:feather")` | field | Same, with an icon (item ID or texture). |
| `@Section` | nested static class | A field of this type becomes a sub-table. |
| `@Icon("minecraft:compass")` | `@Section` field or class | Icon of the sub-section. |
| `@Key("name")` | field | Key in the file, when it differs from the field name. |
| `@Hidden` | field | Saved in the file, but not shown on the screen. |

## Names and texts

| Annotation | On | Effect |
| --- | --- | --- |
| `@Comment("...")` | field | Comment written in the file, and tooltip when no translation exists. |
| `@Name("...")` | field | Display name when no translation exists. |
| `@Description("...")` | field | Text shown under the name on the screen (not written in the file: use `@Comment` for that). |

Translations take priority over these, see [Translations](translations.md).

## Numbers

| Annotation | On | Effect |
| --- | --- | --- |
| `@Range(min, max)` | number, List of numbers | Bounds (of each value, on a list). Values out of range are clamped. |
| `@Slider(step)` | number | Shown as a slider. Needs both bounds. |
| `@Stepper(step = 1)` | number | Field with − and + buttons. |
| `@Unit("%")` | number | Unit shown after the value (text or translation key). |
| `@Color(alpha = false)` | int | Shown as a color field with a preview; clicking the color square opens a color picker. |

## Text

| Annotation | On | Effect |
| --- | --- | --- |
| `@Pattern("regex")`, `@MaxLength(n)` | String, List of String | Text validation (of each value, on a list). |
| `@Multiline` | String | Multi-line text area. |
| `@Suggestions({"a", "b"}, provider = ...)` | String, ResourceLocation, List&lt;String&gt; | Values suggested while typing (fixed and/or from a `SuggestionProvider`). |
| `@Keybind` | String | A key such as `key.keyboard.g`, set by pressing it. |

## IDs (items, blocks, mobs, mods...)

| Annotation | On | Effect |
| --- | --- | --- |
| `@Item`, `@Block` | ResourceLocation, String, List&lt;String&gt; | Item / block ID: icon, suggestions with icons, a searchable grid picker and an automatic preview. |
| `@Entity`, `@Biome`, `@Dimension`, `@Structure`, `@Enchantment`, `@Effect`, `@Sound`, `@Fluid` | ResourceLocation, String, List&lt;String&gt; | ID of that registry, entries added by mods included. |
| `@Registry("minecraft:item")` | ResourceLocation, String, List&lt;String&gt; | Autocompletion from any built-in registry. |
| `@ModId` | String, List&lt;String&gt; | ID of an installed mod: suggestions with the mods' logos, logo in the field and a mod picker. |
| `@Picker(styles, defaultStyle)` | ID field with a picker | Picker styles: `GRID`, `LIST` (icon, name, ID), `DETAILED` (plus the mod description). The player switches with the buttons of the picker (or Tab) and Confee remembers the choice per kind of ID. Default: grid for items/blocks, list for mods. |

Tags and filters (`only = ...`): see [IDs and registries](registries.md).

## Lists

`@AllowDuplicates`, `@Size`, `@Sorted`, `@Reorderable`, `@IgnoreCase`, and `@Range` / `@Pattern` / `@MaxLength` on a
list: see [Lists](lists.md).

## Behavior on the screen

| Annotation | On | Effect |
| --- | --- | --- |
| `@EnabledIf("path")`, `@VisibleIf("path")` | field | Grays out / hides the option unless the condition holds: `"showHud"`, `"!showHud"` or `"quality=HIGH"`. |
| `@RequiresRestart` | field | Yellow "!" icon. The player is told to restart after saving. |
| `@Preview(image, text, renderer)` | field | Preview shown in a third column, see [Previews](screen.md#previews). |
| `@Widget(MyWidget.class)` | field | Your own widget, see [Your own widgets](screen.md#your-own-widgets). |

## Supported field types

- `boolean`, `int`, `long`, `float`, `double` (and their wrappers), `String`, any enum, `ResourceLocation`.
- `List` of `String`, `Integer`, `Long`, `Float`, `Double`, `Boolean` or an enum.
- `Set<E>` / `EnumSet<E>` of an enum (check boxes).
- `Map<String, V>` where `V` is one of those types (key → value editor).
- `List<T>` where `T` is a `@Section` class with a public no-argument constructor and only simple fields (list of
  objects).

Enums with 2 to 4 values are shown as segmented buttons; larger enums are shown as a dropdown.

A public field of type `Decoration` is not an option: it adds a title, text, image or button at its place on the
screen (see [Layout and decorations](screen.md#layout-and-decorations)).

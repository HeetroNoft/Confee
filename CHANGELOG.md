# Changelog

## 0.2.0 (Minecraft 1.20.5 – 1.20.6)

- First version for Minecraft 1.20.5 – 1.20.6, with its own jars for Fabric/Quilt, NeoForge and Forge.

- Universal editor: lists of objects, lists of lists and mixed lists in other mods' JSON, JSON5, YAML and TOML files (inline arrays and `[[arrays of tables]]`) are now editable, at any depth: each element opens in its own screen, with move up / move down / remove buttons and "+ Add" (a copy of the last element). Changes stay in memory until "Save".
- Universal editor: HOCON files (`.conf`, `.hocon`). Values written behind a substitution (`${...}`) are shown read-only with what they resolve to; values of included files are edited in those files.
- API: your own widgets (`ConfeeWidgets.forOption` / `forType`) also work for the files Confee's editor opens for mods that do not use Confee, with `ConfeeWidgets.fileId(modId, pathInConfig)` as config ID.

## 0.1.0 (Minecraft 1.20.1)

First version.

- Config API by annotations or builder: TOML (default), JSON, JSON5, YAML or properties files, with comments, ranges and allowed values written in the file.
- Generated Bedrock-style config screen with a customizable theme: toggles, sliders, dropdowns, segmented buttons, text/number/color fields, list editor, search, keyboard navigation and narration.
- Config buttons added for you in Mod Menu (Fabric/Quilt) and in the Forge/NeoForge mod list.
- Universal editor: edits the config files of mods that do not use Confee (TOML, JSON, JSON5, YAML, properties, INI, Forge legacy .cfg), changing only the edited values, with a backup of the original file.
- Forge/NeoForge config specs edited in game, applied while the game runs.
- Optional preview column for each setting: image (can follow the value), custom live rendering, formatted text.
- Conditions between settings (`@EnabledIf`, `@VisibleIf`), key → value maps, lists of objects, enum lists, multi-line text, units, registry autocompletion (`@Registry`).
- Descriptions under the setting names (`@Description`).
- Item and block settings (`@Item`, `@Block`): icons, suggestions with icons, a searchable item grid and an automatic preview.
- Autocompletion: forgiving search, scrolling list, values suggested by the mod (`@Suggestions`).
- New widgets: color picker, stepper (− / +), key binding, check boxes for sets of enum values.
- Customization: titles, separators, info/warning blocks, images and action buttons among the settings (`Decoration`), category icons, layout options (`@Layout`: panel width, preview width, banner, background), your own widgets (`@Widget`, `ConfeeWidgets`).
- Typed registry options: `@Entity`, `@Biome`, `@Dimension`, `@Structure`, `@Enchantment`, `@Effect`, `@Sound`, `@Fluid` (and any registry with `@Registry`), entries of every mod included, optional tags (`tags = true`). World registries (biomes...) come from the world when one is loaded, else from the game and mod files. Icons per type (spawn eggs, effect icons, enchanted books, buckets).
- Universal editor: IDs and colors in other mods' files and Forge configs are recognized (icons, suggestions, picker, color picker). Your own widgets also work for Forge configs.
- Runs on Quilt: the Fabric jar now declares `fabricloader >= 0.14.21`.
- Bedrock-style tooltips that never cover the hovered element or an open suggestion list.
- Clearer errors for mod developers: every mistake of a config class at once, each with how to fix it.
- English and French.

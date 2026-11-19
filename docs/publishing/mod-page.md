# Confee — mod page draft (Modrinth / CurseForge)

Draft to review before the first release. Nothing here is published automatically.

## Summary (Modrinth "summary", 256 characters max)

Config library with auto-generated Bedrock-style screens, and an in-game editor that adds a Config button to mods
that have none and edits their config files safely.

## Categories / tags (spec 16)

Library, Utility. Environment: client and server (the server side is optional for players).

## Description

**Confee** gives every mod a clean config screen, in the style of Bedrock Edition.

### For players

- **A Config button for mods that have none.** Open Mod Menu (Fabric/Quilt) or the mod list (Forge/NeoForge), pick
  a mod, and edit its config file in game: TOML, JSON, JSON5, YAML, properties, INI and Forge `.cfg` files.
- **Safe edits.** Only the values you change are rewritten; comments, order and formatting stay untouched. The
  original file is backed up first, and a broken file never crashes the game.
- **Forge configs too.** Configs declared with Forge's config system are edited and applied while the game runs.
- **Smart fields.** Item, block and mob IDs show their icon, get suggestions and a searchable picker; colors get a
  color picker.
- **Your choice of screen.** Mods with their own config screen keep it, unless you list them in Confee's settings
  (`forceConfeeEditor`): their Config button then opens the Confee editor on their files, no restart needed.

### For mod developers

- Declare a config with **annotations** or a **builder**: Confee writes the file (with comments, ranges and allowed
  values) and builds the screen.
- Toggles, sliders, steppers, dropdowns, check boxes, text and multi-line fields, colors, key binds, lists, maps and
  lists of objects.
- **Registry options**: items, blocks, entities, biomes, dimensions, structures, enchantments, effects, sounds,
  fluids or any registry, entries of other mods and tags included.
- Conditions between options, descriptions, units, previews (image, live rendering, text), decorations (titles,
  info blocks, images, buttons), category icons, layout options, a customizable theme and your own widgets.
- Clear errors in development: every declaration mistake at once, each with how to fix it.

See the README for the API.

### Compatibility

- Minecraft 1.20.1.
- Fabric and Quilt: one jar (needs Fabric API; Mod Menu recommended to see the Config buttons).
- Forge 47+ and NeoForge 1.20.1: one jar.
- Works next to Mod Menu, Cloth Config and YACL.

### Links

- Source and issues: https://github.com/HeetroNoft/Confee (the repository is private for now: make it public
  before using these links on the page)
- License: LGPL-3.0

## Gallery (files in `screenshots/`, taken at 1280×720, GUI scale 2)

| File | Caption |
|---|---|
| `confee-40-general-top.png` | Generated screen: banner, info block, descriptions, stepper and a custom widget |
| `confee-29-preview-image.png` | Preview column, effect and enchantment icons, key bind field |
| `confee-48-entity-suggestions.png` | Entity suggestions with spawn egg icons |
| `confee-42-item-picker.png` | Searchable item picker |
| `confee-43-color-picker.png` | Color picker |
| `confee-51-raw-detected.png` | Editing another mod's file: item IDs recognized |
| `confee-22-forge-client-spec.png` | A Forge config, with a widget provided by another mod |

## Before publishing

- Icon: `common/src/main/resources/assets/confee/img/ConfeeIconPromo@4x.png` (512×512).
- Check the release type (`release_type` in `gradle.properties`, `beta` for now).
- Remaining steps: see `TODO.md`, section "Publication".

# Confee: what Infernal Mobs still builds itself

Local notes (ignored by git). Written against Confee 0.3.0, after porting the six branches of Infernal Mobs
(mc1.20.1 to mc26.3) to it.

The previous list is done in 0.3.0 and used by Infernal Mobs: `@ItemStack` with `onFileError = KEEP` (the loot lists,
`LootListWidget` and `LootItemChoice` deleted), search by in-game name, `@Suggestions(sources = SCOREBOARD_TAGS)`
(`EntityTagSuggestions` deleted), `PreviewContext#path()` and `@Preview` on a `@Section` (12 renderer classes down to
4), `ConfeeDraw.entity` / `ConfeeDraw.tiled` (hand-drawn zombie and wall deleted), `ConfeeIcons.drawStack`,
`Theme.mix`. `ConfeeTextField` is no longer needed: the custom widget is gone.

Each item: what Infernal Mobs had to work around, and what to add to Confee.

## 1. Preview height per option

- **Seen in:** the `@Preview` of the `outline` section shows three scenes (one zombie per tier, a zombie behind a
  wall, zombies going away). `PreviewRenderer#height(int width)` does not know which option is previewed, so the
  renderer returns the tallest scene: the "max distance" scene has an empty band under it.
- **To add:** `height(int width, PreviewContext context)` (default: calls `height(width)`), or `PreviewContext#path()`
  readable from `height`.

## 2. `ConfeeDraw.entity`: the same drawing with and without outline

- **Seen in:** with `outlineColor = 0`, Confee draws the 3D model when a world is loaded; with an outline, always the
  front view. In one scene, a tier without outline would be a 3D model next to flat outlined zombies.
- **Worked around:** `NO_OUTLINE = 0x00FFFFFF` (transparent but not 0) to force the front view.
- **To add:** a style parameter, e.g. `ConfeeDraw.entity(..., EntityStyle.FRONT_VIEW | MODEL | AUTO)`, or keep the
  front view whenever a renderer asks for it once.

## 3. `ConfeeDraw.entity`: size control

- **Seen in:** the front view rounds a scale above 1 down to a whole number (1.5 becomes 1), and the box must leave
  1 px around the body for the outline. The distance scene had to change its scales (all ≤ 1) and the other scenes
  use only ×1 or ×2.
- **To add:** a variant taking a scale (`entityScaled(g, id, x, y, scale, outline, outlineOnly)`), or an option to
  allow non-integer scales; return the drawn bounds (`{x, y, width, height}` of the body) so a caller can place
  labels and lines on the silhouette.

## 4. Read an item stack on the server side

- **Written in Infernal Mobs:** `ItemConfigHelper` parses each loot SNBT into an `ItemStack` itself, with
  version-specific code (`ItemStack.CODEC` from 1.20.5, `ItemStack.of` + `count` → `Count` byte on 1.20.1).
- **Why:** `@ItemStack` stores strings; Confee reads both SNBT formats on every version but only for its screen.
- **To add:** a common (not client) helper, e.g. `ConfeeStacks.read(String snbt, RegistryAccess)` →
  `Optional<ItemStack>`, accepting the same formats as `@ItemStack`. With it, `ItemConfigHelper` shrinks to a loop.
  Maybe `ConfigValue<List<ItemStack>>` directly.

## 5. Know whether a stack can be drawn

- **Seen in:** the loot preview draws the slot background, then `ConfeeIcons.drawStack`; for a refused stack it
  returns false after the slot is drawn, which leaves an empty slot at the end of the grid.
- **To add:** `ConfeeIcons.canDrawStack(snbtOrId)` (or a slot background option in `drawStack`, drawn only when the
  stack is valid, with an error tint for a refused one).

## 6. A kept refused value blocks every save

- **Seen in:** with one broken loot line kept from the file (`onFileError = KEEP`), "Save" stays disabled for the whole
  screen: a player who only wants to change a rarity must first fix or remove a loot line they did not touch.
- **To add:** let "Save" go through while the only errors are kept values that were not edited (they are written back
  as they were), and only block on values changed in the screen. Or a "Remove the broken values" action on the error.

## 7. Untranslated key in the log

- **Seen in:** `'{id:"minecraft:diamond",count:3' is refused by the mod: confee.error.stack; kept as written` (the
  KEEP warning prints the translation key, not the message).
- **To add:** translate the message in English for the log (as the server has no language).

## 8. Driving the screen from a dev script

- **Written for the port (not committed):** a script that opens the Infernal Mobs screen, focuses each option with a
  preview and takes a screenshot, on 26.3 and 1.20.1. It needs Confee's internal classes (`ConfeeScreens.forMod`,
  `ConfigScreen#openSection` / `focusOption`) and changes with each Minecraft version (screen, screenshot, key events).
- **To add:** a public dev helper, e.g. `ConfeeDev.screenshots(modId, "eliteRarity", "outline.maxDistance", ...)`
  (opens the screen at a given size and GUI scale, focuses each option, saves `screenshots/<modid>-<path>.png`, quits),
  or at least public `openCategory` / `focusOption` on the config screen.

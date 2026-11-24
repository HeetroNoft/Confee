# Confee: improvements noted while porting Infernal Mobs

Local notes (ignored by git). Each item says what was missing in Confee 0.2.0, how Infernal Mobs works around it, and what to add to Confee.

## 1. Item stack option (item + count + components, stored as SNBT)

- **Written in Infernal Mobs:** `LootListWidget` (~500 lines) and `LootItemChoice`: one line per stack with three
  fields (item searched by its in-game name, extra SNBT, count), suggestions, errors, "+ Add", "×".
- **Why:** Confee has `@Item` (an item ID) but no type for a whole **stack**. The loot lists are SNBT strings
  (`{id:'minecraft:diamond',count:3,components:{...}}`), the format of the file must not change.
- **To add:** `@ItemStack` on `String` / `List<String>`: item field with Confee's picker and suggestions, count
  stepper (`min 1`, `max` = max stack size or free), optional components field checked as SNBT, written as SNBT.
  `@ItemStack(count = false, components = false)` to hide parts. Older SNBT keys accepted on read (`Count` of
  1.20.1). With it, `LootListWidget` and `LootItemChoice` are deleted.

## 2. Search items by their in-game name

- **Written in Infernal Mobs:** `LootItemChoice.searchKey` = translated name + ID, so "diam" finds "Épée en diamant".
- **To add (if `@Item` suggestions only match the ID):** match the translated name too, in the player's language.

## 3. Keep a refused list value in the file

- **Why we do not use `@Validator` on the loot lists:** on file load, Confee drops a list value refused by the mod
  validator (`Validated.refused(null, ...)`). A long hand-written loot line with one typo would be lost for good.
  Infernal Mobs keeps it in the file and skips it when the world loads (log line), the screen blocks "Save" through
  `OptionWidget#error`.
- **To add:** `@Validator(value = ..., onFileError = KEEP)`: a refused value read from the file is kept as written
  (warning in the log), the screen shows the error on that value and blocks "Save" until it is fixed or removed.

## 4. Mob drawing in previews (and the Glowing outline)

- **Written in Infernal Mobs:** `ConfigPreviews.zombie(...)`: a front view of a zombie drawn part by part from the
  vanilla skin (head, body, arms, legs UVs), at any scale, with a 1 px outline following the silhouette like the
  Glowing effect, or the outline alone (mob hidden behind blocks).
- **Why:** `ConfeeIcons` draws a spawn egg for an entity, not the mob itself.
- **To add:** `ConfeeDraw.entity(g, "minecraft:zombie", x, y, height, outlineColor)`: the mob model in the GUI
  (inventory style, also without a world), optional colored outline; `outlineOnly = true` to draw just the outline.

## 5. Tiled block texture

- **Written in Infernal Mobs:** `ConfigPreviews.wall(...)`: stone bricks tiled over an area with scissor, to draw a wall
  in the "through walls" preview, and again inside the silhouette.
- **To add:** `ConfeeDraw.tiled(g, "minecraft:block/stone_bricks", x, y, width, height)` (block texture from the atlas
  or a texture path), clipped to the area.

## 6. Item icon with its count

- **Written in Infernal Mobs:** the loot preview draws the icon with `ConfeeIcons.drawItem`, then the count itself in
  the corner.
- **To add:** `ConfeeIcons.drawStack(g, snbtOrId, count, x, y)` with the vanilla count decoration (and enchantment glint
  when the components have enchantments).

## 7. One renderer class per option

- **Written in Infernal Mobs:** 12 renderer classes (`EliteRarity`, `UltraRarity`, `OutlineRare`...) that only give
  the path of their option to a shared base class, because `@Preview(renderer = X.class)` creates `X` without telling
  it which option it previews.
- **To add:** `PreviewContext#path()` (path of the option being previewed). One class could then serve several
  options: `@Preview(renderer = RarityPreview.class)` on the three rarities.

## 8. One preview for a whole section

- **Seen in:** the six `outline.*` options all show the outline scenes.
- **To add:** `@Preview` on a `@Section` class (or `@Category`): shown when any option of the section is hovered, with
  `PreviewContext#path()` telling which one.

## 9. Scoreboard tags as suggestions

- **Written in Infernal Mobs:** `EntityTagSuggestions`: walks the entities of the client world and collects their
  scoreboard tags (`entityTags()` / `getTags()` depending on the version).
- **To add:** a built-in source, e.g. `@Suggestions(sources = SuggestionSource.SCOREBOARD_TAGS)` (also `TEAMS`,
  `OBJECTIVES`), with `needsWorld()` handled by Confee.

## 10. Color helpers for previews

- **Written in Infernal Mobs:** `ConfigPreviews.mix(color, background, amount)` to dim the tiers that are not focused
  (text cannot be dimmed by drawing a translucent layer on every version: some draw the text above the fills).
- **To add:** `Theme#mix(a, b, amount)` / `Theme#dimmed(color)` as public helpers.

## 11. Edit box events across versions

- **Written in Infernal Mobs:** each branch converts the `OptionWidget` int events to the vanilla `EditBox` ones
  (`KeyEvent`/`CharacterEvent`/`MouseButtonEvent` from 1.21.9, ints before) by hand.
- **To add:** a public `ConfeeTextField` for custom widgets (Confee's own text field: theme colors, error border,
  suggestions), taking Confee's int events on every version. `LootListWidget` would use three of them per line.

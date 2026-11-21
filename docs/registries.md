# IDs and registries

[← Back to the README](../README.md)

Suggestions find registry entries by their ID and by their name in the player's language ("épée" finds
`minecraft:diamond_sword` in French). `@Suggestions(sources = SuggestionSource.SCOREBOARD_TAGS)` (also `TEAMS`,
`OBJECTIVES`) suggests values read from the loaded world.

`@Registry("namespace:registry")` (or a shortcut such as `@Item` or `@Entity`) turns a text field into an ID field:
suggestions while typing, an icon when the type has one (items, blocks, spawn eggs for entities, enchanted books,
effect icons, buckets for fluids), a grid picker for those, and an automatic preview.

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

Shortcuts: `@Item`, `@Block`, `@Entity`, `@Biome`, `@Dimension`, `@Structure`, `@Enchantment`, `@Effect`, `@Sound`,
`@Fluid`.

## Filters

`@Entity`, `@Item`, `@Block`, `@Effect`, `@Enchantment` and `@Biome` take `only = ...` to limit the IDs to what the
game already groups (several filters = any of them). The suggestions and the picker only offer these IDs, and the
screen shows an error on a value outside them. The file is not checked when it is loaded: at that time the registries
are not complete yet (mods' items, biomes of the world).

```java
@Entity(only = EntityFilter.MONSTER)
public String boss = "minecraft:zombie";

@Item(only = {ItemFilter.FOOD_AND_DRINKS, ItemFilter.INGREDIENTS})
public List<String> snacks = new ArrayList<>(List.of("minecraft:apple"));

b.string("boss", "minecraft:zombie").entity(EntityFilter.MONSTER).build();
```

| Filter | Values | From |
| --- | --- | --- |
| `EntityFilter` (`@Entity`) | `MONSTER`, `CREATURE`, `AMBIENT`, `AXOLOTLS`, `UNDERGROUND_WATER_CREATURE`, `WATER_CREATURE`, `WATER_AMBIENT`, `MISC`, `LIVING` | spawn category of the entity type; `LIVING` = has attributes |
| `ItemFilter` (`@Item`, `@Block`) | `BUILDING_BLOCKS`, `COLORED_BLOCKS`, `NATURAL_BLOCKS`, `FUNCTIONAL_BLOCKS`, `REDSTONE_BLOCKS`, `TOOLS_AND_UTILITIES`, `COMBAT`, `FOOD_AND_DRINKS`, `INGREDIENTS`, `SPAWN_EGGS`, `BLOCKS` | creative tabs (mods' items included); `BLOCKS` = places a block |
| `EffectFilter` (`@Effect`) | `BENEFICIAL`, `HARMFUL`, `NEUTRAL` | effect category |
| `EnchantmentFilter` (`@Enchantment`) | `ARMOR`, `ARMOR_FEET`, `ARMOR_LEGS`, `ARMOR_CHEST`, `ARMOR_HEAD`, `WEAPON`, `DIGGER`, `FISHING_ROD`, `TRIDENT`, `BREAKABLE`, `BOW`, `WEARABLE`, `CROSSBOW`, `VANISHABLE`, `TREASURE`, `CURSE` | enchantment category, treasure / curse |
| `BiomeFilter` (`@Biome`) | `OVERWORLD`, `NETHER`, `END` | biome tags `is_overworld`, `is_nether`, `is_end` |

## Mod IDs

`@ModId` (or `.modId()`, `b.modList(...)`) is a field for the ID of an installed mod: suggestions with the mods' logos,
the logo in the field and a mod picker.

## Picker styles

`@Picker(styles, defaultStyle)` (or `.picker()` / `.pickerStyles()`) chooses the styles of the picker: `GRID`, `LIST`
(icon, name, ID) and `DETAILED` (plus the mod description). The player switches with the buttons of the picker (or
Tab) and Confee remembers the choice per kind of ID. Default: grid for items/blocks, list for mods.

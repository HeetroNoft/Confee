# Lists

[← Back to the README](../README.md)

A list never holds the same value twice. In the screen, values already in the list are not suggested, not offered by
**+ Add** and hidden in the item / block picker; a duplicate typed by hand is removed when the player saves. In the
file, duplicates are removed when it is loaded (first one kept, a warning in the log).

| Annotation | Builder | In the file | In the screen |
| --- | --- | --- | --- |
| `@AllowDuplicates` | `.allowDuplicates()` | Duplicates kept. | Every value is suggested. |
| `@Size(min = 1, max = 5)` | `.size(1, 5)`, `.maxSize(5)` | Extra values removed; a list too short is replaced by the default. | **+ Add** greyed out at the max, ✕ at the min. |
| `@Sorted` | `.sorted()` | Sorted (texts and numbers in natural order, enums in declaration order). | No move arrows; sorted when saved. |
| `@Reorderable` | `.reorderable()` | Order kept as the player sets it. | ▲ ▼ move buttons (hidden without it). Also on lists of objects. Not with `@Sorted`. |
| `@IgnoreCase` | `.ignoreCase()` | `Dirt` and `dirt` are duplicates. | Same for the suggestions. |
| `@Range(min = 1, max = 64)` | `.range(1, 64)` | Each value: out of range values clamped. | Error on the field of the value. |
| `@Pattern("[a-z_]+")`, `@MaxLength(16)` | `.pattern("[a-z_]+")`, `.maxLength(16)` | Each value: one that does not fit gives the default value. | Error on the field of the value. |

```java
@Size(max = 5) @Sorted @IgnoreCase
public List<String> labels = new ArrayList<>(List.of("alpha", "beta"));

@Range(min = 1, max = 64)          // on a list: each value
public List<Integer> stackSizes = new ArrayList<>(List.of(16, 64));
```

## Builder shortcuts

```java
b.itemList("loot", "minecraft:diamond", "minecraft:emerald").build();      // icons, suggestions, item picker
b.blockList("blacklist", "minecraft:dirt").tags().build();                  // blocks and #tags
b.idList("mobs", "minecraft:entity_type", "minecraft:zombie").build();     // any registry
b.sortedList("labels", String.class, List.of("alpha", "beta")).maxSize(5).build();
```

## Mistakes

A list option that does not fit its type (for example `@Size` on a `String`, `@IgnoreCase` on a `List<Integer>`, or
a default value with duplicates) stops the game in a development environment with a message that says how to fix
it.

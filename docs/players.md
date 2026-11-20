# Confee for players

[← Back to the README](../README.md)

## Installing

Players install Confee like any library mod:

| Loader | File | Also needs |
| --- | --- | --- |
| Fabric, Quilt | `confee-<version>+1.21.3-fabric.jar` | Fabric API. Mod Menu to see the Config buttons. |
| NeoForge | `confee-<version>+1.21.3-neoforge.jar` | Nothing else. |
| Forge | `confee-<version>+1.21.3-forge.jar` | Nothing else. |

Mods that use Confee declare it as a required dependency.

## Universal editor

Confee gives a **Config** button to mods that have no config screen, when their config files are found in `config/`.

**Files Confee finds for a mod:**

- `<modid>.*`, `<modid>-*.*`, `<modid>_*.*`, and the files of `config/<modid>/`.
- Case, `-`, `_` and the mod's display name are ignored when matching.

**Formats it can edit:**

- TOML, JSON, JSONC, JSON5, YAML, HOCON (`.conf`, included files too), properties, INI, and Forge legacy `.cfg`.
- Forge/NeoForge config specs (`ForgeConfigSpec`) are edited through Forge itself: real defaults, ranges, comments and
  translations. Changes apply while the game runs. Server configs are read-only for now.

**How files are changed:**

- Only the text of the values you change is replaced. The order, comments and formatting of the file are kept.
- A copy of the original file is saved in `config/confee/backups/` before the first change. Reset buttons restore its
  values.
- Complex values (dates, lists of objects, multi-line text...) are shown read-only, with a button to open the file.

A mod that already has a config screen keeps it: Confee never replaces it.

## Settings

Player settings live in `config/confee.toml`, also editable from Confee's own Config button:

- `injectConfigButtons`
- `backupExternalFiles`
- `fileMappings`, as `"modid=path"` entries for files Confee could not match
- `ignoredMods`
- `forceConfeeEditor`: mod IDs whose own config screen is replaced by the Confee editor (applies on the next click)

## For mod developers who don't use Confee

If your mod does not use Confee but you want to point Confee's editor to your files:

```java
Confee.registerExternalFile("examplemod", Path.of("examplemod/settings.json")); // relative to config/
```

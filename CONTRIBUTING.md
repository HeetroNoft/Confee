# Contributing to Confee

Bug reports, ideas and pull requests are welcome. Releases on Modrinth and CurseForge are published by the maintainer
only.

## Before you start

- For anything bigger than a small fix, open an issue first so we can agree on the approach.
- The design reference is [`CONFEE_SPEC.md`](CONFEE_SPEC.md) (in French). Points marked ❓ are still open: please ask
  before working on them.
- Version 1 has a fixed scope: no server → client sync, no presets, no editing of server configs.

## Branches

There is one branch per Minecraft version (`1.20.1`, …) and no `main`. Open your pull request against the branch of
the Minecraft version it targets.

## Building and testing

You need a JDK 17 (Gradle 8.8 also runs on a JDK 21).

```sh
./gradlew build               # everything, with the unit tests
./gradlew :common:test        # unit tests only
./gradlew :fabric:runTestmodClient
./gradlew :forge:runTestmodClient
```

On Windows, `.\scripts\confee.ps1` picks a JDK and lists the usual tasks (clients, servers, screenshots, Quilt and
NeoForge test environments).

Please say in your pull request how you tested your change: unit tests, which loader you launched, client or
dedicated server.

## Rules the code follows

- Java 21 only (Minecraft 1.20.5+), no Kotlin.
- NeoForge and Forge each have their own jar since 1.20.2. The Fabric jar must also run on Quilt.
- No client class (`Screen`, `Minecraft`, rendering…) in code that a dedicated server loads.
- `fr.heetstudio.confee.api` is the public API. Do not break it without saying so in the pull request; everything
  else is internal.
- No new required runtime dependency besides the loader and Fabric API. A bundled library must be relocated.
- A config file must never crash the game: catch the error, log it with the `[Confee]` prefix and fall back cleanly.
- Files of other mods are edited surgically: only the text of the changed values is replaced, everything else is
  copied byte for byte. Writes are atomic (temporary file, then rename).
- Every displayed text goes through the language files (`en_us.json` and `fr_fr.json`).
- UI widgets are drawn in code (no textures), follow the GUI scale and work with the keyboard.

## Tests

- Each format adapter has unit tests: opening and saving without changes gives a byte-identical file, and changing a
  value changes only that value.
- A new option or annotation is also added to the test mod (`src/testmod`).

## Commits

[Conventional Commits](https://www.conventionalcommits.org/) in English, split by area, the scope being the module
or package: `feat(ui): ...`, `fix(fabric): ...`, `docs: ...`, `build: ...`, `test: ...`. Code, comments and Javadoc
are in English.

## License

By contributing, you agree that your contribution is released under the [LGPL-3.0](LICENSE), like the rest of the
project.

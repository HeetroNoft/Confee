# Using a config

[← Back to the README](../README.md)

```java
ConfeeConfig<T> config = ...;
config.get();                          // the annotated instance (null for a builder config)
config.save();                         // writes the file
config.reload();                       // reads the file again; listeners are notified
config.resetAll();                     // defaults in memory, then save() to write them
config.onChange(() -> ...);            // called after the player saves from the screen
config.createScreen(parentScreen);     // to open the screen from your own button (client only)
Confee.get("examplemod", "client");    // look up a registered config
```

Listeners are called whenever a value changes. `onChange` callbacks run after a save from the screen, then the file is
written.

## Scopes

- `CLIENT`: loaded only on the physical client.
- `COMMON`: loaded on both sides, each with its own local file.
- `SERVER`: reserved for the future server-to-client synchronization. It throws for now.

## Formats

`format = ConfigFormat.TOML` (default), `JSON`, `JSON5`, `YAML` or `PROPERTIES`.

- Comments and range hints are written in every format except JSON, which has no comments. Confee marks the
  comments it writes with `#:` (`//:` in JSON5). Comments players add by hand (`#`, `//`) are kept when the file
  is saved, and only the values that changed are rewritten.
- In `.properties` files, keys are flat (`dash.distance=4.0`) and lists are comma-separated.

## Robustness

Confee never crashes the game because of a file:

- An out-of-range value is clamped.
- A value of the wrong type falls back to the default.
- A broken file is copied to `<file>.broken-<date>`, then recreated with the default values.
- Unknown keys are kept when the file is saved.
- Files are written atomically.

A mistake in a declaration, such as `@Slider` without `@Range`, throws a clear error in a development environment: every
mistake of the class is listed at once, each with how to fix it. In production, it logs a warning and skips the field.

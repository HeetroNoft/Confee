# Translations

[← Back to the README](../README.md)

The screen looks for these keys in your mod's language files:

- `config.<modId>.<configName>.<path>`: option or category name. Use `config.<modId>.<path>` for a config without a name.
- the same key followed by `.tooltip`: the tooltip of the name.
- the same key followed by `.description`: the text under the name.

```json
"config.examplemod.dash.dashDistance": "Dash distance",
"config.examplemod.dash.dashDistance.tooltip": "How far the dash goes, in blocks"
```

Without a translation, the screen uses `@Name` / `.name()`, then the key made readable (`dashDistance` becomes
"Dash Distance"). Tooltips fall back to the comment, texts under the name to `@Description` / `.description()`.

In a development environment, the dev report lists the keys still missing, ready to copy into your language file.

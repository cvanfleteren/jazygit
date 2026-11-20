# 163. A feature owns its settings

Date: 2026-11-20

## Context

A feature keeps its own part of the model (ADR 157), declares its own keys and draws its own content (ADR 154), but
its settings are the kernel's. `Settings`, in `kernel-settings`, is one record of every setting, from the theme to the
agents of ADR 161, and `Setting.ALL` one list of them: adding a setting meant a component of the record, its default,
and a line in the list. So `Agents` and `AgentProgram` sit in the kernel, not in `features-agent`, and the settings
panel (`features-chrome/settings`) has a field, a row and a `case` for each setting it shows, the GitHub ones too.

The file side was already close to what a feature needs: each `Setting` read its key from `config.toml`, said which
keys were wrong, wrote only what changed and kept the rest of the user's file (ADR 111). What tied it to the kernel was
that a setting was package-private, and pointed, through a lens, into the one record.

## Decision

- **A setting is a `SettingKey<T>`**, public, in `kernel-settings`: its path in `config.toml` (`ui.theme`, `agents`),
  its initial value, its `Codec<T>`, and when it is kept (`Kept`). Whoever owns the setting declares the key as a
  constant, as a feature declares a `Part` (ADR 157).
- **A `Codec<T>` reads a setting from the file and keeps it there.** `decode(key, value)` gives a `Decoded<T>`: what
  the value holds, if anything, and the keys of what is wrong in it. `encode(key, value)` gives the entries kept, by
  their path. Both take the key, so one codec, such as `Codec.bool()`, serves every key it fits, and says which key is
  wrong. `Codec.whole` is a value valid as a whole or not at all, `text` and `bool` the common ones, `optional()` one
  that may be unset and then keeps nothing, and `readOnly` one only the user writes.
- **`Kept`** is `ALWAYS` (the theme, so a new file shows it), `WHEN_CHANGED` (the others: set back to its initial
  value, the entry goes, and a table it leaves empty), or `NEVER` (only the user writes it, as the editors, the diff
  filters and the agents; saving leaves it as it is in the file).
- **`Settings` becomes the values of the settings by key**, as `Parts` holds the parts: `settings.get(KEY)` and
  `settings.with(KEY, value)`. A key never set gives its initial value, and a setting set back to it is not kept, so two
  settings with the same values are equal. Until the callers move, the record's accessors stay, as one-line
  delegates. No key may have the path of another, nor a path inside another's; a test refuses both.
- **The app gathers the keys**: the kernel's own, and each feature's, through `Feature` (ADR 127), as it gathers the
  keymap. A feature that is not built in contributes none, and its entries in the file are left as they are; a key
  nobody declares is not reported, so going back to an older jazygit is no error.
- **The settings panel is drawn from sections** that the kernel and the features contribute through `Feature`, each
  with its rows, so `features-chrome` no longer names a feature's setting. The GitHub rows move to `features-github`.
- `config.toml` keeps every key it has; no setting changes its path or its meaning.

## Consequences

- The work goes in steps, each leaving `config.toml` and the panel as they were: `SettingKey`, `Codec` and `Kept`
  first, with `Settings` still a record; then `Settings` as values by key; then sections on `Feature`; then the
  agents' settings move to `features-agent`.
- `SettingsRoundTripTest` holds the file byte for byte meanwhile: a user's file is read, a change to each setting the
  app writes is saved, and what is written is compared to what was written before the change of code.
- A setting's value is cast when read from `Settings`, as a part's is; only `with`, which takes a value of the key's
  type, puts one there.
- A key declared other than as a constant the app gathers is never read.
- Writing a list the user owns back from the panel, which loses the comments inside it, is for a later ADR; so are
  settings per repository.

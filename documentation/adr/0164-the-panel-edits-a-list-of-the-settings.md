# 164. The panel edits a list of the settings

Date: 2026-11-20

## Context

ADR 163 lets a feature declare its own settings and its own section of the settings panel. The panel could only
change a setting of one value, as a theme or a switch: the lists the user keeps in `config.toml`, as the editors
(ADR 110), the diff filters (ADR 144) and the agents (ADR 161), were only read, never written. Adding an agent meant
editing the file by hand.

A list is kept as an array of tables, `[[agents]]`. The TOML library jazygit uses keeps the comments of the keys it
leaves alone, but a value it writes anew loses the comments inside it: an array of tables written back loses those
between and inside its tables, and the blank lines between them. The file may also have been changed since it was
read: by the user in an editor (ADR 111), or by another jazygit.

## Decision

- **A list the panel edits is a setting like any other** (ADR 163): a `SettingKey<Entries<E>>` with
  `Codec.tables(TableCodec<E>)`. A `TableCodec` reads an entry from its table, with the keys of what is wrong in it,
  and writes one back.
- **Nothing in the file is lost but the comments inside the array.** `Entries` keeps, with each entry, what its table
  had at keys the app does not read, and what it had at a key whose value is wrong, and writes it back, unless the
  entry now has a value there; so a field of a newer jazygit survives an older one's edit. An entry the app cannot
  read is kept as its table was, shown with what is wrong in it, and written back as it was; it can be deleted, not
  edited. The rest of the file is kept as before (ADR 111).
- **The comments inside the array are lost when the panel writes it.** The panel says so where the list is chosen,
  and that `e` edits the file instead, for whoever keeps comments there. Not: a list that stays read-only while it
  has comments, which the settings do not see, as the file is read without them.
- **A list changed in the file since it was read is not written over.** Saving reads the list in the file again,
  under the file's lock, and refuses to write it when it differs from what the session read: an entry another
  jazygit added would otherwise be lost. The panel shows the save as failed, and opening it again reads the file
  again. A setting of one value is written over, as before. Not: merging the two lists, which has no answer when
  both changed one entry.
- **A `ListSetting<E>`** declares the list for the panel: its key, its title, how an entry is named, and its
  `Form<E>`. `SettingsRows.list` is its row in a section: it names the entries, and opens their dialog.
- **The dialog of the entries** (`ListDialogs`): `a` adds one, Enter edits the highlighted one, `d` deletes it, `K`
  and `J` move it up and down, as the order of a list matters (the letters of the agents in `I` follow it). Each
  change is saved at once, through `SettingChange.set`, as any setting is. Escape goes back to the panel.
- **The form of an entry**: one line per `Field`, a `Text`, a `Toggle` or a `Letter`, with its value and what is wrong
  with it. Enter changes the highlighted field, in a text input for a text or a letter, and turns a toggle the other
  way; `s` saves the entry, and Escape drops what changed. `Form.validate` gives the `FieldProblem`s of an entry,
  given the other entries, as a name that is taken: one that blocks keeps the entry from being saved, a warning
  only shows.
- The dialogs are the kernel's, so a feature declares the list and its form, and nothing else. The settings panel
  (`features-chrome`) holds them, as it holds the panel.

## Consequences

- The editors, the diff filters and the agents can move onto `Codec.tables` one by one; until then they stay read
  only. The agents are next (ADR 163, step 6).
- An entry that was read with something wrong in it, as a shortcut an earlier entry has, keeps that value in the file
  until the user sets another in the form.
- A field's problems are checked in the form only; a hand-written entry gets the `TableCodec`'s checks on read, not
  the form's.
- `PickDialog` takes keys of its own (`Keyed`), which act on the highlighted row.
- The blank lines between the tables of a list are lost too, when it is written.

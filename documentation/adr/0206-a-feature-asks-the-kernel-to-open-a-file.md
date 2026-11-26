# 206. A feature asks the kernel to open a file, and the feature that opens files answers

Date: 2026-11-26

## Context

`features-changes` opens a file in an editor (ADR 145): in git's editor while the user set none to open files, in the
one set, or in the one chosen in a menu of them. Four modules outside it imported `changes.edit` for that alone:

- The checks of GitHub and of Forgejo open the log of a check (ADR 160, 195). Each kept a copy of the choice between
  git's editor, the one editor and the menu, built `EditCmd` and `OpenFileCmd` itself, and had a menu of its own,
  `LogEditorPopup`, which went back to the checks and was told what was read of them meanwhile.
- The resolve dialog (ADR 200, 201) called `EditUpdate` with `EditMsg.RequestedAt`, and read `FileEditor` to say
  what editing runs.
- Opening a worktree (ADR 110) read `EditorSettings.EDITORS` for the editors that open worktrees.

So the forges depended on how `changes` builds its commands, and a third place that opens a file would have made a
third copy.

## Decision

- **A kernel event, `KernelEvent.FileToOpen`**: a feature that wants a file opened raises it with `Events.happened`,
  with the path, the line if it has one, and the title of the menu if the file's name does not say what it is, as
  "Open the log of lint in". Without a line, the file opens as `e` opens it, at its first conflict when it is in
  conflict. A feature answers events (ADR 158), so the kernel names no feature, and the one that asks knows neither
  the editors nor the commands. Not a `ProgramLauncher`-like field of `Env` (ADR 183): which editor opens the file,
  and whether a menu asks, is decided by an update, from the model; and not a kernel `Msg` that the update of
  `changes` handles, as the kernel has no way to hand a message of its own to a feature but its events.
- **`features-changes` answers it**: `EditFeature` reacts with `EditUpdate.reacting`, which opens the file as `e`
  does. It is the only feature that opens files.
- **The menu goes over the popup that is open, and back to it**: `EditPopup` is a `Covering` (a new kernel interface,
  in `kernel.state.popup`), which keeps the popup under it and where the user was in it, and goes back to it once an
  editor is chosen or Escape is pressed. Only a popup that says it is `coverable()` goes under it: one whose feature
  goes on telling it, under the menu, what it learns, as the checks are told what is read of them through
  `Covering.under()` and `over()`. Over any other popup the first editor opens the file, as the checks did when
  another popup was over them, since the menu would take that popup away.
- **What the kernel's own code reads moves to the kernel**: the setting of the editors is `Settings.EDITORS`, one of
  the kernel's `SettingKeys.KERNEL`, as the kernel's `Editors` already was; `FileEditor` is in `kernel.state.edit`,
  so the resolve dialog says what opening a file runs without `changes`.
- **The forges lose their menus**: `LogEditorPopup`, `LogEditorDialog`, `ChecksMsg.LogEditorChosen` and
  `LogEditorCancelled` are gone from both.

## Consequences

- No module but `features-changes` imports `changes.edit`. The forges, the resolve dialog and the worktrees open a
  file, or read the editors, through the kernel.
- A new place that opens a file raises one event, and gets git's editor, the one set or the menu, as `e` does.
- `KernelEvent` has one more record, which every switch over it lists.
- A popup that reads by itself and wants the menu over it, rather than the first editor, says it is `coverable()` and
  looks for itself under a `Covering`, as the checks do. The prompt of ssh (ADR 150) keeps the popup under it its own
  way; it is not a `Covering`, as nothing looks for a popup under it yet.

# 111. The settings file opened from the settings

Date: 2026-11-11

## Context

Some settings are only in the settings file, `config.toml`: the editors a worktree opens in (ADR 110) are set
nowhere else. To set them, a user has to know where the file is, which differs per OS and with `$XDG_CONFIG_HOME` or
`$JAZYGIT_CONFIG_DIR` (`SettingsPaths`), and open it by hand.

The file is read once, when jazygit starts (`Launcher`). Saving writes only the settings the user changed in the app,
so what is edited by hand meanwhile stays (`SettingsStore.save`); but a hand edit takes effect only in the next session.
The browsers are the exception: they are read from the file each time a link opens (ADR 88).

jazygit starts two kinds of editors already:

- git's editor, which `e` on the files opens files in (`EditCmd`), with the terminal handed over: the one
  `git var GIT_EDITOR` names, as the user's environment has it: `$GIT_EDITOR`, `core.editor`, `$VISUAL`, `$EDITOR`,
  else `vi`. Every user has one, without setting anything for jazygit.
- the editors the user lists in the settings file, which a worktree opens in (ADR 110). None is set until the user
  sets one, which is what the settings file has to be opened for.

## Decision

### The key

The settings panel gets a heading *Settings file*, last, with one line: the path of the file, with `~` for the home
directory. `e` on it opens the file, as `e` opens a file on the files. It is never disabled: a file that is not there
yet is made (below).

### The editor

The file opens in git's editor, with the terminal handed over to it, as `EditCmd` does; jazygit waits until it is
closed. So it works without anything set, by `$EDITOR` alone, or `vi` without that. A user whose git's editor is a GUI
editor has set it to wait already, as git needs it for commit messages: `code --wait`.

- Not: the editors of ADR 110. They are set to open a directory: `{dir}` is the worktree, and some of their commands
  only take a directory, as `tmux new-window -c {dir}` does. Reading `{dir}` as a file there breaks those; adding a
  `{file}` leaves `["nvim", "{dir}"]` opening the directory. A GUI editor among them does not wait either, so jazygit
  would not know when the file is saved.
- Not: a menu. With one editor, `e` opens it at once.

### A file that is not there

When there is no settings file yet, which is so until a setting is first saved, `e` first makes it, and the
directories it is in, empty. Then the editor opens a file that is there, and saves it where jazygit reads it.

- Not: a file with the settings, or the keys, as comments. Saving loses comments after the last value (`SettingsFile`),
  so such a file would lose most of them at the first save. Writing every setting as it is would fix the defaults
  of today in it.
- Making it fails as saving does: `SettingsProblem.Unwritable`, and the editor does not open.

### Reading it again

The file is read again, and the settings applied, at two moments:

- when the editor is closed;
- when the settings panel opens, for what was edited outside jazygit meanwhile.

#### What is applied

The settings in use are not always what the file has: a setting whose save failed still holds for the session
(`SettingsUpdate`), and the file may have a value that is not valid. So a read applies what changed in the file, not
all of it. jazygit keeps what the file had when it last read or wrote it; a setting takes the value the file has now
when that value is valid and differs from what the file had then. Every other setting keeps the value in use:

- a setting only the session has stays, unless the user edited that same key;
- a key whose value is not valid, such as `theme = "drakula"`, keeps the value in use, rather than the default the
  start falls back to; it is reported (below);
- a file that cannot be read, or is not TOML, keeps all the settings in use.

What can be applied is all the file holds: the theme, the animations, the gh executable, the sizes of the panes, the
look of the diff, the browsers and the editors. A gh executable that changed reopens the repository, as choosing one in
the panel does. A theme that changed replaces the theme `--theme` gave the session, as choosing one in the panel does;
while the theme in the file stays as it was, `--theme` holds.

- Not: applying all the file holds, as the start does. A read when the panel opens would undo, without a word, a
  setting whose save failed; and a typo would reset a setting the user had.
- Not: reading only when the file's modification time changed. It keeps a setting only the session has, but a typo
  still resets one.

#### When it runs

The read runs on the `io` executor, as `SaveSettings` does, which runs one command at a time: so a save asked for
before it is written before it is read, and a read does not overtake it to apply the file as it was.

#### What is reported

What is wrong in the file is reported as at the start (`SettingsProblem`):

- after the editor, always: the user just edited it, and wants to know;
- when the settings panel opens, only when it differs from what was reported last, at the start or since; the same
  problem is not reported at each opening until it is fixed.

After the editor, the settings panel shows again, with the settings as applied, so the user sees what changed.

- Not: watching the file. jazygit watches no file yet; with git's editor it knows when the edit is done.
- Not: a restart to apply them.

### The command log

Opening is in the command log as *Edit settings*, with the command that ran under it, as `e` on the files is. An
editor that cannot start, or fails, is reported as there.

## Consequences

- Every setting, also those only in the file, can be set without leaving jazygit or knowing where the file is.
- A hand edit applies when the editor closes, or the next time the settings panel opens; not before.
- The settings are read at more moments than the start. `SettingsFile` reads them as at the start, but tells which
  keys are not valid apart from their defaults; what applies them is new, and takes in a change of the gh executable
  and of the theme over `--theme`.
- The model keeps what the file had at the last read or save, besides the settings in use, and the problem reported
  last.
- A setting whose save failed, or a typo, never undoes a setting in use; a hand edit of a key always wins over what
  the session has for it.
- The editors of ADR 110 still open worktrees only.

# 110. A worktree opened in an editor

Date: 2026-11-11

## Context

Opening a worktree in an editor is one of the gaps against lazygit that ADR 10 left. A user works on a branch in an
IDE or an editor that opens a directory, `idea`, `code`, `kate`, `nvim`, and has to start it by hand, in the
directory of the right worktree.

jazygit starts two kinds of programs already:

- `EditCmd` opens files in git's editor, with the terminal handed over to it (`TerminalHandOver`), and reads the
  repository again once it exits. It opens no directory: not every editor opens one (`EditKeys`).
- `OpenLinkCmd` starts a browser and leaves it running (`Exec.launch`, ADR 85): input closed, output thrown away, and
  taken to have started well when it is still running, or exited with 0, after a short wait.

An editor is either kind: a GUI editor runs on its own, a terminal editor needs a terminal. Which editors a user has,
and how each is started, only the user knows; and a user may have more than one.

## Decision

### The editors

The user lists the editors in the settings file, `config.toml`, in the order they are offered:

```toml
[[editors]]
name = "IntelliJ"
command = ["idea", "{dir}"]

[[editors]]
name = "Kate"
command = ["kate", "{dir}"]
shortcut = "k"

[[editors]]
name = "Neovim"
command = ["nvim", "{dir}"]
terminal = true
```

- `name`: what the menu and the command log call it.
- `command`: the program and its arguments, as a list, run without a shell. `{dir}` is the absolute path of the
  worktree, wherever it is in the list, as often as it is there. Without `{dir}`, the path is added as the last
  argument. Not: one string run by `sh -c`, which needs quoting for a path with spaces, and a shell, which Windows
  has not; a user who wants one writes `["sh", "-c", "…"]`.
- `terminal`: `true` hands the terminal over to it, as `EditCmd` does. By default, `false`, it is started and left
  running, as a browser is.
- `shortcut`: the letter that chooses it in the menu; optional.
- Every editor starts in the worktree as its working directory, so `["nvim", "."]` works too.
- Only `{dir}`: no `{branch}` or `{repo}` until a command needs one.
- The editors are the user's, for every repository: only in the settings file, not per repository.

A terminal editor in a terminal of its own is not a third kind: it is a program that starts a terminal, left running.

```toml
[[editors]]
name = "Neovim in Konsole"
command = ["konsole", "--workdir", "{dir}", "-e", "nvim", "{dir}"]

[[editors]]
name = "Neovim in tmux"
command = ["tmux", "new-window", "-c", "{dir}", "nvim", "{dir}"]
```

### What is wrong in the file

An entry without a `name`, with an empty `command`, or with a `terminal` that is not a boolean is left out, and
reported as `SettingsProblem.Invalid` under its keys, such as `editors[2].command`. The other entries stay.

A `shortcut` that is not one character, that is `/`, or that an earlier entry has already, is dropped and reported
the same way, as `editors[2].shortcut`. The entry stays, chosen as one without a shortcut is.

### The key

`e` opens a worktree in an editor:

- on the worktrees, the highlighted worktree;
- on the local branches, the worktree that has the branch: the main one, the one shown, or a linked one, also while
  it rebases or bisects the branch (see below).

As `e` on the files means opening the file in the editor. `?` lists it on both. Not: on the remote branches, the
commits or the files.

Where there is nothing to open, `e` says why instead of doing nothing (ADR 9):

- a branch no worktree has checked out: it is not checked out in a worktree. Not: offering to add one first, which
  can follow, through the popups `w` uses;
- a worktree whose directory is gone: it is gone, as `space` says;
- no editors in the settings file: none is set, and how to set one, with an example of `[[editors]]`. Not: falling
  back to git's editor, which may not open a directory.

### Which worktree has a branch

A worktree has a branch when its HEAD is the branch, or when it rebases or bisects the branch: its git directory, such
as `.git/worktrees/wt`, names it in `rebase-merge/head-name` or `rebase-apply/head-name` (`refs/heads/feat`), or in
`BISECT_START` (`feat`). That is how git decides it, when it refuses to check the branch out in another worktree:
`'feat' is already used by worktree at …`.

While a worktree rebases, its HEAD is detached: `git worktree list` says `detached`, and `%(worktreepath)`, which
`CliBranches` reads, is empty for the branch. Taken from HEAD alone, `e` on the branch would say it is not checked out,
at the moment the editor is wanted most: a rebase that went wrong.

- `Branch.worktreeLocation` follows git's rule: the branches are read with, for each worktree without a branch as its
  HEAD, the branch its rebase or bisect names, from the files `CliInProgress` reads for the worktree shown.
- So what asks it besides `e`, `checkedOutElsewhere` for `d`, `space` and `w`, knows a branch that is rebased in
  another worktree is in use there, instead of leaving git to refuse; and the branches show it in that worktree.
- A bisect started on a detached HEAD names a commit, not a branch: no branch is in that worktree.

### Choosing an editor

With one editor, `e` opens it at once, whether it has a shortcut or not.

With more, `e` opens a menu of them, by `name`, in the order of the file. Each is chosen with its shortcut; one
without gets the first that no shortcut takes of the digits `1` to `9`, then the letters `a` to `z`, in the order of
the file. The menu moves on the arrows only, so `j` and `k` are free. Every editor has a letter: 35 are more than a
user lists. Not: shortcuts as keys on the panes themselves, without `e`, where `k` moves up and most letters are taken,
differently on each pane.

The menu is a `PickDialog`, whose rows come from the popup, as the editors come from the settings; a `ChoiceDialog`
has its options fixed. A row of a `PickDialog` gets an optional letter, shown before it as `(k)`, which picks it as an
option of a `ChoiceDialog` is chosen; while the filter is open, the letters are the filter's.

### Starting it

- A GUI editor goes through `Exec.launch`, as a browser does: opening fails when the program cannot be started, or
  exits with an error within the wait. What it does after that, jazygit does not see. Not: keeping it running when
  the terminal of jazygit closes; a browser is not kept either.
- A terminal editor goes through `TerminalHandOver`, as `EditCmd` does; once it exits, the repository is read again.
- Either is in the command log as *Open in editor*, with the command that ran, `idea /repos/app`, under it; a
  failure names the worktree.
- A bare repository is not among the worktrees (`CliWorktrees` leaves it out), so there is no such worktree to open.

### Not

- The editors in the settings panel: they are set in the file only, for now.

## Consequences

- A worktree opens in any editor that takes a directory, GUI or terminal, with `e`, or `e` and a letter.
- A branch is in the worktree that rebases or bisects it, for every key that asks, not only `e`. Reading the branches
  reads a few small files in the git directory of each worktree without a branch as its HEAD.
- The settings file gets its first list of tables; `SettingsFile` reads, and reports, each entry on its own.
- Opening a worktree starts a program the user wrote down, with no confirmation, as `e` on a file does.
- Nothing works until the user lists an editor; the message says how.

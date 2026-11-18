# 145. A file opened in an editor, at a line

Date: 2026-11-18

## Context

The editors of the settings file open a worktree only (ADR 110): `command` gets the directory, as `{dir}`. A file is
opened in git's editor (`EditCmd`), on the terminal, at its start; a directory on the files is not opened, as not
every editor opens one.

Editors differ in what they open. IntelliJ and VS Code open a directory and a file; `tmux new-window -c {dir}` only a
directory; Helix or a `nvim` started on a file only a file. Each wants the line its own way: `code -g {file}:12`,
`nvim +12 {file}`, `idea --line 12 {file}`. ADR 111 left a `{file}` out of `command`: read as the file, `{dir}`
breaks the commands that only take a directory, and a `{file}` added to them leaves `["nvim", "{dir}"]` opening the
directory.

A line to open at is there in a rich diff: each stop of its cursor is a line of the old or the new file (ADR 60).

## Decision

### Two commands

An editor gets a command for each thing it opens:

```toml
[[editors]]
name = "VS Code"
dir-command = ["code", "{dir}"]
file-command = ["code", "-g", "{file}:{line}"]
shortcut = "c"

[[editors]]
name = "IntelliJ"
dir-command = ["idea", "{dir}"]

[[editors]]
name = "Neovim"
file-command = ["nvim", "+{line}", "{file}"]
terminal = true
```

- `dir-command`: what opens a worktree, as `command` did (ADR 110), `{dir}` in it as there.
- `file-command`: what opens a file. `{file}` is its absolute path, `{line}` the line, counted from 1, and `{dir}` the
  worktree it is in; each wherever in the list, as often as it is there. Without `{file}`, the path is added as the
  last argument, as the worktree is without `{dir}`. It runs in the worktree as its working directory.
- An editor opens what it has a command for: a worktree with `dir-command`, a file with `file-command`, both with
  both. One entry for an editor that opens both, so its name and shortcut are the same in both menus.
- `terminal` and `shortcut` are the entry's, for both commands. A shortcut stays unique over all entries.
- `command` is read as `dir-command` when that is not there, so a file written for ADR 110 keeps working.
  Not: guessing what an editor opens from the placeholders in one `command`, which ADR 111 found ambiguous.
- Not: `dirCommand` and `fileCommand`. The settings file has its keys in kebab case (`diff-filters`,
  `recent-repositories`).

### What is wrong in the file

An entry with neither command is left out, and reported under `editors[2].dir-command`. A command that is not a list
of words with a program first is left out the same way, under its own key; the entry goes with it, as one with a
broken `command` went (ADR 110).

### The keys

- `e` on a file in the files opens it at line 1. A directory is still not opened.
- `e` in a rich diff (`Scopes.DIFF`) opens the file of the cursor at its line:
  - a line of the new file, an added or a context line, at its number;
  - a removed line, where it was: at the next line of the new file after it, else the last one before it;
  - a marker of lines left out (ADR 77), at the first line it leaves out; a marker of filtered hunks (ADR 144), at the
    first line of its first hunk in the new file; a row of JSON or YAML rendered (ADR 122), at the line it was drawn
    from; the bar of a file, at line 1.
  - On the lead of a commit, before the first file, there is no file: `e` sends nothing, and `?` lists it disabled.
- Not: `e` in the diff of a pull request's files, where it edits a review comment (ADR 65).
- Both open the file of the working tree, also when the files or the diff are of a commit, a stash or a branch, as `e`
  on the files did. Not: the file as it was in that commit.

### Choosing an editor

- No editor with a `file-command`: the file opens in git's editor, as before, at its start. Not: saying that none is
  set, as `e` on a worktree does: git's editor always opens a file.
- One: it opens at once. More: a menu of them, `Open A.java in`, as the menu of ADR 110 is: by name, in the order of
  the file, each with its shortcut or the next free digit or letter.
- The menu of a worktree lists only the editors with a `dir-command`; with none, `e` on a worktree says how to set one,
  as before, its example now with `dir-command`.

### Starting it

As an editor opens a worktree (ADR 110): a terminal editor through `TerminalHandOver`, after which the repository is
read again; any other through `Exec.launch`, left running. It is in the command log as *Edit*, with the file, as
git's editor is.

## Consequences

- A user who lists editors as ADR 110 has them opening worktrees as before; `e` on a file still opens git's editor
  until an editor has a `file-command`.
- `e` in a rich diff goes from what is read to where it is in the editor, at the line.
- `{line}` is only as good as the working tree is close to the diff: for a commit far back, the line may have moved.

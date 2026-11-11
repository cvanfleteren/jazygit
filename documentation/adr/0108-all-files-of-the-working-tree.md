# 108. All files of the working tree

Date: 2026-11-11

## Context

The files pane lists what changed in the working tree: its staged, unstaged and untracked files. A file that did not
change is not in it, so finding a file to read, or to open in the editor (`e`) for a small change, means leaving
jazygit, though the pane has a tree and a filter (ADR 105, 106) that would find it at once.

`f` on a branch lists the files it changes against its base (ADR 61); the main branch has none, and says so. Listing
the files of a branch as committed was weighed: it shows files that may not be on disk, so `e` could not open them,
or would open the copy of another branch, and what was just edited would not show in it. The files as they are on
disk are what is read and edited.

The content panel shows the diff of the file highlighted, or a version of it read whole (ADR 102, 103). A file
without changes has no diff, and no version can be made from one.

## Decision

### The mode

- `a` in the files pane switches the tree of the working tree between its changed files, as now, and all its files.
  `?` lists it. Not: `f` on the main branch, which is not always what is checked out, nor on disk.
- All its files are those git tracks and the untracked ones it does not ignore: `git ls-files -z -t --cached --others
  --exclude-standard`, each path kept once and sorted: git lists a conflicted file once per stage, and with `-t` its
  `--deduplicate` does nothing. A submodule is one entry. Not: the ignored files, as `target/` or `node_modules/`,
  which would bury the others.
- A file tagged `S` by `-t` (skip-worktree) is left out when it is not on disk, as a sparse checkout leaves it: it
  cannot be read, and `e` would make a new file. One that is on disk, as `git update-index --skip-worktree` leaves it,
  stays and shows its content, its edits too, as git does not report them. A tracked file deleted from the disk but
  not staged stays: it is a changed file, and shows its diff.
- They are read again with a reload of the working tree whose status changed, only while the mode is on: the pane of
  changed files costs nothing more.
- The mode is kept per repository for the session, through reloads and operations. It is not kept for the next one.

### The tree

- It is the same tree with more files: a changed file keeps its marker, and staging, discarding and the other keys
  act on it as in the tree of changed files. On a file without changes they are disabled, saying why. A directory and
  the root act on the changed files under them.
- It opens with the directories of the top level open, and those leading to a changed file: no change is hidden by
  the files around it. Not: all open, as a tree of changes is, which is no use with thousands of files; `/` opens
  every directory leading to a match (ADR 105).
- The working tree keeps its one filter (ADR 105) in both modes: switching keeps `/Test`.
- The title says it, before the filter: `Files · all files`, `Files · all files · /test`.

### The content panel

- A changed file shows its diff, as now, with its versions (Ctrl+O) and Markdown rendered (Ctrl+T).
- A file without changes shows its content, read from disk when it is highlighted, and dropped when the highlight
  leaves it, as a diff is:
    - As source, lexed whole, with one column of line numbers. Ctrl+T renders it as ADR 103 renders a version;
      Ctrl+O does nothing, as there is one version only.
    - With the cursor of a version (ADR 102): `y` copies its lines, `v` a range of them.
    - Cut at the limit of ADR 79, saying so, as a version is.
    - A binary file, one with a NUL byte in its first 8000 bytes, is not shown: `Binary file · 12 KiB`. What
      `.gitattributes` says of it is not read: a file marked `binary` shows as source until it changes, and then as
      git diffs it. Not: `git check-attr` on each file highlighted, a process for little.
      A symbolic link says `Symbolic link to <target>`, a submodule `Submodule at abc1234`. Not: images drawn, which
      are for an ADR of their own (ADR 103).
- A directory, or the root, shows the diff of the changes under it, as now. With none, it says how many files it
  holds, those shown when filtered: `src/main · 214 files`.

### The editor

- `e` opens the file highlighted in the tree in git's editor, as it does now, a file without changes too. The reload
  after it shows the edit at once: the file is marked changed, and its diff shows.
- It opens the file, not the line of the cursor.

### Where it lives

- Whether all files are shown is in the Model, in `RepoView`, per repository: the update that starts a reload reads
  it to know whether to list them, and it outlives operations. Its Msg, `AllFilesMsg.Toggled`, and its update are in
  `feature.files.all`, which declares `a`, as `feature.files.filter` declares `/`.
- All the files listed are kept with the working tree's files in the Model, read with them by the reload.
- The content of a file without changes is read by a Cmd and kept with the area showing it, in `RepoView`, as an
  expansion is (`RepoView.expanded`, ADR 77): dropped once the area shows another file, read again after a reload.
- Which directories are open to start with, and the highlight, stay view state in `FileTreeList` (ADR 105).

### What it may cost

- `FileTreeList` filters the files, and builds its tree, while it renders: on the UI thread, on each key typed in the
  filter and each reload. With all files, that is every path of the repository, matched with the starts of words of
  ADR 106.
- The budget: a key typed in the filter of 100 000 files stays within a frame, 16 ms. `AllFilesBenchmark` in
  `jazygit-benchmarks` measures it, and a run of the app on a repository that large is to confirm it, as a benchmark
  alone can miss where the time goes.
- As built, it is over budget: at 100 000 files a key typed takes about 100 ms, 140 ms for a filter of the starts of
  words, mostly in matching each path; the directories to start collapsed take about 80 ms once. That is taken for
  now: the repositories of most users are far smaller. Making the match cheaper (the filter parsed once, the starts
  of words found without making lists), then applying the filter once typing pauses, or matching off the UI thread,
  is left for later. Not: a limit on the number of files, as ADR 79 has for a diff: the mode is wanted most in the
  repositories that would pass it.
- All files are listed again only when the status changed, not on each reload: a file is added, deleted, renamed or
  committed only with a change to the status.

## Consequences

- A second command lists the files of the working tree, while the mode is on, when the status changed: in a
  repository of 100 000 files that is a few megabytes and tens of milliseconds more, and a tree of all of them rebuilt.
- The content panel reads a file from disk for the first time, apart from a diff; a file without changes has a way of
  its own to be shown.
- `FileTreeList` gets a second shape of the working tree, and the open directories to start from with it.
- `f` on the main branch still says it has no base. Left for later: the files of a branch as committed, read-only,
  for a branch not checked out; `e` at the line of the cursor, which needs the way each editor takes a line; a file
  compared with how it was before the last commit that changed it.

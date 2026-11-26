# 149. Enter locks the files to a list

Date: 2026-11-19

## Context

Since ADR 141 the files pane lists the files of what is highlighted in the list that has the focus, whichever list
that is, at every frame. Enter on a commit moves the focus into its diff (ADR 16, 23); the files pane then lists the
files of that commit. Tab goes round files, branches, commits and stash, TamboUI's own order: from the diff, the
files are reached through the stash, and one Tab more gives the focus to the branches, whose highlighted branch the
files pane then lists in their place. What Enter opened is gone after two keys, without the user asking for it.

`F` does not help: the files it lists are replaced in the same way, as soon as another list has the focus. On the
commits, the stash, the commits of a branch and the divergence it lists nothing that following does not, but moves the
focus into them, which Enter does not.

## Decision

### The lock

- Enter on a commit of the commits pane, of the commits of a branch, of the divergence, or on a stash, **locks** the
  files pane to that list, and moves the focus into its tree, at its first file. Not: into the diff of the whole
  commit, which → from the list still shows; the tree is the more useful of the two to land in.
- `F` on a local branch locks the files pane to the branches, and lists what it lists now (ADR 61, 128): the files of
  its pull request with their review threads, or all files of the tip of the main branch.
- While locked, the files pane follows the highlight of that list only, whichever pane has the focus: Tab, the arrows
  or a click on another list move the focus, not what the files pane lists. A row clicked in another list is only
  highlighted. Not: locking to the commit Enter was pressed on, which would show the files of one commit beside the
  diff of the next once the highlight moved in the list.
- The content panel shows what it shows now for the pane with the focus: the log of the highlighted branch while the
  branches have it, also when the files are locked to the commits.
- Enter, or `F` on a branch, in another list moves the lock to that list. There is one lock at most.
- Moving the highlight in the branches while they hold the lock lists what following lists (ADR 141): the files of the
  branch against its base, read locally. The pull request of a branch is read only by `F` on it. Not: reading the pull
  request of each branch highlighted, which costs the network at each move.
- A row with nothing to list, a stack, or the branch checked out, which lists the working tree, keeps the lock: the
  lock is the list's, not the row's.
- Above 5 000 files, Enter builds the tree whatever its size, as `F` did; following still only says how many there are
  (ADR 141).

### `F`

- `F` is no longer bound in the commits, the stash, the commits of a branch, nor the divergence: Enter does what it
  did there. The key hints show Enter as `files` in those lists.
- `F` on a local branch stays, as above: Enter on a branch lists its commits (ADR 23).

### Letting go

- `Esc` in the files tree of a lock moves the focus back to the locked list, as now (ADR 141), and lets go of the lock:
  the files pane follows the list with the focus again. `Esc` in the diff of a file still goes back to the tree.
- `Esc` in the locked list lets go of the lock too, where `Esc` means nothing else there. In the commits of a branch
  and the divergence `Esc` closes the list, as now (ADR 20, 23).
- A locked list that closes, the commits of a branch or the divergence, by `Esc`, `[`, `]`, or its branch going away,
  takes the lock with it.

### Showing it

- The title of the files pane names the list it is locked to: `Files of abc1234 · from Commits`. Not: a mark in the
  locked list, until the title turns out not to be enough.

## Consequences

- What Enter opens stays in the files pane until `Esc`, through any number of Tabs; looking at the branches or the
  stash on the way no longer loses it.
- Without Enter, the files pane follows the focus as ADR 141 set out.
- The whole diff of a commit is → from the list, no longer Enter.
- `F` is free in the commits, the stash, the commits of a branch and the divergence.
- The lock is held in the model, beside the files of a commit it lists (`FilesView.commitFiles`), so following asks the
  locked list for its highlight, not the list with the focus.

Amended by ADR 207: the update of `SelectionMsg.FilesUnlocked` asks the app to move the focus back to the list,
with `ViewIntent.ToPane`, only from the files pane.

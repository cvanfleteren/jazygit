# 141. The files follow the highlight

Date: 2026-11-16

## Context

The files pane lists the changes of the working tree. `F` on a commit, a stash, a branch or the divergence with a base
lists the files that one changed in their place, and moves the focus into them (ADR 44, 61); `Esc` goes back. Looking
through history file by file is then `F`, a look, `Esc`, `j`, `F` again, for every commit.

The content panel already reads the whole diff of the highlighted commit, `git show --stat --patch`, at each move of
the highlight. Measured on nixpkgs (50 000 files, 910 000 commits), warm cache:

| What                                             | Its files, `--name-status` | Its diff, already read |
|--------------------------------------------------|----------------------------|------------------------|
| A commit as most are                             | 5 ms                       | 6 ms                   |
| A commit of 1 500 files                          | 40 ms                      | 1 430 ms               |
| A merge of 17 000 files, against its first parent | 126 ms                     | 698 ms                 |
| A branch against a base, 67 000 files            | 331 ms                     |                        |
| All files of a commit, `ls-tree -r` (ADR 128)    | 120 ms, 2.3 MB             |                        |

Git limits rename detection itself (`diff.renameLimit`), so `-M` does not blow up. Building the tree is linear in the
files; ADR 108 measured 100 000 paths at about 100 ms to filter and 80 ms to find the directories to start collapsed.
Reading what a commit changed costs a fraction of the diff read for it already; its tree costs little but for the
largest commits. A branch has no diff read for it, the content panel shows its log: the files against its base are a
new read at each move, small but for a branch far from its base. All files of a commit are not too much for git to
list, 120 ms, less than that branch; what costs is 2.3 MB at each move, and a tree of 50 000 paths built and its
directories collapsed, about 90 ms more by ADR 108's measure.

## Decision

### What follows

- While a list of commits has the focus, the files pane lists the files of the highlighted one, without a key: the
  commits, the commits of a branch, the divergence with a base, and the stashes. They change as the highlight moves.
- While the branches have the focus, it lists the files of the highlighted branch against its base (ADR 61), read
  locally, whether it has a pull request or not. A branch whose base is not known says so in the pane, in place of the
  tree; no error is shown.
- The main branch lists the files its tip commit changed. Not: all its files (ADR 128), which would be an `ls-tree` at
  each move.
- What is checked out here, a branch or a detached HEAD (ADR 137), lists the files of the working tree, its changes
  staged or not, as the files pane does now. Not: the files of its last commit, or what it changed against its base:
  what the user works on is what is not committed yet. It is also the way back to the working tree from the lists.
- The other rows of the branches: a stack of pull requests,
  and a pull request of a stack whose branch is not here, say there is nothing to list, in place of the tree, as a
  branch without a base does. Not: the files of the branch highlighted before, which would show one while another is
  highlighted (ADR 44).
- The remote branches do not follow: they list the files of the working tree, as now. `F` on a remote branch is still
  left for later (ADR 61, 128).
- The focus stays in the list. The arrows move it into the files, as into any pane.

### `F`

- `F` moves the focus into the files, at what `F` lists today: a branch with an open pull request lists those of the
  pull request, with its review threads (ADR 55); the main branch lists all files of its tip (ADR 128). Its pull
  request is not read while the highlight moves: that may fetch its commits, and reads its threads from GitHub.

### Going back

- The files of the working tree come back when the branch checked out is highlighted, or when the focus moves to a
  pane that is none of the lists above, not the files pane and not the content panel: the remote branches, the
  worktrees. From a list into the files
  pane, and from there into the content panel, they stay (ADR 44); back into the list, they follow again.
- `Esc` in the files pane moves the focus back to the list they were listed from, where they keep following: it is
  "back", as `Esc` in the content panel is back to the tree. Not: `Esc` listing the working tree in the files pane,
  which the files of the list would replace again as soon as it had the focus back. The working tree is the branch
  checked out.
- While they are not listed, the changes of the working tree are not counted anywhere in the pane. Not: a count in the
  title.

### From one to the next

- Every move of the highlight reads the files of the one highlighted. Not debounced: a read not started yet is
  cancelled by the next one, which takes its slot (ADR 101), and what is read for one no longer highlighted is
  dropped. One is added if holding `j` turns out to lag.
- What is shown is debounced instead: the files listed before stay listed while the next are read, for up to 200 ms,
  as the content panel keeps the diff shown before (`Preview`). Most are read by then, a local branch in a few
  milliseconds, so the pane does not flash `Loading…` at each move. Past that, the pane says they are read: a wake at
  that moment draws it (ADR 124). Not: keeping them until the next are read, however long, which would show the files
  of one while another is highlighted, with nothing to say so.
- The filter (ADR 105) stays from one to the next. All files (`a`, ADR 128) goes back to the files changed: it would
  be an `ls-tree` at each move. The directories start collapsed anew for each.
- The files are read whether the pane is shown or not.
- The title is as `F` gives it now: `Files of abc1234`, `Files of feature · vs origin/main`, `Files of main · abc1234`.

### A large commit

- Above 5 000 files changed, the files are read but not built into a tree: the pane says `17 018 files changed · Enter
  to list them`. Enter there, or `F`, builds it.
- The limit is for following only: `F`, Enter on that line and `a` build the tree whatever its size, as now. Not a
  setting, until one is wanted.

### The content panel

- While a list has the focus, it shows what it shows now: the diff of the highlighted commit or what the branch shows.
  In the files pane, the diff of the file highlighted, as after `F` now.

## Consequences

- History is read file by file with the highlight and the arrows; `F` goes from a key to see the files to one to see
  more of them.
- A git read more at each move of the highlight in those lists: smaller than the diff already read for a commit, and
  a read of its own for a branch, which costs only for one far from its base.
- The changes of the working tree are not in sight while history is looked through.
- The files of a pull request and all files of the main branch stay behind `F`: they cost the network, or a tree of
  the whole repository.

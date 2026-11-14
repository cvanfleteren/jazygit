# 128. All files of a commit

Date: 2026-11-14

## Context

The files of a commit, a stash, a branch or a pull request (ADR 44, 55, 61) list what it changed: a commit against its
first parent, a branch or a pull request against its base. Seeing a file as it is in that commit, not only its diff,
means checking the commit out, or `git show <sha>:<path>`.

`F` on a branch lists the files it changes against its base (ADR 61). The main branch has none, and says so: `Branch
main is the base branch itself`, which tells the user what git sees, not what to do instead. Looking at the main
branch as it is, while working on another one, is what `F` on it is wanted for.

ADR 108 lists all files of the working tree, with `a` in the files pane. It weighed `F` on the main branch listing
them, and rejected it: the main branch is not always what is checked out, nor on disk, so the files of another branch
would show as its own. It left for later "the files of a branch as committed, read-only, for a branch not checked
out".

A commit holds all its files, whether it changed them or not: `git ls-tree` lists them, `git cat-file` reads one, from
the object database, whatever is checked out and in whichever worktree.

## Decision

### The mode

- `a` in the files of a commit, a stash, a branch or a pull request switches their tree between the files changed, as
  now, and all files of the commit they end at: the commit itself, the stash, the tip of the branch, the head of the
  pull request. `?` lists it. It is the mode `a` switches in the files pane (ADR 108), for a commit.
- All its files are those of its tree, `git ls-tree -r -z --full-tree <sha>`, and the changed ones that are not in it:
  a file the commit deleted, the untracked files a stash keeps. A submodule is one entry; a symbolic link is one file.
- They are read once, when the mode is first switched on for the view, in a Cmd: a commit does not change. A pull
  request read again at another head (ADR 64) keeps the mode, as it keeps its filter (ADR 105), and its files are
  listed again at that head.
- The mode is the view's. The files of a commit open listing what it changed, which is what they are opened for; `F`
  on the main branch opens them listing all (below). Not: kept from one view to the next, as the working tree keeps
  its mode for the session: there, all files is where the user works; here, it is a look at a commit.
- With the review threads of a pull request listed only (`c`, ADR 57), all files list those with threads too: a
  thread is on a changed file. `a` and `c` do not change each other.
- In the diff of the files of a pull request, `a` stays the review (ADR 100): the mode is a key of the tree, as `/`
  is.

### The tree

- It is the same tree with more files: a changed file keeps its marker, the others have none. It opens as ADR 108's
  does: the directories of the top level, and those leading to a changed file.
- The filter (ADR 105) is the view's, in both modes: switching keeps `/Test`.
- The title says it, before the filter: `Files of abc1234 · all files`, `Files of feature · vs origin/main · all files
  · /test`.
- Nothing in it is staged, discarded or edited, as now: it is a commit, not the working tree. `e` is not added. Not:
  `e` opening the copy of the file on disk, which may be of another branch, or edited since.

### The content panel

- A changed file shows its diff, as now, with its versions (Ctrl+O, ADR 102).
- A file the commit did not change shows its content at that commit, read when it is highlighted and dropped when the
  highlight leaves it, as ADR 108 shows a file of the working tree without changes: lexed whole with one column of line
  numbers, Ctrl+T rendering it (ADR 103, 122, 123), the cursor of a version, the limit of ADR 79, a binary file as
  `Binary file · 12 KiB`. `git ls-tree <sha> -- <path>` gives its mode and its object, and `git cat-file` reads that:
  the blob of a file, up to the limit, and the target of a symbolic link (`Symbolic link to <target>`); a submodule is
  its object (`Submodule at abc1234`), and nothing is read.
- A directory shows the diff of the changes under it, as now; with none, how many files it holds, as ADR 108 says it.

### `F` on the main branch

- `F` on the main branch lists all files of its tip, at the commit the branches read last have it at (ADR 61): the
  main branch as committed, whatever is checked out, here or in another worktree. Its title is `Files of main · all
  files`; `a` lists the files its tip commit changed. It stays at that commit, as the files of a branch do; `F` again
  reads them anew.
- It is the same whether the main branch is checked out or not. Not: the working tree when it is checked out, which is
  the files of the working tree with `a` (ADR 108), and would make `F` show uncommitted edits on one branch only.
- The error of the main branch having no base goes from `F`. Without a main branch known, `F` on a branch still says
  there is none to compare it with (ADR 61).
- The divergence with the base (`u`, then base) still has nothing to show for the main branch. Its error says so in
  the user's terms: `main is the main branch: there is no base to compare it with`.

### Where it lives

- Whether all files are listed, and those of the tree once read, as a `Loadable`, are in `CommitFilesView`, with its
  filter. The Msg, `CommitFilesMsg.AllToggled`, and `a` are in `feature.diff`, with the other keys of the files of a
  commit (`CommitFilesAction`).
- The files of the main branch are a `Changes` of their own, `OfTip(branch, sha)`: what its tip commit changed, titled
  with the branch. Not: `OfCommit` of its tip, whose title is a sha, and which `F` again could not read anew at the new
  tip.
- Listing a tree and reading a blob are new reads of `jazygit-git`, next to `Diffs.commitFiles`. The node without
  changes highlighted, and the content read for it, are kept in `CommitFilesView`, as ADR 108 keeps those of the
  working tree in `RepoView`. All files are read with those changed, and merged, in the Cmd that reads them: merging
  100 000 paths on the UI thread, or on each frame, would cost what ADR 108 measured.

### What it may cost

- Listing the tree of a commit of 100 000 files is a few megabytes and tens of milliseconds, once per view. Filtering
  and building its tree is ADR 108's cost, on the UI thread, with its budget and its debt.

## Consequences

- The files of a commit get a second list, as the working tree did, and a file in them may have no diff.
- The content panel reads a file at a commit, not only from disk; the way ADR 108 shows a file without changes is
  shared by both.
- `F` works on every local branch; on the main branch it shows the branch rather than what it changed.
- `a` means all files in both trees of files: the working tree, and the files of a commit.
- Left for later: `F` on a remote branch (ADR 61 left it out), which `a` would then show as committed too.

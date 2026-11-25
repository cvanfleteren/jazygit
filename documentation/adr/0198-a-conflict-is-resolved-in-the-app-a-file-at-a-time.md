# 198. A conflict is resolved in the app, a file at a time

Date: 2026-11-25

## Context

A merge, rebase, cherry-pick or revert that stops on conflicts is shown (ADR 197), and `M` continues, skips or aborts
it, but the conflicts themselves were resolved outside the app: `git checkout --ours`, an editor, `git add`, `git rm`.
Many conflicts are settled by taking one side of a whole file: a lockfile, a generated file, a file the other side
deleted. And nothing stopped a file with its conflict markers still in it from being staged, which marks it resolved,
and then committed by continuing.

The files pane said `UU` or `DU` of a conflicted file, and the header how many were left, but not how many were
resolved already: once a file is staged, git lists it as any other.

Git keeps what a resolved file was in conflict with, in the index's resolve-undo extension: `git checkout -m` puts
the conflict back from it, and `git ls-files --resolve-undo` lists the files that have it. It is written when a
conflicted file is staged or removed, and dropped when a rebase, cherry-pick or revert moves on to its next commit, but
not when a merge is committed.

## Decision

1. **A new group of features, `features-midway`**, for an operation the repository is in the middle of: what to do
   with it (the `M` dialog), resolving its conflicts, and later its plan. It uses `features-changes`, as
   `features-branches` does. Not: in `features-changes`, which holds every feature of the working tree already.
   - **`changes.inprogress` moves to it**, as `midway.inprogress`, unchanged but for its package and its texts, which
     start with `midway.` now. Nothing outside the app named it.
2. **A conflicted file is resolved by taking one side of it**, on its row in the files pane:
   - `o` keeps *ours*, the side of HEAD: the branch checked out, or, in a rebase, what it rebases onto with the commits
     rebased so far. `t` keeps *theirs*: the merged branch, or the commit being rebased, cherry-picked or reverted. The
     help and the dialog name the sides by what they are, as `InProgressText` does, never as ours and theirs: in a
     rebase git's *ours* is the other branch, which reads backwards.
   - A side that kept the file is checked out and staged (`git checkout --ours|--theirs -- <path>`, then
     `git add`); a side that deleted it removes it (`git rm`), so `o` and `t` mean the same on a file one side
     deleted, and the dialog says which deletes it.
   - `r` opens a dialog of the choices for the file, each with what it does: keep ours, keep theirs, and open it in the
     editor (`e`, ADR 145). Not `enter`, which opens the diff of a file in the files pane.
   - Only on a single file in conflict. A directory, or a file that is not in conflict, does nothing: a whole directory
     on one side is a choice to make file by file.
   - The highlight stays on the file once it is resolved. Not yet: moving it to the next file in conflict, which
     the files pane would have to be told from an update, as only the app moves its highlight now (ADR 117).
3. **`u` brings a conflict back**, on a file resolved since the operation stopped (`git checkout -m -- <path>`). It is
   how a wrong pick is undone, and works however the file was resolved: in the app, in an editor, by `git add`.
4. **Staging a file with conflict markers asks first.** Staging owns `space` (ADR 69): before it stages a file in
   conflict, `changes.stage` reads it for lines that start with `<<<<<<< `, `=======` or `>>>>>>> `, as git writes
   them, and stages it only when there are none. Otherwise it asks, with a popup of its own, `MarkersLeftPopup`, whose
   dialog `features-midway` draws: edit it, keep ours, keep theirs, or stage it anyway, for a file where such lines
   belong. Not: staging refusing outright, as a fixture may hold them; nor staging asking only whether to stage
   anyway, which leaves the user to find what else to do.
   - Only the files in conflict are read, and only when some are selected. Staging a directory with files in conflict
     reads those of them.
5. **What is resolved is read from git, not remembered.** `RepoStatus` gets the files that are resolved, read with
   `git ls-files --resolve-undo` while an operation is in progress, and the files in conflict are those still
   unmerged. So the count survives restarting the app, and a file resolved outside it counts too. Not: remembering
   the conflicts when the stop is first seen, which a restart, or a stop the app never saw, would lose.
   - The title of the files pane says how far it is: `Files · 2 of 4 resolved`. It is the kernel's files pane that
     says it, from the status, as it lists the conflicts already; no feature fills it.
   - A resolved file is marked on its row in the files pane, with a `✓` after its path.
6. **Continuing with markers left is warned of.** The `M` dialog's continue says which staged files still have
   conflict markers, read when the dialog opens, and continuing then commits them.

## Consequences

- A conflict is resolved without leaving the app when one side of the whole file is right. Merging the two inside a
  file is still done in an editor (`e`), or, later, in the app, block by block.
- `features-midway` is one more module in the build, and the text of `M` moved bundle.
- Reading the resolved files runs one more git process with each status, only while an operation is in progress.
- After a merge is committed, git keeps the resolve-undo of its files until the index is next rewritten; it is only
  read while an operation is in progress, so it shows nothing then.

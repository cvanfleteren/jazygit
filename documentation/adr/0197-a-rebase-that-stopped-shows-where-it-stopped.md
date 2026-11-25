# 197. A rebase that stopped shows where it stopped, in every worktree

Date: 2026-11-25

## Context

A branch was rebased onto `main` in the linked worktree that has it checked out, and the rebase stopped on a conflict.
Git detaches HEAD at the tip of `main` while it rebases, and the app showed little else:

- The header named the rebase (`REBASING feature 1/2 onto main · 1 conflict`), but it is one line in the narrow left
  column, and was cut off.
- The branches pane listed no branch as checked out: the one rebased is not, and the detached HEAD is left out while
  a rebase goes on. The commits pane, the log of HEAD, said `detached at <sha> · 0 ahead of main`. All of it read as
  `main` being checked out.
- Checking out the branch that is being rebased asked where (ADR 68), and *here* failed with "Checkout of feature
  failed: a rebase is in progress", as if the branch were somewhere else.
- From any other worktree, the worktrees list showed the one that stopped as `(detached at <sha>)`, and the branch as
  checked out in a worktree (`wt`), as any other. Nothing said that work had stopped there, waiting.

Git keeps what a worktree is in the middle of in files of its git directory (ADR 110 reads the branch a rebase or a
bisect works on from them), so it can be read for every worktree without running git.

## Decision

1. **The branch a rebase works on is shown as the one checked out here.** In the worktree that rebases it, the
   branch is listed first, as the checked out branch is (`BranchesInUse.busyIn` orders it in `CliBranches`), drawn as
   it, with what the rebase is at after it: `REBASING 1/2 onto main · 1 conflict`. A branch that is bisected is
   listed first too; a bisect is not read as in progress, so it shows nothing more. Highlighted, it lists the files of
   the working tree, with their conflicts, as the checked out branch does (ADR 141), not what the branch changed
   against its base. The log the commits pane shows is titled after the branch: `rebasing feature`, not the detached
   HEAD.
2. **Checking it out says why not.** Space on the branch rebased here fails right away with "feature is being
   rebased here; continue or abort the rebase first (M)", in the main worktree as in a linked one, rather than
   asking where or failing as a checkout.
3. **Every worktree is read for what it is in the middle of.** `Worktree` gets a `Midway`: the operation in progress,
   read from the files of its git directory as `CliInProgress` reads them for the worktree shown, and how many files
   still have conflicts, read with `git ls-files --unmerged` only for a worktree with an operation in progress. So a
   listing of the worktrees runs one more git process per worktree that stopped, and none for the others.
4. **The lists say it.**
   - The worktrees list shows the operation of a worktree in place of `(detached at <sha>)`, the one shown too:
     `wt REBASING feature 1/2 onto main · 1 conflict`, styled as in the header.
   - A branch whose worktree is in the middle of something, here or in another worktree, shows it on a third line
     of its own, under the second, in the style of the header: `REBASING 1/2 onto main · 1 conflict`. After the
     badges on line 2 it was cut first, in a column of usual width; on a line of its own it fits. That a branch takes
     two lines always (ADR 45) gives way for it: the rows below move down when a worktree stops, and back up once it
     is done. That happens when the repository changes, not when the highlight moves or the column is resized,
     which is what ADR 45 kept the rhythm for, and to few branches at a time. The `wt` mark on line 2 still says
     which worktree it is in, when it is another.
   - Asking to switch to the worktree a branch is in, when checking it out (ADR 68), says what that worktree is in
     the middle of.
5. **The texts are the header's.** What an operation is called, and its conflicts, is said in one place
   (`InProgressText`), that the header, the lists and the dialog use. A row about one branch leaves out its name.

## Consequences

- A worktree left stopped on conflicts, by an agent say, shows in the worktrees list and on its branch from any other
  worktree, as soon as the worktrees are read again.
- Continuing, aborting or skipping still happens in the worktree that stopped: switch to it, then `M`. Resolving
  conflicts itself is not changed here.
- The header still cuts off a long line in a narrow column; the lists now say the same, in more room.
- Conflicts left without an operation, as after a stash that did not apply cleanly, are shown for the worktree shown
  only, as before: another worktree is only counted when it is in the middle of something.

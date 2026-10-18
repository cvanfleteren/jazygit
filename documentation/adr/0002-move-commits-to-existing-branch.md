# 2. Move commits to an existing branch

Date: 2026-10-18

Extends [1. Move commits to new branch](0001-move-commits-to-new-branch.md).

## Context

Besides "I meant to do this on a new branch", there is "I meant to do this on that other branch". The command
from ADR 1 already copies commits onto another branch when the new branch starts elsewhere; adding them to an
existing branch is that same path, with the branch's own tip as the starting point.

## Decision

The command becomes **Move commits to branch** (still `N` in the commits panel). Everything in ADR 1 stays as it
is, except for the first dialog.

### Choosing where the commits go

After the commits are read, a pick list (with `/` to filter) asks where they go, instead of the name field:

- **New branch…** first, which opens the name dialog and then the base dialog of ADR 1, unchanged
- then the local branches, most recent first, as the branches pane lists them

A single text field, where an existing name would mean "add to that branch", was rejected: a typo in the name of
an existing branch would silently create a new one.

Left out of, or refused in, the list:

- the checked-out branch itself is left out
- a branch checked out in another worktree is listed, marked `(worktree)` as in the branches pane, and picking it
  fails with the existing `CheckedOutElsewhere` error, so it is clear why it can't be used
- when the commits hold a merge, they can't be cherry-picked, so only a new branch at the parent is possible: the
  list is skipped and the name dialog opens straight away

### Git steps for an existing branch

There is no base step: the commits always go on top of the target's tip.

1. `git checkout <target>`
2. `git cherry-pick <sha>^..<old>`
3. only when the cherry-pick succeeded: `git branch -f <old> <sha>^`

When the checkout is refused because uncommitted changes would be overwritten, the user is offered to take them
along, as checking out a branch does: confirming runs the same steps, with the checkout done as stash, check out
and pop (`checkoutWithStash`).

### Unchanged from ADR 1

- the pushed-commits confirm and the force-push of the old branch, as they are about the old branch
- a target with an upstream needs nothing special: the commits are simply new, unpushed ones there
- a cherry-pick that stops on conflicts is left in progress, with the notice that the old branch still has the
  commits; one that fails otherwise is aborted
- the target branch is checked out afterwards

## Consequences

- One command covers both "new branch" and "existing branch"; the new-branch flow only gains one extra step (picking
  **New branch…**), and none when the commits hold a merge.
- Moving to an existing branch always rewrites nothing on the target, but always copies the commits, so it can stop
  on conflicts.

# 1. Move commits to branch

Date: 2026-10-18

## Context

Two everyday mistakes: "oops, I meant to do this work on a new branch", and "oops, I meant to do this on that other
branch". In both, commits were made on the checked-out branch that belong elsewhere: they have to end up on the
right branch, and be taken off the one they were made on.

## Decision

A command **Move commits to branch**, bound to `N` in the commits panel, next to `n` (new branch at commit).

### What moves

The selected commit and every commit above it, up to HEAD of the checked-out branch. Selecting HEAD moves just
that one commit. Moving a single commit out of the middle of the history is not supported; that would be drop +
cherry-pick, a possible separate command later.

### When it's offered

- There must be a checked-out branch; on a detached HEAD the "no branch checked out" error is shown.
- The selected commit must be on that branch and have a parent, where the branch is moved back to: the first
  commit can't be moved.

Before asking anything, the app reads the range (`git rev-list`): how many commits move, how many of them are merges,
and how many are on the old branch's upstream already.

### Choosing where the commits go

A pick list (with `/` to filter) asks where the commits go:

- **New branch…** first
- then the local branches, most recent first, as the branches pane lists them; the checked-out branch is left out

A branch checked out in another worktree is listed, marked `(worktree)` as in the branches pane, and picking it fails
with the existing `CheckedOutElsewhere` error, so it is clear why it can't be used.

**New branch…** asks for its name (starts empty; a blank name or one that exists keeps the dialog open), then where
it starts, from a fixed list (no free text):

- **parent of `<shortsha>`**, where the commits are now (the first option)
- **`origin/<default>`**, when a default branch is known (`origin/HEAD`)
- **the old branch's upstream**, when it has one and it differs from the default

When the range holds a merge, it can't be cherry-picked, so only a new branch at the parent is possible: the pick
list and the base list are skipped, and the name dialog opens straight away.

### Uncommitted changes

A move that copies commits (onto an existing branch, or a new branch on another base) is refused up front while the
working tree has changes: "Commit or stash your changes before copying the commits onto `<branch>`". git refuses to
cherry-pick onto staged changes, whatever files they are in, and onto changes to the files the picked commits touch;
by then the checkout would already have happened. Untracked files don't count, as git only stops for those that are
in the way, the same rule as checking out a pull request.

A new branch at the parent copies nothing, so it takes uncommitted changes along.

### Already-pushed commits

When some of the moved commits are already on the old branch's upstream, a pull would bring them back, so a confirm
step comes first: "N commits are already on `origin/<old>`", offering:

- **Move, then force-push `<old>`**, saying so explicitly when `<old>` is the main branch
- **Move only**; the branch is left diverged, to force-push later with the normal push
- **Cancel**

The force-push reuses the existing `PushMode.FORCE_WITH_LEASE` (a bare `--force-with-lease`, leasing against the
local remote-tracking ref), the same as the normal force-push. It runs after the move succeeded.

### Git steps

New branch at the parent (nothing is copied, so nothing can conflict):

1. `git checkout -b <name> <old>`
2. `git branch --force <old> <sha>^`

New branch on another base:

1. `git checkout -b <name> <base>`
2. `git cherry-pick --empty=drop --allow-empty <sha>^..<old>`
3. `git branch --force <old> <sha>^`

Existing branch (the commits go on top of its tip):

1. `git checkout <target>`
2. `git cherry-pick --empty=drop --allow-empty <sha>^..<old>`
3. `git branch --force <old> <sha>^`

When force-push was chosen, as the last step: `git push --force-with-lease` of `<old>` to its upstream (`Push` takes
the branch explicitly, so it doesn't need to be checked out).

Each step only runs when the one before it succeeded, so the old branch is only moved back once the commits are on
the target. Afterwards the target is checked out.

The cherry-pick flags:

- `--empty=drop` leaves out commits whose changes are already on the target, instead of failing the whole move, as
  there is nothing to copy; the most likely case is a commit that was cherry-picked there before.
- `--allow-empty` copies commits that were empty to begin with as they are, so the move keeps them.

### Failures

Conflicts are the user's to resolve; anything else is undone, as for the other operations (`Unfinished.runOrUndo`).

- A cherry-pick that stops on conflicts is left in progress for the existing in-progress UI (`M`). The old branch
  still has the commits, and the notice says so: "`<old>` still has the moved commits: once they are on `<target>`,
  reset `<old>` to `<sha>^`."
- A cherry-pick that fails otherwise is aborted, and what came before it is undone: the old branch is checked out
  again and a new branch, which got none of the commits, is deleted. The user is left where they started, with the
  error.
- A failed force-push (e.g. branch protection) leaves the move done; the user can retry with the normal push.

### Rejected alternatives

- **One text field for the target**, where an existing name would mean "add to that branch": a typo in the name of
  an existing branch would silently create a new one.
- **Taking uncommitted changes along through a stash** when they are in the way of checking out the target. Popped
  right after the checkout, the cherry-pick then refuses when they are staged or touch the same files, leaving the
  user on the target with their changes and the commits unmoved. Popped after the copy, a cherry-pick that stops on
  conflicts leaves the changes in the stash while the user resolves them, and the pop can still conflict with the
  copied commits.
- **Leaving a failed copy as it is**, without undoing the checkout: there is nothing left to resolve, as the
  cherry-pick is already aborted, so the user would only be left on the target branch, with an empty new branch to
  clean up.

## Consequences

- No commits can get lost: the old branch keeps them until they are on the target.
- The common case, a new branch at the parent, never touches the working tree and can't conflict.
- Moving to an existing branch, or a new branch elsewhere, always copies the commits, so it can stop on conflicts,
  and it needs a clean working tree.
- `--empty=drop` needs git 2.45 or newer.
- Moving commits that are already pushed rewrites history on the old branch's upstream when force-push is chosen.

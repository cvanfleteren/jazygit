# 1. Move commits to new branch

Date: 2026-10-18

## Context

The use case is "oops, I meant to do this work on a new branch": commits were made on the checked-out branch
that belong on a branch of their own.

## Decision

A command **Move commits to new branch**, bound to `N` in the commits panel (help text "Move commits to new
branch", next to `n` "new branch at commit").

### What moves

The selected commit and every commit above it, up to HEAD of the checked-out branch. Selecting HEAD moves just
that one commit. Moving a single commit out of the middle of the history is not supported (that would be drop +
cherry-pick, a possible separate command later).

### When it's offered

- There must be a checked-out branch (no detached HEAD).
- The selected commit must be an ancestor of HEAD on that branch.

### Dialog

A branch name field (starts empty, rejects names that already exist) plus a fixed list of bases (no free text):

- **parent of `<shortsha>`** (selected by default)
- **`origin/<default>`**, when a default branch is known (`origin/HEAD`)
- **the old branch's upstream**, when it has one and it differs from the default

Bases that aren't allowed are left out of the list instead of giving an error afterwards:

- no "parent" option for a root commit
- no other bases when the range contains a merge (cherry-picking merges doesn't work well); with the parent as
  base, merges are fine because nothing is rewritten

### Already-pushed commits

When the moved commits are already on the old branch's upstream, a confirm step comes first: "These commits are
already pushed to `origin/<old>`", offering:

- **Move and force-push `<old>`**, with an explicit warning when `<old>` is the default branch
- **Move only** (the branch is left diverged; force-push later with the normal push)
- **Cancel**

The force-push reuses the existing `PushMode.FORCE_WITH_LEASE` (a bare `--force-with-lease`, leasing against the
local remote-tracking ref), the same as the normal force-push.

### Git steps

Base is the parent of the selected commit (nothing can conflict, nothing to stash; uncommitted changes come
along to the new branch):

1. `git checkout -b <name>` (at HEAD)
2. `git branch -f <old> <sha>^`

Any other base:

1. `git checkout -b <name> <base>` (uncommitted changes handled the way checkout handles them today)
2. `git cherry-pick <sha>^..<old>`
3. only when the cherry-pick succeeded: `git branch -f <old> <sha>^`

When force-push was chosen, as the last step: `git push --force-with-lease` of `<old>` to its upstream (`Push`
takes the branch explicitly, so it doesn't need to be checked out).

Afterwards the new branch is checked out.

### Failures

The command stops at the first failure and leaves git in its in-progress state, with an error naming the step
that failed. There is no automatic rollback.

- A cherry-pick conflict is left in progress for the existing in-progress UI. The old branch still has the
  commits and the error says so; after resolving, the user moves it back themselves (e.g. with `g`, reset).
- A failed force-push (e.g. branch protection) leaves the move done; the user can retry with the normal push.

## Consequences

- The old branch is only moved back once the commits are referenced by the new branch, so no commits can get
  lost along the way.
- The common case (base = parent) never touches the working tree and can't conflict.
- Moving commits that are already pushed rewrites history on the old branch's upstream when force-push is chosen.

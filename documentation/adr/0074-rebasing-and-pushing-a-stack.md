# 74. Rebasing and pushing a stack

Date: 2026-11-03

## Context

When the base of a stack moves on, or a branch in it gets a commit more, every branch above has to be rebased on the
one below, and all of them pushed. `r` rebases the checked out branch on the highlighted one, a branch at a time,
with `git rebase --autostash --keep-empty --rebase-merges --no-autosquash refs/heads/<onto>`; conflicts stop it, and
`M` continues, skips or aborts it.

There are two ways to rebase a stack:

- One rebase of its top branch, with `--update-refs`: git moves every branch whose tip is in what it replays to where
  that commit lands. One rebase, one stop per conflict, and git keeps where it is between stops.
- A rebase per branch, from the bottom up, each on the new tip of the one below, as `gh-stack` does. It rebases a
  branch checked out in another worktree in that worktree, skips a merged branch and rebases the ones above it
  `--onto` past its commits, and leaves a branch in the merge queue as it is. For that it keeps a state of its own to
  go on after a conflict, which can stop in another worktree, and rolls back across worktrees.

`--update-refs` leaves out, without failing, a branch that is checked out in another worktree:

```
$ git rebase --update-refs main      # on top, with b checked out in another worktree
Successfully rebased and updated refs/heads/top.
Updated the following refs with --update-refs:
	refs/heads/a
```

`b` then stays on the old base, and `top` holds a copy of its commits.

## Decision

### One rebase of the top

"Rebase stack" from the stack menu (ADR 73), on any branch of a stack or on its header, rebases the whole stack:

```
git rebase --update-refs --autostash --keep-empty --rebase-merges --no-autosquash <base> <top>
```

- `<top>` is the top branch of the stack, which the rebase checks out. When it is done without stopping, the branch
  that was checked out before is checked out again, so the rebase does not leave one on another branch than the one
  it started from. One that stopped on a conflict and is continued with `M` ends on the top.
- `<base>` is the base of the stack as it is on its remote, `refs/remotes/origin/main`, else the local branch: pull
  requests are merged into the one on the remote, and that is what GitHub compares them with (as ADR 46 does). It
  does not fetch first; fetching stays a step of its own.
- Before it starts, the menu says which branches it rebases, on what, and asks.
- Conflicts stop it as any rebase: `M` continues, skips or aborts it, and git keeps where it is.
- Not: a rebase per branch. What it takes on, worktrees and the merge queue, is refused instead (below), which leaves
  out most of what `gh-stack` has to keep and roll back. It can come when that is missed.

### A merged bottom

When the pull request of the bottom of the stack is merged, its commits are in the base already, squashed or not, and
rebasing them again would only conflict. The rebase then starts above it:

```
git rebase --update-refs … --onto <base> <merged> <top>
```

- `<merged>` is where the merged branch was: the local branch when it is still there, else the `headRefOid` of its
  pull request on GitHub. The same goes for several merged ones at the bottom: the top one of them counts.
- The merged branch stays: deleting it is `d`, apart.

### When it refuses

The rebase does not start, and the menu says why, when a branch of the stack that it would move:

- is checked out in another worktree, which it names: `--update-refs` would leave it behind;
- is in a merge queue: rewriting it would take it out of the queue;
- is open on GitHub, but not checked out here: it cannot be moved.

### Pushing

"Push stack" pushes the branches of the stack that are not merged, in one push that GitHub takes whole or not at all:

```
git push --force-with-lease --atomic origin <branch>…
```

- A branch without an upstream gets `origin/<branch>` as its upstream, so the branches of an agent push too.
- It is in the stack menu, and offered when a rebase of the stack is done. A rebase does not push by itself, as `r`
  does not.

## Consequences

- A stack is rebased and pushed with two choices of a menu, and GitHub never sees half of it pushed.
- A stack with a branch in another worktree or in the merge queue is not rebased by jazygit.
- Rebasing a stack checks out its top for the while it runs: a conflict leaves one there, until the rebase is
  continued or aborted.
- The base is taken as it was last fetched.

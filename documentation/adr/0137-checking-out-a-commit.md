# 137. Checking out a commit

Date: 2026-11-16

## Context

jazygit checks out branches only: a local one, a remote one as a new local branch following it, or a new branch
(`Checkout`, ADR 69). To look at, build or run an older commit, of HEAD or of another branch, the user has to leave
the app and run `git checkout <sha>`.

The commits of any branch, local or remote, can be listed with Enter in the branches pane (ADR 23), but that list
acts on nothing. The commits pane lists the commits of HEAD; it can make a new branch from a commit, but not check
one out as it is.

The status panel already names a detached HEAD (`Head.Detached`), and a new worktree can already start detached
(`WorktreeSource.Detached`).

## Decision

- Space on a commit checks it out with HEAD detached, `git checkout --detach <sha>`, in the commits pane and in the
  commits of a branch (ADR 23). Not: a new branch at it, which "New branch from commit…" already makes; nor a popup
  offering both.
- It is Space as it is for a branch in the branches pane, so checking out has one key. The help and the key hints
  read `Check out commit (detached)`, so that what it leaves HEAD as is said before it is done.
- Space is the first key in the commits of a branch that changes anything: until now they could only be looked at,
  their files with `F` and only those ahead with `b`. Cherry-pick and search stay left for later.
- A commit at the tip of a local branch is checked out detached too, not as that branch: the user picked a commit,
  and Space on the branch checks out the branch.
- Nothing asks first: from a branch, detaching loses nothing. The command log lists what ran.
- Uncommitted changes that the checkout would overwrite are handled as for a branch: the popup offers to take them
  along, stashed first and popped on the commit afterwards (`branchWithStash`), with the same handling when the
  checkout or the pop fails.
- In a linked worktree, it asks where the checkout goes, as for a branch (ADR 68): here, in the main worktree, or in
  a new worktree, which starts detached at the commit.
- Space on the commit HEAD is at does nothing, whether HEAD is on a branch or already detached there: detaching onto
  the commit it is at is almost always a key pressed by mistake.
- After a checkout in the worktree shown, the commits of a branch stay open with the same commit highlighted, so that
  the next one can be checked out, and the branches tab is left as it is: Esc goes back to the local or the remote
  branches they were opened from. Not: switching to the local branches, as a checkout of a branch does. The commits
  pane lists the log of HEAD, now from the commit.
- After a checkout in the main worktree or in a new one, the app lands there as it does after checking out a branch
  there (ADR 68): the commits of the branch are not carried along, as a `Landing` holds only the tab and the focused
  pane.
- Not: a warning, nor a guard, when leaving a detached HEAD that has commits no branch holds. Checking out a commit
  loses nothing; committing on it and then leaving does, and git only warns about that. It is left for a later ADR.
- Not yet: checking out from the divergence view (ADR 20) or from the commits of a pull request (ADR 26).

## Consequences

- `CheckoutRequest` gets a request for a commit, next to `Local`, `Remote` and `NewBranch`, and `Checkout` checks out
  a commit, with and without taking the changes along.
- The commits of a branch are no longer only looked at: Space there moves HEAD.
- Commits made on a detached HEAD can be lost by checking out something else, without any warning from jazygit.

Amended by ADR 142: `b` is gone from the commits of a branch; they always list every commit.

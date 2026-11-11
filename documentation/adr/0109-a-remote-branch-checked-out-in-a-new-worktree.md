# 109. A remote branch checked out in a new worktree

Date: 2026-11-11

## Context

`w` on a local branch checks it out in a new worktree (ADR 9). On a remote branch, `origin/feat-x`, there is no such
key:

- `W` asks for the name of a new branch, `feat-x`, and runs `git worktree add -b`. When a local `feat-x` exists, git
  refuses it, also when that branch follows `origin/feat-x` and is the one wanted: `W` never takes the branch that is
  here already.
- `space` finds the local branch that follows the remote one, or makes one (`CheckoutUpdate.remoteRequested`), and
  offers a new worktree for it, but only in a linked worktree (ADR 68). In the main worktree it checks out there.

So from the main worktree, getting a branch someone pushed into a worktree of its own takes `W` and, when the branch
was checked out before, renaming or deleting the one here first.

More than one local branch can follow the same remote one: `W` makes one each time. `space` takes the first that
`Repo.trackingBranch` finds, whichever that is.

## Decision

### The key

`w` on a remote branch checks it out in a new worktree: the branch `space` would check out, in a worktree as `w` on
a local branch adds one. `?` lists it. `W` stays as it is, for a new branch under a name the user gives.

### Which branch

As `space` decides it, from the branches read, without fetching:

1. **A local branch follows it**: one of those that do, the first of:
    1. the one under its name, `feat-x`, when no worktree has it checked out;
    2. any other that no worktree has checked out, the first by name;
    3. one that another worktree has checked out: switching to that worktree is offered (`SwitchWorktreePopup`), as
       `space` offers it. Not: refused, as `w` on a local branch refuses it; the user asked for the remote branch, and
       that worktree has it;
    4. the one checked out here: a worktree cannot check it out a second time, so a new branch following the remote
       one is asked for, as `W` asks.

   Not: the first one found, which depends on the order the branches are listed in, and can ask for a new branch, or
   offer another worktree, while a follower is free.
2. **No local branch has its name**: one is made under that name, following it, without asking for the name.
3. **A local branch has its name but follows something else, or nothing**: a name is asked for, as `space` asks
   (`NewBranchPopup.following`). Not: taking that branch, which may be another branch altogether.

`space` picks the follower the same way, also when it checks out here or in the main worktree: one function picks it
for both keys.

Then the location is asked for as for any new worktree (`NewWorktreePopup`), starting out at the same default, and
once added the app asks whether to switch to it (`SwitchToAddedPopup`), as after `w` on a local branch.

### A branch behind its remote

A local branch that follows the remote one and goes into a new worktree is fast-forwarded to it when it is behind and
has no commit of its own, as checking out a stack does (ADR 99), run after the location is confirmed and before
`git worktree add`. Cancelling leaves the branch as it was.

```
git fetch --no-write-fetch-head . refs/remotes/<remote>/<remote-branch>:refs/heads/<local-branch>
```

- That is so whichever key gets it there: `w`, and `space` in a linked worktree with *in a new worktree* chosen
  (ADR 68). The checkout asked for keeps that it came from a remote branch on its way to the new worktree.
  Checking out here, or in the main worktree, keeps the branch as it is.
- Without `+`, git refuses what is no fast-forward: a branch that got commits of its own meanwhile fails the whole
  operation, as any failed operation shows, and no worktree is added.
- A branch with commits of its own is checked out as it is, behind or not, and the notice says so:
  `feat-x kept: 1 commit not on origin/feat-x`. Not: rebasing or merging it, which `w` never does.
- Not: checking out the branch as it is, as checking out here does: asking for the remote branch means wanting what
  is on the remote, and a new worktree has no files of its own to lose. Checking out here keeps the branch as it is,
  as before: it has the files of a worktree in use around it.

### Not fetched

The remote branch is checked out as last fetched. Not: fetching it first, a round-trip on every press that `space`
does not make either; the remote branches are fetched in the background as they are now.

## Consequences

- From any worktree, the main one too, a pushed branch is in a worktree of its own with one key and a location.
- `w` means the same on both tabs of branches and on the pull requests screen: this, in a new worktree.
- `space` and `w` share which branch to check out; a change to one is a change to both. Which follower `space`
  checks out no longer depends on the order of the branches.
- A remote branch in a new worktree is at what was fetched of it, whichever key put it there; checked out here, a
  branch that is behind stays behind.

Amends ADR 68 (`0068-asking-where-to-check-out-in-a-linked-worktree.md`): which local branch following a remote one
is checked out, and the fast-forward of one going into a new worktree.

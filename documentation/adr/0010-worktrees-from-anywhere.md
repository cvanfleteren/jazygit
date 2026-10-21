# 10. Worktrees from anywhere, and removed with their branch

Date: 2026-10-21

## Context

With ADR 9 done, a worktree can be added, switched to, removed, pruned and locked. Compared with lazygit, a few gaps
remain:

- A worktree can only be added from the branches, for a local branch or a new branch made from one. The worktrees
  list has no key to add one, and neither a commit nor a remote branch can be checked out on no branch.
- A pull request can only be checked out in place, which needs a clean working tree. To review one alongside the
  work in progress, it has to be checked out by hand in a worktree, and for one from a fork, gh would have to set
  up its branch.
- Removing a worktree keeps its branch, which then has to be deleted on its own. Deleting a branch that another
  worktree has checked out fails up front with `CheckedOutElsewhere` and offers nothing, so it takes three steps:
  remove the worktree, find the branch, delete it.

## Decision

### Removing a worktree together with its branch

- `d` on the worktrees asks whether to keep the branch (`r`) or delete it as well (`b`). `b` is shown only when
  the worktree has a branch, and applies only when no operation runs on that branch.
- `d` on a branch that another worktree has checked out offers `w`: remove that worktree, then delete the branch
  locally. It is offered only when that worktree can be removed (not the main one, not the one shown, not locked).
  The up-front error remains for when neither this nor deleting on the remote is possible.
- Both run a single `RemoveWorktreeCmd` with the branch it deletes (`deleting`): `git worktree remove`, then
  `git branch -D`. When the removal fails, the branch stays. When the worktree has changes, removing it anyway is
  offered, as before, and the forced removal still deletes the branch. Deleting locally takes no remote branch
  (`deleteLocalBranch`).
- Deleting a branch now removes a worktree, so the branch feature depends on the worktree feature. The worktree
  feature used the branch feature only for the `[`/`]` keys of its tab; those keys move to `BranchesTabAction`,
  which switches all the tabs of the branches pane. The features stay free of cycles (`ArchitectureTest`).

### What a new worktree checks out

What a worktree checks out is a sealed `WorktreeSource`:

- `Existing(branch)`: `git worktree add <path> <branch>`.
- `Created(name, from)`: `git worktree add -b <name> <path> <start>`, following the start when it is a remote
  branch.
- `Detached(start)`: `git worktree add --detach <path> <start>`, for a commit, a remote branch or a branch checked
  out elsewhere.
- `PullRequest(pullRequest)`: see below.

Every way of adding one goes through the same popups: where it goes (`NewWorktreePopup`), and once it is added,
whether to switch to it (`SwitchToAddedPopup`). A source names the directory the worktree goes in by default, next
to the main worktree as ADR 9 places it. The popups give the focus back to wherever they were opened from, since a
pull request is added from the pull requests screen rather than from the branches.

### Adding from the worktrees

`n` on the worktrees asks, in turn:

1. Where it starts: a branch, a remote branch (`origin/x`) or a commit. This starts out as the branch checked out
   here, or `HEAD` when none is.
2. The name of a new branch made there. From a local branch, the new branch records it as its parent; from a
   remote branch, it follows that branch and starts out named after it. The name may be left blank: the worktree
   then checks out the local branch typed, when it can be checked out (known, idle, not checked out anywhere), or
   else what was typed on no branch. The title says which of the two a blank name does.
3. Where it goes.

What was typed is not checked against git beforehand. git refuses a start that names nothing, and the failure is
shown as for any other operation.

### A pull request in a new worktree

`w` on a pull request of the repository asks where its worktree goes. By default the directory is named after the
pull request's branch, or `pr-<number>` for one from a fork, whose branch gh may name differently. Then
`AddWorktreeCmd`:

1. Adds the worktree on no branch, at `HEAD`.
2. Runs `gh pr checkout <number>` in it (`GitHub.checkoutPullRequest(number, in)`). This sets up the branch exactly
   as checking the pull request out in place does, from a fork too, without repeating what gh knows.
3. Removes the worktree again (`--force`) when gh fails, so no worktree is left on no branch. The failure carries
   every command that ran, the removal included.

Changes in the worktree shown do not stop it; they stay where they are. As when checking out in place, a pull
request of another repository is refused, and one whose branch another worktree has checked out offers to switch
there. One whose branch is checked out in the worktree shown is refused with `PullRequestCheckedOutHere`, because
git checks a branch out in only one worktree at a time.

## Consequences

- `AddWorktreeCmd` runs gh for a pull request, so it is an `OperationCmd` with its own `run` rather than a
  `GitOperationCmd`. A gh failure is reported as such, after the git commands that ran before it.
- `git worktree add --detach` (`addDetachedWorktree`) is tested against a real repository, as `CliWorktreeTest`
  does. The commands, the cleanup after gh fails, and each step of the popups are tested against stubs.
- `NewWorktreePopup`, `NewWorktreeBranchPopup` and `SwitchToAddedPopup` hold a source or its label instead of a
  branch name, and `NewWorktreeBranchPopup` holds what a blank name means (`ifBlank`).
- Of the gaps listed against lazygit, opening a worktree in an editor, locking with a reason, a dirty or
  ahead/behind marker per worktree, and `git worktree move` and `repair` remain.

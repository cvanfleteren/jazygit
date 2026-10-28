# 43. Nuke a branch

Date: 2026-10-28

## Context

Deleting a branch everywhere takes up to four dialogs today (#200). `d` asks where to delete it (`DeleteBranchPopup`,
`DeleteScope`). When another worktree has the branch checked out, removing that worktree is offered, and when it is
locked, whether to unlock it is asked first (`UnlockWorktreePopup`). A worktree with changes is removed only once
removing it anyway is confirmed (`RemoveWorktreeMsg.NeedsForce`). A branch that is not merged is deleted only once
losing its commits is confirmed (`ForceDeleteCmd.refused`, `ForceDeleteMsg.NotFullyMerged`).

The user wants one confirmation, after which jazygit deletes the branch, its branch on the remote and its worktree,
as far as it can, without the warnings.

`Branch.pushTarget()` is where the branch is pushed: its upstream, which can have another name. A branch made from
`origin/main` follows `origin/main` under git's default `branch.autoSetupMerge`, as it does when jazygit makes it
(ADR 40). `Branch.isDefault` only says whether the local branch is the default one.

## Decision

### `D` on a local branch

- `D` on the local branches deletes the branch, its worktree and its branch on the remote, after one confirmation.
  It is git's `-d`/`-D` pair. Not: `D` on the remote branches, where it would be `d` without its dialog.
- It is refused, with the errors `d` already gives:
  - the default branch: `AppError.DefaultBranch`;
  - a branch checked out in the worktree shown: `ShownWorktreeNotRemovable`;
  - a branch checked out in the main worktree: `MainWorktreeNotRemovable`.

  Not: deleting what still can be, the remote branch alone, which is not what a nuke of the branch promises.
- Like `d`, it opens its dialog only for a known branch that no operation runs on, and not while an operation runs
  on its remote branch either. Confirming checks that again (`Update.confirm`), and that the worktree and the remote
  branch are still the ones the dialog named; when not, nothing starts.

### Which remote branch

- The remote branch deleted is the push target, only when it is on `origin`, has the branch's own name and exists
  (`Branch.hasRemote`). A push target of another name, like `origin/main` for a branch made from it, or on another
  remote, is left alone, and the dialog does not name it. Not: deleting the push target whatever its name, which
  would delete `main`.
- As the local branch is not the default one, a remote branch of its name on `origin` is not the default one either.
  The default branch is only known for `origin` (`origin/HEAD`), so a remote branch on another remote could be its
  default one, like `upstream/develop` in a fork. Not: telling the default branch of every remote from
  `refs/remotes/<remote>/HEAD`, which takes reading it for every remote, and which a remote does not always have.

### One dialog

- `NukeBranchPopup` is a yes/no `ConfirmDialog`, confirmed as the others are (ADR 38). It opens at once, with nothing
  read first.
- It says sternly that the branch is nuked, and names what goes: the branch, its remote branch when it deletes one,
  and the worktree that has it checked out, marked `locked` when it is. It warns that commits only that branch has
  and uncommitted changes in that worktree are lost, without counting them. Not: counting the commits and files lost,
  which takes git commands before the dialog, and a slip from `d` is already caught by the stern dialog.
- Not: typing the branch name to confirm.

### One command

- `NukeBranchCmd`, in `feature/branch/nuke`, does every step in its own `perform`, and is one entry in the log with
  every git command it ran. The local branch is busy until it is done. It guards the remote refs when it deletes the
  remote branch (ADR 27). Not: chaining `RemoveWorktreeCmd` and `DeleteBranch` through messages, whose refusals would
  open their own popups half way.
- The remote branch is not marked busy: a command keeps one branch busy. A `d` on it from the remote branches while
  the nuke runs waits for the guard, and then fails as the branch is gone.
- The steps, in this order:
  1. The worktree: `git worktree remove --force --force`, which removes one that is locked, that has changes, or
     whose directory is gone. Not: `git worktree prune` for a gone one, which leaves it when it is locked.
  2. The branch: `git branch -D`.
  3. The remote branch: `git push <remote> --delete refs/heads/<branch>` (ADR 40).

  The worktree goes first, as git does not delete a branch that is checked out. The remote branch goes last, as the
  only step that talks to the remote; which of the branch and the remote branch goes first loses nothing when the
  other fails, as the one left keeps the commits.
- A step that fails skips the steps that need it and not the others: when the worktree is not removed, the branch is
  not deleted, but its remote branch is. What was not deleted is said in one error. Not: stopping at the first
  failure, which leaves what could have gone.
- A branch without a remote branch to delete has no third step; one not checked out elsewhere, no first.

## Consequences

- A slip from `d` to `D` loses commits and changes after one confirmation, which says so but not how many.
- A branch that follows a remote branch of another name, or one on another remote than `origin`, keeps that one; it
  is deleted with `d`, whose dialog names it.
- Acceptance tests cover a nuke with a worktree, a locked one, one with changes and a gone and locked one; a branch
  that is not merged, one that is local only, one that follows `origin/main` and one that follows
  `upstream/<its name>`, whose remote branches stay; the three refusals; and a step that fails.

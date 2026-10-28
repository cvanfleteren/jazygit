# 43. Nuke a branch

Date: 2026-10-28

## Context

Deleting a branch everywhere takes up to four dialogs today (#200). `d` asks where to delete it (`DeleteBranchPopup`,
`DeleteScope`). When another worktree has the branch checked out, removing that worktree is offered, and when it is
locked, whether to unlock it is asked first (`UnlockWorktreePopup`). A worktree with changes is removed only once
removing it anyway is confirmed (`RemoveWorktreeMsg.NeedsForce`). A branch that is not merged is deleted only once
losing its commits is confirmed (`ForceDeleteCmd.refused`, `ForceDeleteMsg.NotFullyMerged`).

The user wants one confirmation, after which jazygit deletes the branch, its push target and its worktree, as far
as it can, without the warnings.

What the model already knows of what would be lost is little:

- `Branch.ahead()` counts the commits not on the upstream, but the upstream is deleted too, so pushed commits are
  lost as well.
- `GitInfoProvider.countOnlyOn` counts the commits no other branch, remote-tracking branch or tag has. It counts the
  ones on the push target as kept, and that one is deleted too.
- `Repo.hasChanges(worktree)` (ADR 11) says whether a worktree has changes, without a count, and only while the
  worktrees are shown.

## Decision

### `D` on a local branch

- `D` on the local branches deletes the branch, its worktree and its push target, after one confirmation. It is
  git's `-d`/`-D` pair. Not: `D` on the remote branches, where it would be `d` without its dialog.
- It is refused, with the errors `d` already gives, before anything is read:
  - the default branch: `AppError.DefaultBranch`;
  - a branch checked out in the worktree shown: `ShownWorktreeNotRemovable`;
  - a branch checked out in the main worktree: `MainWorktreeNotRemovable`.

  Not: deleting what still can be, the push target alone, which is not what a nuke of the branch promises.
- A branch cannot be nuked while it is being pushed or deleted, as with `d`.

### What would be lost is read before asking

- `D` marks the branch busy and starts one command that reads what would be lost; its message opens the dialog. The
  dialog is not opened when the branch is gone or no longer idle by then, and nothing is said.
- The commits lost: those of the branch that no other branch, remote-tracking branch or tag has, apart from its push
  target. That is `countOnlyOn` with the push target excluded too (`--exclude=<remote>/<branch>` before `--remotes`),
  one `git rev-list --count`.
- The files lost: those `git status --porcelain -z --untracked-files=normal` lists in the worktree removed, as ADR 11
  reads its changes; ignored files are not counted. Only when there is a worktree to remove and its directory exists.
- A count that cannot be read is left out of the dialog, which opens anyway. Not: failing the nuke, which the user
  asked to go ahead regardless.

### One dialog

- `NukeBranchPopup` names what goes: the branch, its push target when it has one, and the worktree that has it checked
  out, marked `locked` or `gone`. It says how many commits and uncommitted files are lost, each only when more than 0.
- It is a yes/no `ConfirmDialog`, confirmed as the others are (ADR 38). Not: typing the branch name to confirm.
- What is lost is not read again once confirmed: the confirmation stands for whatever is there when it runs.

### One command

- `NukeBranchCmd`, in `feature/branch/nuke`, does every step in its own `perform`, and is one entry in the log with
  every git command it ran. The branch stays busy until it is done. It guards the remote refs when it deletes the push
  target (ADR 27). Not: chaining `RemoveWorktreeCmd` and `DeleteBranch` through messages, whose refusals would open
  their own popups half way.
- The steps, in this order:
  1. The worktree: `git worktree remove --force --force`, which removes a locked one and one with changes; when its
     directory is gone, `git worktree prune` instead.
  2. The branch: `git branch -D`.
  3. The push target: `git push <remote> --delete refs/heads/<branch>` (ADR 40).

  The worktree goes first, as git does not delete a branch that is checked out. The push target goes last, so that a
  nuke that fails half way has not deleted the remote copy while the local one stays.
- A step that fails skips the steps that need it and not the others: when the worktree is not removed, the branch is
  not deleted, but its push target is, as the commits are still on the branch. What was not deleted is said in one
  error. Not: stopping at the first failure, which leaves what could have gone.
- A branch without a push target, or whose push target is gone, has no third step; one not checked out elsewhere,
  no first.

## Consequences

- A slip from `d` to `D` loses commits and changes after one keypress more. The dialog says how many.
- `D` opens its dialog after one or two git commands, not at once.
- `GitInfoProvider` gets a count of the commits only a branch and its push target have, and a count of a worktree's
  changes.
- Acceptance tests cover a nuke with a worktree, a locked one, one with changes and a gone one; a branch that is not
  merged and one that is local only; the three refusals; and a step that fails.

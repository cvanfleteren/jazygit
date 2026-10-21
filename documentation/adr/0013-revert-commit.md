# 13. Reverting a commit

Date: 2026-10-21

## Context

A revert in progress is shown and can be continued, aborted or skipped with `M` (`InProgress.Reverting`,
`CliInProgress`), but nothing starts one: the commits panel has no revert. Cherry-pick and drop show how a commit
action runs git, asks the user, and leaves a conflict for `M`.

## Decision

### Asking

- `t` in the commits panel (`Binding.onSelected`, `help.commits.revert`) reverts the selected commit. It is free,
  and it is what lazygit uses. One commit at a time: the panel has no multi-select.
- It asks for confirmation, as drop does (`ConfirmDialog`), naming the commit.
- The revert gets the message git writes (`--no-edit`). Saying why is a reword (`r`) of the new commit afterwards,
  which for the last commit is an amend.
- A merge commit is reverted against its first parent (`-m 1`), the branch it was merged into; the confirmation
  says so. Cherry-pick refuses merges, but reverting a merged pull request is the usual reason to revert.
- Nothing is checked up front. Git refuses when local changes would be overwritten; unrelated changes are fine.
  Reverting a commit already reverted fails as git fails ("nothing to commit"), and is reported as any failure.

### Running

- `git revert --no-edit [-m 1] <sha>` (`CliRevert`), through `Unfinished.runOrUndo`, undone with
  `git revert --abort` when it left a revert in progress, as cherry-pick is: a stop on conflicts is kept, other
  failures are undone, and nothing runs while something else is in progress.
- On conflicts, `MERGE_MSG` holds git's message, which `M` → continue commits, as for a revert started outside the
  app.
- A `feature/commit/revert` package, shaped like drop: `RevertAction`, `RevertMsg` (Requested, Confirmed,
  Cancelled), `RevertUpdate`, `RevertCmd` (a `GitOperationCmd`), `RevertDialogs` and `RevertPopup`. Success goes
  through the plain `OperationMsg.Succeeded`; a conflict through `OperationMsg.Stopped` and its notice, as for
  cherry-pick.

### Not editing the message first

The first version opened the commit dialog, filled with git's message, and committed what was typed. That took a
copy of git's rules for the message, an editor writing it into `git revert --edit`, and overwriting `MERGE_MSG` on
conflicts. Continuing with `M` still lost lines starting with `#`: `git revert --continue` strips them, and ignores
`commit.cleanup`. Keeping them would have taken a revert of its own in `CliInProgress`. Rewording afterwards gives
the same result for one key more, also after a conflict.

## Consequences

- A conflicted revert is resolved outside the app and finished with `M`.
- The message is changed after the revert, not before.
- Reverting a merge against another parent than the first is not possible from the app.

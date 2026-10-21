# 13. Reverting a commit

Date: 2026-10-21

## Context

A revert in progress is shown and can be continued, aborted or skipped with `M` (`InProgress.Reverting`,
`CliInProgress`), but nothing starts one: the commits panel has no revert. Cherry-pick, drop and reword show how
a commit action runs git, asks the user, and leaves a conflict for `M`.

## Decision

### Asking

- `t` in the commits panel (`Binding.onSelected`, `help.commits.revert`) reverts the selected commit. It is free,
  and it is what lazygit uses. One commit at a time: the panel has no multi-select.
- It opens a message dialog, as reword does, filled with the message git would write. That dialog is the
  confirmation: Enter reverts, Esc cancels. A blank summary keeps it open.
- The message is built from the `Commit`, as git builds it: `Revert "<summary>"`, a blank line, and
  `This reverts commit <sha>.`; for a merge, `This reverts commit <sha>, reversing\nchanges made to <first parent>.`
- A merge commit is reverted against its first parent (`-m 1`), the branch it was merged into; the dialog's title
  says so. Cherry-pick refuses merges, but reverting a merged pull request is the usual reason to revert.
- Nothing is checked up front. Git refuses when local changes would be overwritten; unrelated changes are fine.
  Reverting a commit already reverted fails as git fails ("nothing to commit"), and is reported as any failure.

### Running

- `git revert --edit [-m 1] <sha>`, with a `GIT_EDITOR` that writes the user's message, as `CliRebase.reword`
  does: one command, one commit. `--no-commit` followed by `git commit` is not used: it takes what is staged into
  the revert, where `git revert` refuses.
- It runs through `Unfinished.runOrUndo`, undone with `git revert --abort` when it left a revert in progress, as
  cherry-pick is: a stop on conflicts is kept, other failures are undone, and nothing runs while something else
  is in progress.
- On conflicts git does not call the editor, so `MERGE_MSG` holds git's message, which `M` → continue would
  commit. The user's message is written to `MERGE_MSG` (`git rev-parse --git-path MERGE_MSG`) when the revert
  stops on conflicts, so continuing keeps it.
- A `feature/commit/revert` package, shaped like reword: `RevertAction`, `RevertMsg` (Requested, Confirmed,
  Cancelled), `RevertUpdate`, `RevertCmd` (a `GitOperationCmd`), `RevertDialogs` (`CommitDialog.revert`) and
  `RevertPopup` (a `MessagePopup`, so Ctrl+O edits it in the editor too). Success goes through the plain
  `OperationMsg.Succeeded`, as reword's does; a conflict through `OperationMsg.Stopped` and its notice, as for
  cherry-pick. The message is built by `CommitMessage.revertOf`, and the git side is `CliRevert`.

## Consequences

- A conflicted revert is resolved outside the app and finished with `M`, keeping the message the user typed.
- Reverting a merge against another parent than the first is not possible from the app.

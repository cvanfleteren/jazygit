# 205. A feature says its notices itself

Date: 2026-11-26

## Context

ADR 169 opened `Notice` to the features as it opened `AppError`: a feature's notice is a `Notice.Phrased`, of a
sealed set of its own, that says itself as a `Phrase`. Only the notices made then took that way (the stack's
`NotListed`, then Forgejo's, the midway's and the copied sha). The kernel still declared, and `NoticeText` still
phrased, eleven notices that only a feature makes, with their messages in the kernel's bundle:

- GitHub's `MergeEnqueued` and `MergeStillPending` (`github.show`), and `StackNotAsked` and `StackCheckedOut`
  (`github.stack`), beside the `StackNotice.NotListed` of the same stack;
- the copy's `Copied`, `PathCopied`, `NotCopied` and `CopyFailed` (`changes.copy`), beside its `ShaCopied`, which was
  phrased already;
- the files pane's `NoNextFile` and `NoPreviousFile` (`changes.files.step`);
- the new worktree's `BranchKept` (`branches.worktree.add`).

A new notice of one of these features edited `Notice` and `NoticeText`, and its key broke the rule that a message is in
the bundle of the module that names it (ADR 136).

## Decision

- **Each moves to its feature as a `Notice.Phrased`**, in a sealed set beside the code that makes it:
  - `MergeNotice.Enqueued` and `MergeNotice.StillPending` in `github.show`;
  - `StackNotice.NotAsked` and `StackNotice.CheckedOut` join `StackNotice.NotListed` in `github.stack`;
  - `CopyNotice.LinesCopied`, `PathCopied`, `ShaCopied`, `NotCopied` and `CopyFailed` in `changes.copy`; the
    command and the message of a copy hold a `CopyNotice`, not any notice;
  - `FileStepNotice.NoNextFile` and `NoPreviousFile` in `changes.files.step`;
  - `BranchKeptNotice` in `branches.worktree.add`.
- **Their messages move with them**, under the root of their module (`github.notice.…`, `changes.notice.…`,
  `branches.notice.…`), with the same text.
- **The kernel keeps what it makes or builds on**: `StoppedOnConflicts`, which the kernel's operations stop with, and
  `MoveStopped`, which says a `StoppedOnConflicts` and then how to put the moved commits back. `MoveStopped` is made
  only by `branches.branch.movecommits`, but its text starts with the kernel's text of the stop, which a `Phrase` cannot
  say: that text is made in `NoticeText` from `InProgressText`.

## Consequences

- A feature adds a notice without editing the kernel, and `NoticeText` phrases only the kernel's two.
- A test of a notice's text is in its feature, beside the notice.
- `MoveStopped` is the one notice of a feature left in the kernel. It can move once `StoppedOnConflicts` says itself
  as a `Phrase` (its keys and the kind of operation are phrases already, `AppError.Unfinished.kind`), so that
  `MoveStopped` joins it with its own.

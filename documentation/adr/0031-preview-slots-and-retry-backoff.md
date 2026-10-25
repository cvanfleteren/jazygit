# 31. Every preview takes a slot, and failed reads back off

Date: 2026-10-25

## Context

ADR 12 let a command take a slot, so that a newer one for that slot cancels it before it starts. Only
`LoadPullRequestPreview` took one. The other previews did not: each highlight move queued its own `git log` or
`git show` on `git-io`, so holding a key through a long list queued one read per item passed, in front of the
status and of the operations the user starts.

A cancelled command reported nothing. That was fine for the pull request preview, which nothing waits for, but a
periodic reload of a preview (a moved branch's log, the diff of the highlighted files, a retry) is kept in
`Jobs.refreshing` until it reports. Skipped without a word, it would stay there, and that load would never be
started again.

A read that failed was tried again on every tick (750 ms). A preview that cannot be read, like that of a commit
that is gone, ran a git process on every tick for as long as it was highlighted.

## Decision

### Every preview takes a slot

- `LoadBranchLog`, `LoadStashDetail` and `LoadFileDiff` take a slot of their own (`Cmd.Slot`).
- `LoadCommitDetail` names the preview it reads for (`preview`): the commits pane, a divergence, or the commits of a
  branch. Each has its own slot. If they shared one, highlighting a commit in one view could cancel the read
  another view waits for, and that view would keep loading. When two views show the same commit, it is read once
  for each.
- A cancelled command reports `Cmd.skipped()`, if anything. A `Load` reports `LoadMsg.Skipped`, which takes it out
  of `refreshing` and changes nothing else: it is no failure, and keeps the backoff of a read that failed before.
  `LoadPullRequestPreview` still reports nothing.

### Failed reads back off

- `Jobs` counts the ticks (`ticks`) and, for each read that failed, how often it failed in a row and on which tick
  (`retries`). A tick tries it again once it waited 1 tick after the first failure, then twice as long after each
  failure in a row, up to 64 ticks (`Jobs.retryDue`), under a minute at the default interval.
- A read that succeeds clears its count. One whose failure is no longer shown, like the preview of an item no longer
  highlighted, is forgotten on the next tick or refresh.
- `Update.refresh`, after an operation, still tries every failed read at once: what changed may be what made it
  fail.
- `ticks` and `retries` are not drawn (`Model.drawn()`), so an idle tick still draws no frame.

## Consequences

- Moving through a list faster than git answers only reads the item highlighted last.
- A preview that keeps failing shows its error for up to about 48 s before it is tried again, unless the user
  highlights another item and comes back, or an operation refreshes.

# 71. A load keeps what it reads in a target

Date: 2026-11-03

## Context

The review in issue #294 (section 1b, item 5) found that the load pipeline churns for a structural reason. Adding or
changing a load touched its `Cmd.Load` record, its `LoadMsg`, a case in `LoadUpdate.update`, a near-mirror case in
`LoadUpdate.failed`, and a clause in `Update.retries`. The loaded and the failed cases walked the model to the same
`Loadable` twice, each with its own "only if this is still the view that asked" check, and `Update.retries` walked to
it a third time to see whether it had failed. Pull request #306 added `Loadable.isFailed()` and a `mapWhere` helper,
which shortened that code but did not remove the three walks.

The review proposed a slot per load: a function pair that finds and replaces its `Loadable` in the model. Two names
were taken or misleading: `Cmd.Slot` already means the latest-wins slot of a command that a newer one cancels, and
"focus" is the focus of a pane.

Not every load fills a single `Loadable`:

- `LoadBranches` fills two (the local and the remote-tracking branches), and keeps the parents and the counts read
  before when they cannot be read (ADR 46).
- `LoadAhead` keeps whether reading what is ahead of HEAD failed in `Jobs`, whatever value was kept.
- `LoadWorktreeChanges` fills a map, not a `Loadable`, and a failure only marks it read.
- `CheckMetadata` and `CheckFileDiff` keep what the files look like in `Jobs`.

And the loads that do fill one do not fail the same way: what the repository holds (the status, the stashes, the
worktrees, the log) keeps a value read before when reading again fails, as that can fail for a while; what a view
shows (a search, a divergence, a preview, the files of a commit) is replaced by the failure.

## Decision

1. **An optional lens, `Lens<S, A>`, finds an `A` in an `S` if it is there, and changes it.** `Lens.of` is a field,
   `Lens.where` an `Option` field while it holds the one wanted, `Lens.some` one that holds anything; `andThen`
   composes, and `alongWith` changes the same value kept in two places while reading only the first. A change that
   changes nothing gives back the same `S`, so unchanged data keeps its identity, as `Loadable.reload` already
   promised.
2. **A `Target<T>` is where a load keeps what it reads:** a `Lens<Model, Loadable<T>>`, and whether a value read
   before stays when reading again fails. `Target.inRepo` keeps it; `Target.inView` replaces it. The lens finds the
   loadable only while the model still wants what the load reads, so what was read for another item, or for a view
   closed since, finds no target and is dropped. `Preview.lens` and `CommitFilesView.open` give the lenses most
   targets are built from.
3. **A load that has a target is a `Cmd.Targeted<T>`, and says what its target is.** The loads that keep what they read
   in a way of their own (`LoadBranches`, `LoadAhead`, `LoadWorktreeChanges`, `CheckMetadata`, `CheckFileDiff`) are
   not targeted; they do not pretend to have an empty target. `LoadCommitDetail` reads from the preview of its own
   `Cmd.Slot`, and changes every preview of its commit.
4. **What a targeted load read comes back as a `LoadMsg.Read<T>`,** whose `kept(model)` puts it in the target. The
   messages that only keep what they read share one case in `LoadUpdate.update`; the others call `kept` and then do
   what is theirs.
5. **`LoadUpdate.failed` stays exhaustive over the loads.** A targeted load fails in its target; each load that is not
   targeted keeps its own case. A new load that is not targeted does not compile until it says what its failure does.
6. **`Update.shown` is the one list of the loads for what the open views show,** as the model would ask for them now.
   `Update.retries` keeps those whose target failed, after the log of HEAD and what is ahead, which keep their own
   rules; `DiffOptionsUpdate` keeps those that read with diff options.

## Consequences

- A new load that keeps a `Loadable` writes its target once, next to its `run`, and a `Read` message. Its failure and
  its retry follow; only a load the open views show is added to `Update.shown`.
- Whether a read is still wanted is decided in one place per load, its lens, instead of separately when it loaded, when
  it failed and when it is retried. A wrong lens there drops a read or never retries it, for that load everywhere.
- The difference between keeping a value and replacing it by a failure is now a property of the target, stated once,
  instead of a choice in each case of `LoadUpdate.failed`.
- `Lens` is a small optics vocabulary that state code did not have; it is only used for targets so far.
- The loads that are not targeted (the branches, what is ahead, the worktree changes, the two checks of the files)
  still spell out their handling in `LoadUpdate` and `AheadUpdate`.

Follows from issue #294, section 1b and item 5, and pull request #306.

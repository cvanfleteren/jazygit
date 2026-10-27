# 39. What runs is told from `Jobs.running`

Date: 2026-10-27

## Context

ADR 33 gave the model `Jobs.running`: every command, by an id, from the moment `Update` asks for it until `Program`
reports it done with `Msg.Done`. Four older accounts of what runs stayed next to it, each kept in its own way:

| account                   | holds                                  | taken out by                                  |
|---------------------------|----------------------------------------|-----------------------------------------------|
| `Jobs.refreshing`         | the periodic loads, retries, the diff  | `LoadUpdate`, on the load's `LoadMsg`         |
| `Jobs.busy`               | the branch an operation runs on        | `OperationUpdate.done`, on its `OperationMsg` |
| `Jobs.fetching`           | the remote fetched from, those queued  | `FetchUpdate`, on each `FetchMsg`             |
| `GitHubState.reading`     | the reads from GitHub                  | each read's own update                        |
| `PrOverview.reading`      | the reads of the pull requests screen  | `PrOverviewUpdate`, on their messages         |

Each is added to where its command is asked for and taken out where its result is handled, next to `Msg.Done`, which
already does both for every command: a handler that asks for one of those commands, or handles its result, has to
keep two accounts in step, and one that forgets leaves it wrong for good. They are sets of commands compared by value,
so of two equal loads that run at once the first one done takes both out. And to know what runs, one has to know which
of six to ask. ADR 33 left this for later: "`Jobs.refreshing`, `Jobs.fetching` and
`GitHubState.reading` could later be told from `running`".

`Jobs.lingering` is not one of them: it says what the indicator shows after the commands that showed are done.

## Decision

`Jobs.running` is all the model knows of what runs. The other accounts go, and what they told is asked of `running`.

### Asked of `running`

- `Jobs.runs(cmd)`: whether a command equal to `cmd` runs; `Jobs.runs(predicate)`: whether one that the predicate
  tells runs, like any `LoadWorktreeChanges` or any `FetchCmd`. A load that is not started again while it runs, a
  read of GitHub that waits for the one that runs, the checks of the pending branches, and the reads of the pull
  requests screen all ask that.
- `Jobs.busy()`: the branches an operation runs on, and that operation, from the `OperationCmd`s that run and their
  `busyBranch()`. Not kept, but told each time: a busy branch is asked for when a key is handled and when the
  branches are drawn, among a handful of commands.
- `Update.start` only asks for the operation; `OperationUpdate.done` no longer releases its branch, `Msg.Done` does.
  `Model.withBusyBranch` and `withoutBusyBranch` go.
- `PrOverviewUpdate.update(overview, msg, now, jobs)` is told the jobs, as the overview alone no longer knows what
  of it runs.

### A message asks for a command once

The commands a message asks for only run, in `running`, once the message is handled: `Update.step` stamps them as its
last step (ADR 33). Within one message, an account that was added to at once told that a load was already asked for,
as when an operation that is done reads the repository again and a fetch that is done does too. So `Next` drops a
command equal to one asked for before it in the same message. Not: stamping each command as it is asked for, which
every handler would have to do, and which ADR 33 kept out of them. Not: dropping, as it is stamped, a load that runs
already: a preview asked for again, after a newer one for its slot cancelled it and before the cancelled one reported,
would never run.

### The fetch

- A fetch runs while one of its commands does: the listing of the remotes, or a fetch from one.
- `FetchCmd.Fetch(remote, rest)` carries the remotes still to fetch from after it, and `FetchMsg.Fetched` and
  `FetchFailed` hand them back, so the next is fetched from without the model keeping a queue. Which remote is fetched
  from is that of the `Fetch` that runs, which the indicator shows as its label (ADR 33).
- Whether to fetch again once the fetch is done, as something changed on a remote that it may have missed, is
  `Jobs.fetchAgain`: that is not what runs, but what is asked for after it, as `GitHubState.again` is for the reads of
  GitHub. `Fetching` goes.

### Drawn

- `Model.drawn()` leaves `running` out, as ADR 33 has it, but for the operations that keep a branch busy: the spinner
  of that branch shows at once, so a frame is drawn as soon as one starts or is done. Before, `Jobs.busy` was drawn.
- `Jobs.fetchAgain` is not drawn.

## Consequences

- One place tells what runs, and one message, `Msg.Done`, takes a command out of it, also when it reported nothing.
  Two equal loads that run at once both count until both are done.
- A busy branch is released when its command is done, whatever it reported. An operation that reports a message of
  its own, like the refusal to delete a branch that is not merged, no longer has to release it.
- A command asked for twice in one message runs once, whatever it is.
- The tests tell a command running as `Program` has it: `TestModels.stepped` handles a message and runs what it asks
  for, `TestModels.done` reports a running command done with what it reported, and `TestModels.running` and `busy`
  start commands. `TestModels.loaded()` is built that way, so nothing runs in it; before, the worktrees it never read
  stayed in `refreshing`, and the reads of the repository again in the tests now read them too.
- While a busy branch's operation runs, the indicator at the bottom turns on every tab, as it does for any command;
  before, a busy branch on a tab that does not show it turned no spinner in the tests, though in the app its command
  was in `running` all the same.
- Not changed: the popups' `Reading` (ADR 34), which says whether the user asked for a read and which read it is, more
  than that one runs; and `Jobs.lingering`.

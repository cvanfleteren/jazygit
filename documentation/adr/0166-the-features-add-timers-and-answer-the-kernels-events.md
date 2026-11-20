# 166. The features add timers and answer the kernel's events

Date: 2026-11-20

## Context

ADR 158 opened the model to the features: a `Part` declares what the kernel asks of it, and a part that is kept answers
a `KernelEvent`. That does not reach a feature that reads from outside the repository, like GitHub's pull requests and
checks: it must answer always, not only while it keeps a part, and it has timers of its own. Both were still in the
kernel and the app, which named GitHub:

- `Timers` in the app listed `PrOverviewMsg`, `PullRequestMsg`, `ChecksMsg` and `StackMsg` next to the fetch.
- `OperationUpdate` read the pull requests again after an operation that `changesGitHub()`.
- `FetchUpdate` (in `features-changes`) read them after each fetch, with `PullRequestsUpdate.poll`.
- `RepositorySession` dispatched `PullRequestsMsg.Refresh` once a repository was opened.

So the kernel could not be built without GitHub, and a second forge would have had to edit all four.

## Decision

- **A feature declares its timers.** `Feature.every(interval, msg)` adds a message to dispatch every so often; the app
  starts the timers of `Features.ALL`, in the order of the features, next to the kernel's own refresh tick. A feature
  that is not listed has no timer, so its ticks stop with it.
- **A feature declares its reactions.** `Feature.reacting(reaction)` adds a `Reactions.Reaction`, a function from the
  model and a `KernelEvent` to a `Next`. The app gathers them from `Features.ALL` into `Reactions`, which the model
  holds without naming a feature, as it holds its parts (`Model.reactions`). `Events.happened(next, event)` tells the
  parts that are kept first, then the reactions, each with the model the earlier ones left.
- **The kernel says what happened, without naming a forge.** `KernelEvent` gains `OperationDone(changedForge)`,
  `Fetched` and `RepositoryOpened`. `OperationCmd.changesGitHub()` becomes `changesForge()`: whether the operation
  changed what the repository's forge has.
- **GitHub answers them in its own package.** `GitHubReactions` reads the pull requests and the checks again after an
  operation that changed the forge, after a fetch (those reads that do not keep failing), and once a repository is
  opened.

## Consequences

- `OperationUpdate`, `FetchUpdate`, `Timers` and `RepositorySession` no longer name GitHub. `PullRequestsUpdate` and the
  `kernel.state.github` package are still in the kernel, until the state moves (the next step of #588).
- A test that needs the model to read from GitHub after an event gives it the feature's reactions
  (`GitHubFeature.FEATURE.reactions()`); the sample model of the GitHub tests has them.
- An event is added when the first feature needs it, and a reaction leaves the model alone for those that are none of
  its concern, as a part's `reacting` does.
- The order of the timers is the order of the features, no longer a list in the app; no timer depends on another.

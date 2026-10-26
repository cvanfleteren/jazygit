# 35. A workflow that waits to start is queued

Date: 2026-10-27

## Context

The checks of a commit are read from its `statusCheckRollup` (ADR 32): its state for the branches, its check runs one
by one for the checks popup, the branches pane's estimate and the pull request dialog. A workflow run has check runs
only once it starts. One that waits, as the publish workflow on `main` does for the one before it in its
`concurrency` group, or for a runner, has none, and so neither has the rollup: it is `null`, which reads as no
checks. On `main` after a merge, the branches pane shows no symbol, and `c` does nothing, as the popup only opens on
a branch whose tip has checks (ADR 32). There is no way to see that the workflow is there and waits.

GitHub does tell it, in GraphQL, through the commit's `checkSuites`: a suite of GitHub Actions has a `workflowRun`,
with its `createdAt`, `runAttempt`, `event` and workflow, from the moment it is queued, and a `status` that is not
`COMPLETED` while it is not done; while it waits, it has no check runs (`checkRuns.totalCount` is 0). Apps that
listen to check suites get one on every commit too, `claude` here, and leave it `QUEUED` for good when they never add
a check run; those have no `workflowRun`. Over the last 40 commits of `main` and every branch, no suite of Actions
was left not completed without check runs.

## Decision

A workflow run that waits to start is a queued check, named after its workflow, wherever checks are read; every
queued check of a first attempt shows how long it has been queued.

### Read

- A workflow that waits is a suite of a commit with a `workflowRun`, a `status` other than `COMPLETED`, and no check
  run. Not: a suite without a `workflowRun`, as an app leaves those queued for good; nor the suites of any status
  with no check run, as a workflow whose jobs were all skipped is `COMPLETED` with none.
- The first 20 suites of a commit are read, next to its rollup, in the query of the branches, of the checks of
  branches one by one and of the details of a pull request. Not in the query of the checks that ran lately: a
  workflow that waits teaches nothing. The branches query still costs 1 point a page.
- In the branches, a branch with a workflow that waits is pending, unless its checks failed: its state is that of
  the rollup combined with a pending one. So `main` shows `⏳` and `c` opens the popup. Read one by one, a workflow
  that waits is a check of its own, queued, next to the check runs: `💤 Publish fat-jar and native image  queued 3m`.
- It is a check run without its own id (0), conclusion, start or end, with the id, creation, attempt and event of
  its workflow run and the workflow's name as its name. So it is queued, timed from its run, and never learned from,
  as it never passes. Not: a kind of check of its own, which every place that lists checks would have to know.

### Shown

- A queued check of the first attempt of its workflow run shows how long ago that run was created, as a running one
  shows how long it runs, to 5s under an hour in the dialogs (ADR 34): `queued 3m 20s · ~2m left`. That is how long
  it waits, for its workflow to start, a runner, or the jobs it needs. Of a re-run it shows `queued`, as GitHub
  does not say when an attempt started. Not: only for a workflow that waits, as a job waiting for a runner waits as
  much.
- The estimate of a workflow that waits is not known, as no check of its own name has a history: the checks
  together show `done in ?`, and the branches pane `⏳ ?`. Not: an estimate from the checks its workflow ran before,
  which needs which checks a workflow has, and how long it waits is what is not known.
- The branches pane does not tell queued from running: it has room for one symbol and a time.

## Consequences

- One step: the queries, their jq and parsing, and the time a check is queued.
- A workflow that waits is timed from when its run was created, so once it starts, its checks are expected later
  than they were before it waited, as ADR 32 has it; how long it waited is in what is learned from them too.
- An app's suite that is left queued is not shown; one of Actions that is would show as queued for good.
- Tested: the parsing of a branch with a workflow that waits, also next to failed checks and without a rollup; the
  line of a workflow that waits read one by one, in the branches and the pull request dialog; that it is queued and
  never learned from; `queued 3m 20s` for a first attempt and `queued` for a re-run. The jq of the three queries is
  not run by the tests, as no other jq is; it was tried on what GitHub gives.

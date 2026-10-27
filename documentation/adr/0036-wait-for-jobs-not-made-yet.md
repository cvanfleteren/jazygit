# 36. Wait for the jobs GitHub has not made yet

Date: 2026-10-27

## Context

ADR 32 expects the checks of a commit done at the latest of when each check that is not done is expected, a check
being timed from when its workflow run started. It only knows the checks in the rollup. GitHub makes the check run of
a job that `needs` others only once those are done; until then, the job is not in the rollup at all.

On `main`, `Publish fat-jar and native image` runs `verify / verify`, `verify / package` and `verify / acceptance`,
and then `snapshot`, which needs them. The history knows how long after the run started each is usually done:

| check                 | finished |
|-----------------------|----------|
| `verify / verify`     | 103s     |
| `verify / package`    | 52s      |
| `verify / acceptance` | 300s     |
| `snapshot`            | 458s     |

Until `acceptance` is done, the checks are expected done 300s into the run, and near its end the estimate says
`~10s`. Then `snapshot` shows up, queued, and the estimate jumps to 458s: `~2m 50s` more. The history already knew
that the run takes about 7m 40s; it was not asked.

## Decision

A workflow run seen in the rollup also waits for the checks its history says it has, and that it does not show yet:
the checks together are expected done at the latest of those too, while the run still has a check that is not done.

### Which checks a run waits for

- A check of the history belongs to a workflow run when its key starts with the run's workflow and `/`
  (`CheckSample.key`), and at least `CheckTimings.MIN_SAMPLES` of its samples are of the run's event. So a workflow
  that runs other jobs on a push than on a pull request waits for those of its own event: `snapshot` on a push to
  `main`, not on a release.
- It is missing when no check of that workflow run has that key. A check that is in the rollup, skipped too, is
  never missing. Not: no check of the commit, as two runs of one workflow can be on a commit, one for a push and one
  for a pull request, or a cancelled one next to the one that replaced it, and a check that shows in one is still
  missing in the other.
- A missing check is expected at the run's start plus its median `finished`, as a queued check is (ADR 32), with
  the run's start as ADR 32 has it, also for a re-run. Its samples are the same pool of events as the history keeps;
  only whether it is waited for goes by event.
- A missing check of which too little is known is not waited for: it makes the checks neither later nor `?`. Not:
  `?` for it, as a check that does not show cannot be told from one that will never come.
- A run waits for its missing checks only while one of its checks in the rollup is not done. A job is made once the
  jobs it needs are done, so when every check of the run is done and one is still missing, it is not coming: its
  `if:` was false, a job it needs failed, or it was taken out of the workflow. The minute the checks are read at
  covers a job made just after the read. Not: waiting until it shows up, which would leave the estimate overdue for
  good when it never does.
- Checks of an app other than Actions have no run, so none is missing; a workflow that has not started at all for the
  commit is not waited for either, as nothing tells it will.
- A workflow that waits to start shows as a queued check named after its workflow (ADR 35). It has no history of its
  own, so the checks together stay `done in ?`, also when the history knows the checks it will run: how long it waits
  is still not known.

### Shown

- A missing check counts only in when the checks together are expected done: the counts line of the pull request
  dialog and the checks popup (`· done in ~4m`), and the branches pane (`⏳ ~4m`). It is not listed as a row nor
  counted as running or queued: those are what GitHub shows. Not: a row `💤 snapshot  queued`, a state GitHub does
  not give it.
- A missing check is never overdue on its own: once the checks of its run are done, it is no longer waited for. So
  the checks together are overdue only by a check that shows.

### Where

- `CheckHistory` tells, for a workflow and an event, the keys of its checks with enough samples of that event.
- `Eta.of(all, history)` adds, per workflow run among `all` with a check not done, the missing ones, timed as a
  queued check of that run. `Eta.of(check, all, history)` does not change.

## Consequences

- On `main`, the estimate counts down to the end of `snapshot` from the start, without the jump when it shows up.
- A job taken out of a workflow is waited for, while its run's other checks are not done, until its samples are
  forgotten after 90 days (`CheckTimings.FORGET_AFTER`). So is a job whose `if:` is false only now and then, with
  enough samples of the event: the estimate is later than it ends up, never overdue.
- A job that needs one that failed is waited for until the run's other checks are done.
- A workflow named `<other workflow>/<something>` would have its checks taken for those of the other; GitHub allows
  it, it is not handled.
- Tested, in the pure estimate: a run waiting for a check that does not show yet, with its event, and not with
  another event; not waited for with too few samples, once the run's checks are all done, nor for a check that shows
  as skipped; a re-run; two workflow runs of one commit, each waiting for its own; two runs of one workflow, a check
  that shows in one still missing in the other; a workflow that waits to start, still not known. The dialog's counts
  line and the popup's, with no row for the missing check, and the estimate of a pending branch in the branches pane.

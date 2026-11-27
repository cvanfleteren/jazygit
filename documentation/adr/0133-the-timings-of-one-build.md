# 133. The timings of one build

Date: 2026-11-15

## Context

The builds show the jobs of a workflow's last build that is done on one time line, each hanging off the jobs it waits
for (ADR 131). An older build, like the one in the chart that took twice the median, cannot be looked at that way:
ADR 131 left "a cursor on a build, to see its jobs" for later, as the builds are text to scroll.

A build that is slow is slow for a reason the time line does not tell yet: it waited for a runner, a job took longer
than it does, or a job waited for a slow one. The check runs read for each build give when each job started and
ended, and the run when it was created; what each job waits for comes from the file of the workflow (ADR 131).

## Decision

- A build is picked to show on the time line in place of the last that is done:
  - a click on its row in the list, or on its column in the chart or the marks under it, picks it; a click on it
    again goes back to the last build. As a click on a tab does (ADR 131): no cursor in the text.
  - `p` in the builds lists the builds of the tab that show, the newest first, with how each ended, its commit, when,
    how long it took and its title, to filter and pick from; "the last build that is done" first, to go back.
  - Not: a cursor on the list, which `j` and `k` would have to move instead of scrolling the text, nor a view of a
    build of its own, which would hide the chart it was picked from.
- The build picked is marked `▸` in the list, with its title in bold, and the time line says `the build picked` where
  it says `the last build`. A mark at the end of the row is cut off with a long title.
- It stays picked while it is among the builds that show; when it no longer is, as with another tab, `n` (ADR 131) or
  every branch, the last build shows again, and `p` marks that one. It does not stay for another branch, whose builds
  are others.
- The file of its workflow is read at its commit, as that of a last build is (ADR 131), so that what each job waits
  for is as it was then.
- The time line says, for any build:
  - next to how long the build took and when it started, how long it waited for its first job to start: `queued 2s`.
    Not for a re-run, whose run keeps the first attempt's start.
  - for each job, after how long it took: how long it waited for a runner, from the end of the last job it needs, or
    from the start of the build without any, to its start; and how long it took against its median over the builds
    the jobs above show (the last 40), of its slowest shard, as those do. Under a line naming those columns.
  - a wait only where it can be known; else `–`. Not while the file of the workflow is not read, or could not be, nor
    for a job it does not match: from the start of the build, a job that waited for others would seem to have waited
    minutes for a runner. Nor for a job that needs one that did not run, as one skipped, whose end is not known: what
    each job needs (ADR 131) now keeps those by their key, and a job that needs none as needing an empty set, so that
    one that needs none is told apart from one whose needs are not known.
  - no waits for a re-run: it keeps the jobs of the attempt before that passed, with their times, and a wait after
    one of them would be the time between the attempts, as `queued` would be.
  - a job still running has `–` against its median: its time is not known yet.
  - the jobs that decided how long the build took, in bold: the last to end, the job of those it needs that ended
    last, and so on. Not while the file of the workflow is not read, or could not be: without what each job needs,
    the last job to end is all that is known. Nor while the build runs: nothing decided how long it takes yet.
- Not yet: the steps of a job (GraphQL's `CheckRun.steps`), which a picked build could read on its own, one call.

## Consequences

- A slow build in the chart is a click away from why: a wait for runners, a slower job, or a slow job others waited
  for.
- The time line is narrower by the two columns of numbers.
- Picking a build reads one more workflow file from git, in the background, once a view.

Amended by ADR 212: the run highlighted in the runs pane of the builds screen is the one on the time line; `p`, `n`
and `N` are gone.

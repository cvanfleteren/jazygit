# 130. The builds of a branch

Date: 2026-11-15

## Context

The branches pane shows how the checks on a branch's tip are doing, and `C` lists them one by one, with how long each
takes (ADR 32). Neither says how the builds before it went: whether the main branch has been failing now and then,
when it broke, or whether the build has been getting slower. ADR 32 keeps how long the checks took, but only of runs
that passed, without their commit, and pooling pull requests with pushes: it is there to estimate, not to look back.

GitHub tells, in GraphQL, the commits of a branch (`ref(qualifiedName:) { target { ... on Commit { history } } }`),
and for each its check runs (`statusCheckRollup`), each with its workflow run (ADR 32's `CheckFragment`). One call
for the last 100 commits of jazygit's main branch took 7 to 9 s; 25 took 2.3 s. GitHub ends a GraphQL call after 10 s,
and charges extra points for an hour when it does. A call for 100 commits costs 11 of the 5,000 points an hour.

On jazygit's main branch, 100 commits are 5 days, and 100 builds of its main workflow. Runs started by a comment or an
issue (`issue_comment`, `issues`) are on the main branch too, as GitHub runs those on its tip.

## Decision

- `B` on a local branch shows the builds of the branch it follows on GitHub in the content panel, in place of its log;
  `B` again shows the log again. It shows while that branch is highlighted, and the pane lists the local branches;
  another branch highlighted shows its log. Not: a popup, which would hide the branches, nor a screen of its own.
- A build is a workflow run on one of the commits: its check runs are its jobs. The runs of an event that does not
  build the code are left out: only `push`, `pull_request`, `pull_request_target`, `merge_group`, `workflow_dispatch`
  and `schedule` count. A re-run counts once, at its last attempt, as the rollup gives only that.
- The builds are read 25 commits a call, up to 100 commits, one call after the other, in the background: the first
  builds show after one call, and each call stays far from the 10 s. Not: 100 a call, which is close to it.
- They are read each time `B` shows them; not kept in a file. Not yet: keeping them, so that more than 100 commits can
  be looked back on, and reading only what is new.
- How a build ended: running while one of its jobs runs; else failed when one failed, timed out or could not start;
  else cancelled when one was; else passed. How long it took: from its run's start to its last job's end, as ADR 32
  times a run; a re-run from its first job's start, as its run keeps the first attempt's start.
- Each workflow and event has its own part, the one with the most builds first. A part shows:
  - a summary: how many passed, failed, were cancelled and run; the median and p90 of those that passed, and the last
    one against the median;
  - a chart of how long they took, one column a build, the newest on the right, as many as fit; a bar in the colour
    of how the build ended, the median as a dotted line, the scale up to twice the median so that a few slow builds
    do not flatten the others, and a build past it marked `▲`; under it, the days, and a mark for a build that did
    not pass;
  - each job: how it ended and how long it took over the last 40 builds, its median, its last time, and its trend, the
    median of the newer half of those against that of the older half; the jobs named `name (1/6)` are one job, of its
    slowest shard;
  - the jobs of its last build that is done, as bars on one time line, so that which job the build waits for shows;
  - the builds, the newest first: how it ended, the commit, when, how long, against the median, and the commit's title.
- Nothing is selected in it: it is text to scroll. Not yet: a cursor on a build, to see its jobs, open it in the
  browser or run it again.

## Consequences

- The content panel shows something read from GitHub for a branch, next to its log.
- What GitHub said of the checks of the last 100 commits is in memory while the builds show, and read anew each time.
- Left for later: the builds kept, more than 100, a build selected, and the builds of a remote branch.

# 131. The builds of a branch

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
- A branch that follows none, or one on a remote that is not on GitHub, says so, and nothing is read: gh reads the
  repository it finds for the working tree, where a branch of the same name would show builds that are not its own.
- A build is a workflow run on one of the commits: its check runs are its jobs. The runs of an event that does not
  build the code are left out: only `push`, `pull_request`, `pull_request_target`, `merge_group`, `workflow_dispatch`
  and `schedule` count. A re-run counts once, at its last attempt, as the rollup gives only that.
- The builds are read 25 commits a call, up to 100 commits, one call after the other, in the background: the first
  builds show after one call, and each call stays far from the 10 s. Not: 100 a call, which is close to it. A call
  that fails after some were read keeps those, and the title says no more could be read, so that fewer commits do not
  pass for the whole history.
- They are read each time `B` shows them; not kept in a file. Not yet: keeping them, so that more than 100 commits can
  be looked back on, and reading only what is new.
- How a build ended: running while one of its jobs runs; else failed when one failed, timed out or could not start;
  else cancelled when one was; else passed. How long it took: from its run's start to its last job's end, as ADR 32
  times a run; a re-run from its first job's start, as its run keeps the first attempt's start.
- The builds of one workflow and event show at a time, chosen from a line of tabs at the top. On a pull request's
  branch the tip runs several workflows (Build, Markdown, a review), and the commits it shares with the main branch
  ran that one's (Publish, 91 builds of jazygit's): stacked, each with its own chart, the branch's own came last.
  - A tab names the workflow, with its event when it runs for more than one, and how its last build ended. The tabs
    are in an order that stays as builds come in: those that ran on the newest commit with builds first, then the
    others, each by how many builds it has, then by name.
  - The tab shown is the one chosen, while it has builds; else, of those on the newest commit with builds, one whose
    last build failed, else the one with the most builds. The choice stays for another branch with the same workflow.
  - `[` and `]` in the builds show the tab before and after, around from the last to the first; `w` lists every
    workflow in a popup, to filter and pick from, for when there are many. A click on a tab shows it; one on a count
    of the tabs left out shows the first of those.
  - When the tabs do not fit, the line shows as many around the one shown as fit, and a count of those left out on
    either side, with how many of those failed: `‹ 4 ✗1 │ ✓ Build │ ✗ E2E │ 12 ›`. Not: a list of the workflows above
    the builds, a line each, nor a popup only, which hides how the others are doing.
  - Not: a list at the top to scroll to a workflow's part: the parts share nothing, and the list scrolls away.
- With only the commits not on the base listed (`b`, ADR 20), only their builds show, and the title says so as the
  log's does: a pull request's branch then shows only the builds of its own commits. `b` in the builds switches it
  too: it is the one switch of the logs, not one of the builds' own.
- A workflow that only runs for pull requests has a build or two on a branch: too few to tell how long it takes.
  `a` in the builds shows the runs of the workflow of the tab on every branch instead, and back:
  - GraphQL lists the runs of a workflow on every branch, from its id (`node(id:) { ... on Workflow { runs } }`),
    which the builds of the branch give (`workflowRun { workflow { id } }`). 50 runs with their jobs took 1.8 s; they
    are read 25 a call, up to 100, once a view, when their tab first shows. A run gives its branch, none for a branch
    deleted since, as most of a pull request's are once it is merged, and its pull request.
  - The tabs are then the workflows of the repository, those the branch ran first, in the same order, then the others
    that run, by name, each of any event. GraphQL has no list of them; REST does (`actions/workflows`), read once a
    view. A tab whose runs are not read yet shows `·` for how its last one ended.
  - The runs of the branch are marked `◆`, under the chart and in the list, which says what each ran for: its pull
    request, else its branch. The chart, the jobs and the last build are of every branch.
  - The builds of the branch say how long their workflow takes on every branch next to their own median: `every
    branch 4m18 (96 runs)`, the median of its runs of the same event that passed, read when the tab first shows.
    Even one build then has something to be held against.
  - Whether the builds show every branch stays for another branch, as the tab does. Not: a panel of the workflows of
    the repository of its own, apart from the branches: on every branch the tabs are those workflows already, and the
    branch's runs stay marked in them.
- `n` in the builds asks, in a popup, how many of the newest builds of a tab show: 10, 20, 50 or all those read. It
  takes what is read, nothing is read again; the title says `last 20 of 97 builds`, and the chart gives each build up
  to five columns, a bar and a column between them, so that a trend over a few builds shows. The summary, the chart,
  the jobs and the list follow it; the last build that is done and how long the workflow takes on every branch do
  not. Not: `+` and `-`, or `<` and `>`, which size the panes everywhere (ADR 92). The choice stays for another
  branch.
- The builds take the focus once `B` shows them, so that their keys work at once; `B` in them shows the log again,
  and gives the focus back to the branches.
- The builds of a workflow show:
  - a summary: how many passed, failed, were cancelled and run; the median and p90 of those that passed, and the last
    one against the median;
  - a chart of how long they took, one column a build, the newest on the right, as many as fit; a bar in the colour
    of how the build ended, the median as a dotted line, the scale up to twice the median so that a few slow builds
    do not flatten the others, and a build past it marked `▲`; under it, the days, and a mark for a build that did
    not pass;
  - each job: how it ended and how long it took over the last 40 builds, its median, its last time, and its trend, the
    median of the newer half of those against that of the older half; the jobs named `name (1/6)` are one job, of its
    slowest shard;
  - the jobs of its last build that is done, as bars on one time line, each hanging off the jobs it waits for, as a
    merge train (below);
  - the builds, the newest first: how it ended, the commit, when, how long, against the median, and the commit's title.
- The jobs of the last build that is done show which job waits for which, which GitHub does not tell: neither GraphQL
  nor REST gives a job's `needs`. The file of its workflow does:
  - GraphQL gives the path of the file of a workflow run (`workflowRun { file { path } }`). It is read at the commit
    of the build, with `git show <sha>:<path>`, from the repository on disk, as are the workflows of this repository
    its jobs call (`uses: ./.github/workflows/build.yml`). At the build's commit, not as it is now: a workflow
    changes, as jazygit's did twice in the last 100 commits of its main branch, and a build is drawn as it ran. A file
    of each workflow is read once a view, in the background, for its last build that is done.
  - A job of a workflow is matched by the name GitHub gives its run: its `name`, an expression in it matching
    anything (`acceptance (${{ matrix.shard }}/6)`), or its key, followed by the values of its matrix. A job of a
    called workflow is named after the job that calls it (`verify / package`); a job that needs a calling job waits
    for the last jobs of the workflow it calls. A job needs only what it waits for directly: `build-ok`, which needs
    `changes` and `package` as well as what needs them, hangs off `verify` and `acceptance` only.
  - A file that cannot be read (its commit not fetched, a workflow of another repository), that is no workflow, or a
    job that does not match, shows without lines: none drawn rather than one that is wrong. Not: guessing from when
    the jobs started and ended, which draws a job after another that only happened to end just before it.
  - The rows show a job after all it needs, as close under the last of those as can be, so that the lines stay short.
    A line drops from the end of a job and runs along the row of each job that needs it to an arrow before its bar; a
    bar starts two columns after the jobs it needs at the earliest, to leave room for that.
  - The shards of a job (`acceptance (1/6)` to `(6/6)`) are one row, `acceptance ×6`: solid until the first shard
    ends, shaded until the last one does, with the quickest and the slowest time.
- No build is selected in it: it is text to scroll. Not yet: a cursor on a build, to see its jobs, open it in the
  browser or run it again.

## Consequences

- The content panel shows something read from GitHub for a branch, next to its log.
- What GitHub said of the checks of the last 100 commits is in memory while the builds show, and read anew each time.
- The files of workflows are read from git at a build's commit, and a little of what GitHub Actions makes of them
  (names, matrices, calls) is redone to match the jobs.
- Left for later: the builds kept, more than 100, a build selected, and the builds of a remote branch.

Amended by ADR 138: a file of a workflow git does not have at the commit of a build is read from GitHub at that
commit.

Amended by ADR 142: `b` is gone; the builds of the commits on the base always show.

Amended by ADR 212: the builds have a screen of their own, the workflow first and the branch a filter; they no
longer show in the content panel next to a branch.

## Folded in: ADR 132 (2026-11-22)

`m` in the builds reads 100 more: of the branch, 100 more commits; with every branch shown (`a`), 100 more runs of the
workflow of the tab. They are read 25 a call, from where the last read ended, and show as they come in. After reading
failed, `m` reads again from where it failed, rather than everything again with `B`. The title says `m for more` while
more can be read and nothing is being read, and `m to try again` after a failure.

A call for 25 commits costs about 3 of the 5,000 points an hour, and 2 to 3 s. So more is read when asked for, not
once the list is scrolled to its end, and never everything at once: a branch of years has thousands of commits, read
in minutes, and kept in memory for as long as the builds show.

# 212. The builds get a screen of their own

Date: 2026-11-27

## Context

`B` on a local branch shows the builds of the branch it follows in the content panel, in place of its log (ADR 131,
133). The builds start from the branch: its last 100 commits are read, and the workflows they ran become tabs. `a`
turns that around and shows the runs of the tab's workflow on every branch, with the branch's own marked.

Looking back at how builds go is seldom about one branch. Whether the main branch has been failing, which workflow
got slower, what is running now: none of these is a question about the branch highlighted. On a branch, the badges of
the branches pane and `C` (ADR 32) already say how its last build did. So the view shows the branch first, where the
workflow is what stays the same from one look to the next, and the branch is a filter on it. ADR 131 turned down "a
panel of the workflows of the repository of its own"; GitHub's own Actions page is that: the workflows, the runs of
the one chosen on every branch, and the branch as a filter.

Since ADR 168 and 211 a feature brings a screen and its panes, as the pull requests screen is brought.

## Decision

- **GitHub's feature brings a screen of the builds**, as it brings that of the pull requests: `ctrl+b` switches
  between it and the repository screen (`ScreenMsg.Toggled`), and `B` on a local branch shows it filtered to that
  branch. `B` in the screen does nothing: the arrows and `ctrl+b` leave it. Not: the builds in the content panel next
  to a branch, which go, with `B` showing and hiding them.
- **Two panes and the content panel.** The left column has the workflows of the repository on top, about seven rows,
  and the runs of the one highlighted under it; the content panel shows the runs of that workflow. The width and the
  heights are kept under `ui.builds-width` and `ui.builds-heights`. The screen opens with the focus on the runs, the
  pane most used, rather than on its first: a screen can say which of its panes it starts with.
- **The workflows pane** lists the active workflows of the repository (`actions/workflows`, as ADR 131 reads them),
  each with how its last build on the default branch ended and how long it took; `·` when none of the commits read
  ran it, "PRs only" for one that runs only for pull requests. The order is ADR 131's: those that ran on the default
  branch's newest commit with builds first, each by how many builds it has, then by name. The first time it is shown,
  the one highlighted is, of those on the default branch's newest commit, one whose last build failed, else the one
  with the most builds; after that it stays as the user leaves it for as long as jazygit runs, in the model, not in
  the settings.
- **The runs pane** lists the runs of the workflow highlighted, the newest first: how each ended, what it ran for (its
  pull request, else its branch), its event, how long it took against the median, and how long ago. `b` filters them:
  every branch, the default branch, or one branch, "mine": the branch `B` was pressed on, else the checked-out one,
  read through the branch it follows on GitHub. A branch that follows none on GitHub leaves "mine" visible and
  disabled, saying why. On every branch, the runs of "mine" are marked `◆`, as ADR 131 marks them. The filter starts
  at every branch, and stays for as long as jazygit runs; `B` sets it to the branch. A filter without runs says why in
  the pane: "Markdown runs for pull requests only".
- **What is read for each filter.** Every branch: the runs of the workflow, from GraphQL's `Workflow.runs`, as `a` read
  them (ADR 131). The default branch and "mine": the builds on the branch's commits, as `B` read them (ADR 131), one
  read for every workflow, so that another workflow highlighted reads nothing. `m` reads more, of whatever the filter
  reads: runs, or commits, 25 a call (ADR 132).
- **The default branch is read whatever the filter**: its last 25 commits with their builds, from
  `defaultBranchRef`, which gives its name too, so that it need not be asked for. Those builds fill the workflows
  pane's column, the line of the default branch in the content panel and, filtered to it, the runs pane. Not: one
  REST call for the runs on it (`actions/runs?branch=`), which needs its name first, and gives no jobs.
- **The content panel** shows, of the runs the filter keeps, ADR 131's summary and the jobs of the last 40, then the
  chart, then the jobs of one run on one time line (ADR 133), and, always, on its own line, how the default branch did:
  `main ✗ failing since 2h · 14 of 15 passed`, as a pull request's runs fail for what is not done yet, and would
  make the rate of every branch say little of the code. The list of runs that ended it is now the runs pane.
- **The run highlighted is the run on the time line**, while the runs pane is in use; while the workflows pane is,
  the last that is done. A click on a bar of the chart highlights its run. `p`, `n` and `N` go, as do `[`, `]`, `w`
  and `a`: the panes and the filter are what they did.
- **`c` sizes the chart only**: the newest 10, 20 or 50 runs, or all those read, each with a wider bar, to see a recent
  trend. The median line and the scale stay those of every run the filter keeps, so that a recent slowdown shows
  against how long it took before, rather than becoming the new normal. The summary, the jobs and the runs pane show
  every run read. The choice stays for as long as jazygit runs.
- **What acts on the run highlighted**: `o` opens it in the browser set for the repository's account, as a pull
  request opens (ADR 88), with `gh run view --web`: the browser gh would choose may not be logged in to that account;
  `r` runs its failed jobs again (ADR 135); and the keys other features add to a failed build (ADR 161), as the
  agent's, take the run as a `FailedBuild`. `r` and the failed build's keys show, disabled, why they do not apply to a
  run that passed or still runs. Not yet: cancelling a run, or its logs in jazygit.
- **The title of the status** says after the name of the screen `✗` while the last build on the default branch of
  any workflow failed, and `●N` for the runs in progress on any branch, which REST counts
  (`actions/runs?status=in_progress`, its `total_count`), as the pull requests screen says how many are new.
- **It is read only while it is shown**: when it is, and again every minute, the workflows, the default branch and the
  runs in progress; while a run of the workflow highlighted runs, its newest page again as the checks of a branch are
  (ADR 34). Nothing is read while another screen shows.
- **A repository not on GitHub** shows the screen, saying why, as the pull requests screen does; a read that failed
  says so in its pane's title, with `m` to read again (ADR 132).
- **GitHub's only, in `features-github`.** Forgejo 17 has runs too (ADR 195), but a flow is written once in
  `features-forge` only when both forges have it (ADR 209); a Forgejo screen of builds takes an ADR of its own.

## Consequences

- The builds no longer take the place of a branch's log, and nothing is read for them while their screen is hidden.
- A third screen: `ctrl+p` and `ctrl+b` each switch between the repository and their screen; from one to the other
  goes through neither.
- The default branch's last 25 commits, the workflows and the runs in progress are read every minute while the screen
  shows: about 4 of GitHub's 5,000 points an hour per read (ADR 131), and two REST calls.
- The runs of one branch among those of every branch were few; the filter reads the branch's own history instead, so
  that "mine" shows all of them.
- `BuildsView` is the screen's state, a `Part` of `features-github` (ADR 157), and draws one workflow's runs as they
  are given; it no longer knows tabs or a branch.

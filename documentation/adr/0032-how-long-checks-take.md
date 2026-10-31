# 32. How long checks take, and when they are done

Date: 2026-10-25

## Context

The pull request dialog counts the checks on its last commit that failed, are running and passed, and lists the
failed and running ones by name (`PullRequestDialog.checks`). The branches pane shows a symbol for how the checks on
a branch's tip are doing, `⏳` while they run, for a branch without a pull request too (`BranchSegs.gitHub`). Neither
says how long a check has been running, whether it is still queued, or when they will all be done. On `main` there
is nowhere to put that at all.

GitHub tells, in GraphQL, for every check run its `status` (`QUEUED`, `IN_PROGRESS`, …), `startedAt` and
`completedAt`, empty while it is queued, and its workflow run, through `checkSuite.workflowRun`: `createdAt`,
`runAttempt`, `event` and the workflow's name. That is `null` for a check run made by an app other than GitHub
Actions (Codecov, SonarCloud, …): it has no workflow and no run start. A re-run keeps the `createdAt` of the first
attempt; there is no time an attempt started. A commit status (the older API) has no timing, and only its latest
state shows.

It does not tell how long a check usually takes; that has to be learned from runs that are done. Checks wait for each
other: on #140, `package` ran from 2s to 60s into its run and `acceptance`, which needs it, from 62s to 254s. Which
waits for which is not in the rollup. On `main`, pushes run workflows that pull requests never run.

## Decision

Each check that is not done shows how long it has been running, or that it is queued, and how long it is expected to
take still; the checks together show when they are expected to be done, or that this is not known.

### Estimating

- A check's history is how long after its workflow run started it finished, and how long it ran. Timing from the
  run covers the checks that wait for others, without knowing which wait for which.
- A queued check is expected at the run's start plus its median finish. A running one at the later of its own
  start plus its median duration, and the run's start plus its median finish.
- The checks together are expected done at the latest of those, of the checks not done. They are not known when
  one of those is not: a check with too little history, a queued check of an app other than Actions, or a commit
  status that is pending. Not: an estimate of what is known with a count of what is not; they are not done before
  the unknown one is.
- The median of the newest 20 runs that passed, given at least 3; under 3, the check is not known. Runs that
  failed, were cancelled or skipped stop early and are left out. Only first attempts: a re-run's `createdAt` is the
  first attempt's.
- A check run of an app other than Actions is keyed by `"<app slug>/<check>"` (`checkSuite.app.slug`), and has no run
  start: its history is only its duration, and a running one is expected at its own start plus its median duration.
  A queued one is not known, as it has no run start to count from.
- A re-run being watched starts at the earliest `startedAt` of its checks; its queued checks are not known while
  none started.
- Past its estimate, a check is overdue only after one more minute, the interval the checks are read at: one just
  done looks running until the next read. Until then it shows `<1m left`. Not: the slowest run seen as a second
  estimate; it says little.

### Learned locally, topped up from GitHub

- Every read that brings check timing adds the checks that passed to the history: the details of a pull request,
  the pending branches and the checks popup. A sample is `{ id, duration, finished, event, at }`: the check run's
  `databaseId`, its duration and when after its run's start it finished in seconds, the event of its run, and when
  it finished. A check of an app other than Actions has no run, so its sample has no `finished` and no `event`, and
  it is estimated from its duration alone.
- The first time a check that is not done is seen while the repository was last topped up more than a day ago, or
  never, one `gh api graphql` reads the checks of the last 20 merged pull requests and of the last 20 commits of the
  default branch: those cover the workflows that only run on a push. Not when a repository is opened, so one without
  running checks never costs a call. Estimates show `…` while it runs.
- When the top-up is tried, successful or not, the instant is kept, and that, not the age of the samples, gates the
  next one: a quiet repository, an empty result and a failing `gh` all wait the day out, so it is at most one a day
  per repository. The gate goes by the instant in memory (`GitHubState`, next to the history), set as the top-up
  starts; `topped-up` in the file only carries it across restarts and to other jazygits. A file that cannot be
  written then does not bring the top-up back every minute.
- Not: only learning locally, as a new developer would only know the pull requests they looked at, and history
  would go stale on a repository they rarely watch. Not: only asking GitHub, every hour, without a file.
- The event is kept but not part of the key: a workflow that runs on both pushes and pull requests pools them, and
  they can be told apart later.

### Kept

- In `check-timings.toml`, next to `state.toml`: what jazygit keeps by itself, but it changes as checks finish and
  grows with repositories and checks, so apart from the recent repositories, and it can be deleted alone. A table
  per repository (`owner/name`) holds when it was last topped up, and a sub-table `checks` holds the samples of each
  check by its key, `"<workflow>/<check>"` or `"<app slug>/<check>"`, so no check can clash with `topped-up`:

  ```toml
  [repo."owner/name"]
  topped-up = 2026-10-25T10:00:00Z

  [repo."owner/name".checks]
  "Build/acceptance" = [{ id = 121, duration = 192, finished = 254, event = "pull_request", at = 2026-10-24T22:22:37Z }]
  "codecov/patch" = [{ id = 133, duration = 41, at = 2026-10-24T22:21:02Z }]
  ```

  Durations are in seconds. TOML has no null, so a sample of an app other than Actions leaves out `finished` and
  `event`.
- `RecordCheckTimings` writes only the new samples: under `TomlFile.change`'s lock it reads the file, adds them,
  drops those already there by id, and keeps the newest 20 per check, dropping checks not seen for 90 days. Never the
  history in memory over the file, so two jazygits do not lose each other's samples. What it wrote for the
  repository replaces `GitHubState.checkHistory`, so this one learns what the other wrote.
- Read into `GitHubState.checkHistory` when a repository is opened, with `topped-up`. A failed write raises no
  error; the samples, and when it was topped up, stay in memory. A file that cannot be read is empty, and the next
  write writes it anew.
- The repository is the one the read was made against, as `gh` fills in `{owner}/{repo}`; a fork is not special.
- Not: SQLite. It handles writes from several processes itself, but the history is bounded and small, it is written
  a few times per CI run, and a native library would complicate the native image. It fits once the history is kept
  unbounded, or asked more than a median per check.

### Read

- The details of a pull request ask for `status startedAt completedAt databaseId` of each check run and its
  `checkSuite { app { slug } workflowRun { createdAt runAttempt event workflow { name } } }`.
  `PullRequestDetails.Check` gets its workflow or app, its phase (queued, running or done) and these times. `Checks`
  stays as it is, so the lists do not change.
- The branches are read as before, one state per branch. After that, when any is pending, one query reads the checks
  with their timing of at most 10 of them, by alias: the checked-out branch, the default branch, then the others by
  name. No call when none is pending.
- At the minute they are read now: the dialog (ADR 19), the branches and the popup.

### Shown

Times in whole minutes, `<1m` under one: the clock moves in steps of 5s (ADR 21) and GitHub's times are not on it,
so seconds would stutter.

- **The pull request dialog**: the counts line ends with `· done in ~3m`, `· done in ?` or `· taking longer than
  usual`, nothing once all are done. Each check not done, after the failed ones, running first by when it is expected
  done, then queued:

  ```
  ⏳ acceptance     running 2m · ~2m left
  ⏳ claude-review  running 3m · overdue
  ⏳ verify         running <1m · ? left
  💤 package        queued · ~4m left
  ```

  `💤` is new, and the rest of its line is dimmed: a queued check is not running yet. A colour emoji ignores dimming
  in most terminals, so the text carries it. Not `⏸`, which most terminals show one column wide and some two, so
  the names after it would not line up. A commit status shows `running` and nothing more. The dialog keeps its
  height.
- **The branches pane**: after the running symbol, `main ⏳ ~4m`, `feat/x #146 ⏳ ~3m`, `⏳ ?`, `⏳ late`; on every
  branch of which the timing was read.
- **The checks popup**: `c` on a branch whose tip has checks, with or without a pull request. It lists every check:
  those not done as in the dialog, with its counts line; those done with how long they took, `✅ verify  2m`,
  `❌ acceptance  failed after 3m`. It reads its branch's checks itself, as they may all be done: when it opens,
  every minute while it is the open popup, and with `R`, with a spinner in its title and stale when that fails, as
  ADR 19. `o` already starts a pull request on a branch without one.
- Not on the pull requests screen: its lists would read the timing of up to 100 pull requests every minute. Its
  local branches show it in the branches pane, and `o` opens the dialog.

### Tuned in one place

20 samples, at least 3, a top-up after a day, dropped after 90 days, at most 10 pending branches and a minute's grace
are constants next to each other in one class, not settings, so they can become settings later.

## Consequences

- Built in four steps: the dialog with what is shared (the timing read, the history and its file, the estimate),
  then the branches pane, then the checks popup, then the check runs of apps other than Actions. Until that last
  step those are neither learned nor estimated: their key, sample and estimate wait for `checkSuite.app.slug`, and a
  commit with one shows `done in ?` while it runs.
- One more `gh api graphql` a minute while a branch is pending, and at most one a day per repository to top up.
- A new repository, or a new workflow, shows `?` until it has 3 runs that passed, unless the top-up finds them.
- A check that varies a lot, like `claude-review` (16s to 174s), gets an estimate that is often off, and overdue
  when it is only slow.
- The estimate is a pure function, unit tested: queued, running, overdue and its grace, too little history, a
  commit status, a check of an app other than Actions (running and queued), a re-run, all done. The file is tested
  for merging, duplicates, pruning, two writers, a broken file, a sample without `finished` and `event`, and
  `topped-up` next to the checks. The update is tested for the top-up's day, also after a failed or empty top-up and
  when the file cannot be written. The parsing of the new fields and the top-up, the dialog's lines, the branches
  pane and the popup are tested too. The acceptance tests run without GitHub, so none shows an estimate.

Amended by ADR 34: the checks popup and the pull request dialog show how long a check runs to 5s under an hour, its
estimate to 5s under 5m, and `due` past it; they read every 10s while a check is not done. The branches pane keeps
whole minutes. Every time of an hour or more is written `1h 5m`, or `1h`, in the branches pane too.

Amended by ADR 36: a workflow run also waits for the checks its history says it has and that GitHub has not made
yet, like a job that `needs` others, while one of its checks is not done.

Amended by ADR 37: the checks popup shows, under its counts, when the checks started, ended and how long they took
together.

Amended by ADR 63: how long the checks took is kept in a file per repository, `repos/<owner>/<name>/check-timings.toml`.

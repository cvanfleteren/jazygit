# 34. Check times to the second in the dialogs

Date: 2026-10-26

## Context

ADR 32 shows how long checks run and are expected to take still in whole minutes, `<1m` under one, because the
clock moved in steps of 5s (ADR 21) and GitHub's times are not on it, so seconds would stutter. ADR 33 replaced that
clock: the model is told the exact time with every message, and a clock wakes it up every second. Seconds rounded to
5s now move on by one step every 5 clocks, without stutter.

Checks often take a few minutes. In whole minutes, a check that runs shows `running 2m` for a minute, and one that is
nearly done `<1m left` for a minute; while watching them, nothing seems to happen.

The checks popup (ADR 32) and the pull request dialog (ADR 19) list the checks one by one with the same rows
(`CheckRows`), and read them again every minute while they are open, with a spinner in their title. The branches pane
shows the same estimate, short, next to a branch's symbol. A check past its estimate is overdue only after a minute
more (`CheckTimings.GRACE`), the interval the checks are read at; until then it shows `<1m left`.

## Decision

The dialogs that list checks show their times to 5s while they are short, and read the checks every 10s while one
is not done. The branches pane keeps whole minutes, for its room. How the times are estimated does not change: only
how they are drawn, and how often the dialogs read.

### Shown

- In the checks popup and the pull request dialog, a time that is not estimated is shown to 5s, and an estimate to
  5s only while it is under 5m.
- How long a check runs or took is known exactly, so it is shown to 5s at any length: `45s`, `2m`, `7m 40s`,
  `1h 5m 40s`. It is rounded down, as it was, like a clock that counts up: a check that has run 7m 41s shows
  `running 7m 40s`, never a time it has not run yet. Under 5s it shows `<5s`, as the branches pane shows `<1m`:
  `running <5s`.
- How long it is expected to take still is an estimate, rounded to the nearest step, as it was: to 5s under 5m,
  `~45s`, `~2m 5s`; from 5m on to the nearest minute, `~7m`. Not: seconds on a long estimate, which claims more than
  is known. Not: an estimate in whole minutes also under 5m: the `~` says it is rough, and both times moving on
  together is what shows that something happens.
- Not padded: `2m 5s`, not `2m 05s`, as a duration and not a clock. The column the checks popup lines the times up
  in can grow by a column once a minute.
- A check whose estimate rounds to no time left, or is past, but which is not overdue yet, shows `due`:
  `running 2m 30s · due`, and the checks together `done any moment`. So `~5s left` is followed by `due`, never
  `~0s left`. Not: `<1m left`, which comes after `~5s left` and would count up again; nor `~5s left`, which
  would stand still for a minute.
- The branches pane keeps whole minutes, `~4m`, `<1m` and `late`, as ADR 32 has it: it has no room for seconds, and
  is read once a minute.
- The dialogs and the branches pane keep one estimate and one `GRACE`: they only round it differently, and agree on
  when a check is overdue.

### Read

- The checks popup and the pull request dialog are each told every 10s to read again (`ChecksMsg.Tick`,
  `PullRequestMsg.Tick`), not every minute. A tick reads only when no read runs, and then when a check is not done,
  or when the last read started a minute ago or more (`model.now()`): a check just done shows within 10s, and once
  all are done the dialog is read every minute, for a re-run, a push, or a review. A read that hangs gets no second
  one next to it. After a read failed, the checks shown are stale and say nothing of what still runs, so the dialog
  reads every minute, as ADR 19 has a failing tick do, until a read succeeds: not 360 failing calls an hour while
  offline, or more while GraphQL refuses for its rate. Not: a second timer of 10s next to the one of a
  minute, which would leave the minute as a rule no test could see.
- The minute is counted with half a tick to spare: a tick reads once `now - readAt` is 55s or more. The time a
  message is handled is exact (ADR 33), so the tick a minute after the one that read can come a few ms short of the
  minute, and the read would slip to the tick after, 70s. Not: counting ticks since the last read, which is one more
  thing to keep.
- Each popup keeps when its last read started (`readAt`): set when it opens and whenever it reads again, by `R` too.
- The pull request dialog reads all its details as before, two `gh api graphql` calls, as the checks are part of
  them. Not: its checks alone every 10s and the rest every minute, a second read of the same checks.
- At most 360 calls an hour for the checks popup and 720 for the pull request dialog, while it is open and a check
  is not done, of the 5000 points an hour of GraphQL a user has.

### Reading quietly

- A read the dialog starts by itself shows no spinner in its title; one asked for with `R` does, as does the first
  read, which shows a spinner in place of the checks. The bottom line shows every read that takes 300 ms or more,
  as it does every command (ADR 33). Not: a spinner in the title every 10s.
- Each popup keeps how it reads in place of `refreshing`: not, by itself, or asked for. When a read by itself
  fails, the title says the details are stale as before.
- `R` while the dialog reads by itself does not start a second read: the read that runs becomes one asked for, and
  the spinner shows. Not: `R` disabled for a second every 10s, with nothing to say why.
- Turning the pull request into a draft, or marking it ready, stays possible while the dialog reads by itself. The
  read that runs then may have started before the change, and come back with how it was before (ADR 19), before or
  after the change is done (`DraftSet`). So choosing the change marks that read to be dropped: a fourth way of
  reading, by itself but dropped (`Reading.DROPPED`). That the read of the pull request is to be dropped is also
  kept apart from the dialog, by its number (`GitHubState`), so it is dropped when the dialog was closed meanwhile, or
  opened again. When it comes back, all of it is dropped: the dialog, and the
  pull request the branches pane lists (`GitHubState.withRead`), keep how they were, and nothing is learned from its
  checks. A dropped read that fails is dropped too, and does not mark the details stale: it says nothing of the pull
  request as it is since the change. Either way the dialog no longer reads, so ticks read again. The change shows
  once it is done, and a later tick reads it: the next one while a check is not done, else within a minute of when
  the dropped read started (`readAt`). Not: reading at once after a dropped read, which would take `readAt` set
  back, for a change that already shows. While it is read as asked, or first read,
  the change waits as before. Not: the change disabled for a second every 10s, with nothing to say why; nor taking
  what such a read brings, which would show the change undone; nor telling the read apart by the branch being busy,
  which it no longer is when the read comes back after the change.
- `R` waits while a read to be dropped runs, as while the branch is busy: that read ends soon after the change, and
  the next tick reads again. Not: `R` turning it into a read asked for, which would show a spinner and then drop
  what it brings.

## Consequences

- Built in two steps: first the times, `due` and the minutes kept in the branches pane; then the reads every 10s,
  `readAt`, and the quiet reads.
- Up to six times as many calls while a dialog is open on checks that are not done.
- The bottom line shows a read of the checks for about a second every 10s while one is not done.
- Tested: how long down to 5s at any length, and what is left to the nearest, to 5s and to the minute from 5m on,
  `<5s`, `due` past `~5s left` and `done any moment`, the minutes kept in the branches pane; when a tick reads, for
  checks that are and are not done, and a tick that comes a few ms short of the minute, and every minute only after a
  read failed; that only `R` and the first read show the spinner, `R` during a read by itself, and that a read by
  itself that fails marks the details stale; a draft changed while the dialog reads by itself, with that read coming
  back before and after the change is done, dropped from the dialog and the branches pane alike, also once the dialog
  was closed, and a dropped read that fails, which marks nothing stale and lets ticks read again; `R` while a read to
  be dropped runs.

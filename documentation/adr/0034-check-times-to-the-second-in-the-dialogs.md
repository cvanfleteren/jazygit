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

- In the checks popup and the pull request dialog, every time, how long a check runs or took and how long it is
  expected to take still, is rounded to the nearest 5s: `45s`, `2m 5s`, `2m`. A time that rounds to 5m or more is
  rounded to the nearest minute, as it was: `7m`, `1h 5m`. Not: seconds on every time, which a long one does not
  need. Not: seconds only on how long a check runs, as that is exact and an estimate is not: the `~` says the
  estimate is rough, and both moving on together is what shows that something happens.
- Not padded: `2m 5s`, not `2m 05s`, as a duration and not a clock. The column the checks popup lines the times up
  in can grow by a column once a minute.
- A check past its estimate but not overdue yet shows `due`: `running 2m 30s · due`, and the checks together
  `done any moment`. Not: `<1m left`, which comes after `~5s left` and would count up again; nor `~5s left`, which
  would stand still for a minute.
- The branches pane keeps whole minutes, `~4m`, `<1m` and `late`, as ADR 32 has it: it has no room for seconds, and
  is read once a minute.
- The dialogs and the branches pane keep one estimate and one `GRACE`: they only round it differently, and agree on
  when a check is overdue.

### Read

- The checks popup and the pull request dialog are each told every 10s to read again (`ChecksMsg.Tick`,
  `PullRequestMsg.Tick`), not every minute. A tick reads when no read runs and a check is not done, or when the last
  read started a minute or more ago (`model.now()`): a check just done shows within 10s, and once all are done the
  dialog is read every minute, for a re-run, a push, or a review. Not: a second timer of 10s next to the one of a
  minute, which would leave the minute as a rule no test could see.
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

## Consequences

- Built in two steps: first the times, `due` and the minutes kept in the branches pane; then the reads every 10s,
  `readAt`, and the quiet reads.
- Up to six times as many calls while a dialog is open on checks that are not done.
- The bottom line shows a read of the checks for about a second every 10s while one is not done.
- Tested: the rounding to 5s and to minutes, `due` and `done any moment`, the minutes kept in the branches pane;
  when a tick reads, for checks that are and are not done and around the minute; that only `R` and the first read
  show the spinner, and that a read by itself that fails marks the details stale.

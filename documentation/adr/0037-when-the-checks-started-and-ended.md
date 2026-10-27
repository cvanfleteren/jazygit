# 37. When the checks started and ended

Date: 2026-10-27

## Context

The checks popup (ADR 32) shows how many of a commit's checks failed, run and passed, when they are expected to be
done, and then each check, with how long it runs or took. It does not say when the checks started, when they ended,
nor how long they took together: only each check alone. Checks wait for each other, so how long they took together is
not the sum of them, nor the longest.

Every check run of GitHub Actions knows when its workflow run was created (`runCreatedAt`, of a re-run the first
attempt's) and when it started and was done (`startedAt`, `completedAt`). The estimate counts from the run's start
(ADR 32): its `runCreatedAt`, or for a re-run the earliest `startedAt` of its checks. A commit status has no times.

ADR 34 shows how long a check runs or took to 5s under an hour, rounded down, and `<5s` under 5s. Since ADR 33 the
model is told the exact time every second, so a time counted to the second moves on without stutter.

## Decision

The checks popup shows, on a line of its own under the counts, when the checks started, when they ended once all are
done, and how long they took together. What is measured is shown to the second, in the checks popup and in the pull
request dialog; what is estimated is rounded as before.

### Shown

- In the checks popup only, on the line after the counts, before the blank line:

  ```
  ❌ 1 failed · ✅ 6 passed
  started 14:02:10 · ended 14:06:41 · took 4m 31s

  ❌ lint                                         failed after 1m 32s
  ```

  While a check is not done: `started 14:02:10 · running 4m 31s`, counting up to the model's now. The labels are
  dimmed, the times not. Not in the pull request dialog, which has little room and is one `c` away from the popup.
  Not: at the end of the counts line, which can then be wider than the 76 columns inside the border.
- The dialog is one line higher, so it still lists 16 checks.
- The line is not shown while the checks are first read, when reading them failed, when there are none, nor when none
  of them has a time, only commit statuses. When a read again fails, it shows what the last read told, as the checks
  do.

### Times

- The start is the one the estimate counts from (ADR 32): the earliest of the commit's workflow runs, a run by its
  `runCreatedAt`, a re-run by the earliest `startedAt` of its checks, and a check of an app other than Actions by its
  `startedAt`. So the queueing before the first job counts, and how long it ran and `done in ~1m` add up. Commit
  statuses are left out. Not: the earliest `startedAt` of any check, which leaves the queueing out. Until the check
  runs of apps other than Actions are read with their timing (ADR 32's last step), they have none, and count as a
  commit status does.
- The end is the latest `completedAt`, once every check is done, passed or failed. While one is not done there is no
  end. Not: the expected end as a time of day, `~14:06:40`, which says again what `done in ~1m 5s` says.
- How long they took is from the start to the end, or to now while one is not done: the time on the clock, not the
  sum of the checks.
- A time of day is written `HH:mm:ss` in the local zone, and `yyyy-MM-dd HH:mm:ss` when it is not today: the start and
  the end each on its own. GitHub tells them to the second.

### To the second

- Every time that is measured, how long the checks together or a check runs or took, is shown to the second under an
  hour, rounded down, in the checks popup and the pull request dialog: `running 2m 31s`, `failed after 1m 32s`,
  `✅ verify  2m 3s`, and `0s`, `1s` where ADR 34 showed `<5s`. From an hour on, to the minute as before: `1h 5m`.
  Not: seconds also from an hour on, `1h 5m 12s`, which tell little and make the column the times line up in three
  columns wider, taken from the names.
- What is estimated, how long a check is expected to take still and when they are done together, stays rounded as
  ADR 34 has it: `~1m 5s left`, to 5s under 5m and to the minute from 5m on. It is a guess, and the `~` says so.
- The column the times line up in is as wide as the time of a check that runs can get, now
  `running 59m 59s · ~4m 55s left`, so the names still do not move as the seconds do.
- The branches pane keeps whole minutes (ADR 34).

## Consequences

- Built in one step.
- The time of a check that runs changes every second, not every 5s.
- Tested: the start of a run, of a re-run, of two workflow runs and of a check of an app other than Actions; commit
  statuses left out, and no line when only those are there; the end once all are done, a failed one too, and none
  while one is not; how long they took, counting up while one runs; a start or end of another day with its date; no
  line while loading, when the read failed and without checks, and the line from the last read when a read again
  fails; the dialog's height; times to the second under an hour, `0s`, `59m 59s` then `1h`, in the rows of both
  dialogs; estimates still to 5s; and the column of the times keeping its width while a check runs.

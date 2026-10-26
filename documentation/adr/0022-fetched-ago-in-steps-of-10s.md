# 22. How long ago the remotes were fetched, in steps of 10s

Date: 2026-10-22

## Context

ADR 21 shows every age under a minute as "now", or "just now" in a sentence, and tells the model the time every 5s.
That suits the ages in the lists. It does not suit the line that says how long ago the remotes were fetched from,
or the pull requests read: they are read every minute, so it said "fetched just now" nearly all the time, and only
told anything once a fetch had failed for a while.

Counting it in seconds again would bring back the stutter of ADR 21 in a smaller form. The time is told every 5s,
and when the clock runs a little late just as a step of the age turns, that step shows for one clock longer and
the next for one clock shorter. With the fetch timed by `Instant.now()` in the command, when within those 5s it
was fetched is anywhere, so now and then a fetch lands where that happens.

## Decision

- The line counts how long ago in steps of 10s under a minute: "fetched just now" under 10s, then "fetched 10s
  ago" up to "fetched 50s ago", and from a minute on as ADR 21 (`TimeText.readAgo`). The ages in the lists, and the
  creation of a pull request, stay as ADR 21 has them.
- A fetch, and a read of the pull requests, are timed by the model's clock rather than in the command:
  `FetchMsg.Fetched`, `PrOverviewMsg.RepositoryLoaded` and `PrOverviewMsg.MineLoaded` no longer carry a time, and
  the update records `Model.now`. When it was read and the time it is now are then both clocks, so how long ago is
  a whole number of 5s, give or take how late the clock ran; `readAgo` rounds it to that before it counts. Each step
  then shows for exactly two clocks, however late one runs.
- `CLOCK_INTERVAL` moves to `TimeText`, next to the spinner's frame, as `readAgo` counts in it.

## Consequences

- The line counts 10s every 10s under a minute, without stutter.
- A fetch is timed up to 5s early: when the clock last ran before it was done. Nothing goes by that time but the line.
- The commands that read the remotes and the pull requests no longer ask for the time, and their tests give the
  time on the model (`withNow`).
- Right after switching to another repository, the time is told once off the 5s beat of the clock, until the next
  clock. A fetch done in those 5s is timed off the beat too, and can stutter until the next fetch, a minute on.

The rounding to whole clocks is replaced by ADR 33: the model is told the exact time, and `readAgo` rounds down to
steps of 10s from it.

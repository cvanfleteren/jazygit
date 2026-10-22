# 21. Ages in minutes, told by a clock apart from the refresh

Date: 2026-10-22

## Context

The model knows the time only as it was told last (`Model.now`), and the ages it shows are told from it: how long
ago a branch, a remote branch or a pull request was changed, a pull request created or commented on, and the
remotes fetched from or the pull requests read. One message did two things: every `REFRESH_INTERVAL`, `Msg.Tick`
both told the time and read the repository again.

ADR 14 had that tick every 500ms. ADR 15 made it 750ms, to run git less often, and the acceptance tests set it to
100ms (`jazygit.refreshInterval`), so that they see sooner what a test wrote. Under a minute, an age showed in
seconds, and at 750ms they no longer counted evenly: told at 0, 0.75, 1.5, 2.25 and 3s, an age shows 0, 0, 1, 2, 3.
One in four seconds showed for 1.5s and the other three for 0.75s, which looks like a clock that stutters.

Telling the time at an interval that divides a second only mostly fixes that: the clock runs a little late now and
then, and when it is told just before a second turns, that second shows for 0.5s and the next for 1.5s.

The seconds tell little. The remotes are fetched and the pull requests read every minute, so "fetched 12s ago"
mostly counts from 0 to 59 and starts over; what it is there for is to show when that stops, and it then shows
minutes, next to "last fetch failed". A branch committed to 37s ago says no more than one committed to just now.

## Decision

- An age under a minute shows as "now" in the lists, and as "just now" in a sentence ("fetched just now"), rather
  than in seconds (`TimeText.age`, `TimeText.ago`). Minutes, hours and days show as before.
- `Msg.Clock(now)` tells the model the time every 5s (`CLOCK_INTERVAL`), and does nothing else: no git, no
  commands. An age then turns at most 5s after it should, and no one sees when within a minute it does.
- `Msg.Tick` no longer carries the time. It only reads the repository again, every `REFRESH_INTERVAL`, which stays
  750ms and configurable: how often to run git is a matter of cost, not of how the ages look.
- What a tick decides by the time, like whether the worktrees were read long enough ago (ADR 15), uses the time of
  the last clock, at most 5s old.
- At the start, the app tells the time once at once, as the first clock is 5s off.

## Consequences

- No age changes more than once a minute, and none stutters, whatever the refresh interval is set to.
- An idle app draws a frame every 5s, rather than 2 a second as ADR 14 counted on: what changes the model there is
  the time. The clock itself runs no git.
- A minute that turns shows up to 5s late, and the time that a tick goes by is up to 5s old. Which worktrees have
  changes (ADR 15's `WORKTREE_CHANGES_INTERVAL`, 3s) is read again every 5s: both when they were read and when a
  tick looks are told by the clock, which moves in steps of 5s.
- Tests that need a tick at some time set it on the model (`withNow`) rather than in the tick.

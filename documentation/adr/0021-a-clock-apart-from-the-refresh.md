# 21. A clock apart from the refresh

Date: 2026-10-22

## Context

The model knows the time only as it was told last (`Model.now`), and the ages it shows, how long ago a branch was
committed to or the remotes were fetched from, are told from it in whole seconds. One message did two things: every
`REFRESH_INTERVAL`, `Msg.Tick` both told the time and read the repository again.

ADR 14 had that tick every 500ms. ADR 15 made it 750ms, to run git less often, and the acceptance tests set it to
100ms (`jazygit.refreshInterval`), so that they see sooner what a test wrote. At 750ms the ages no longer counted evenly:
told at 0, 0.75, 1.5, 2.25 and 3s, an age shows 0, 0, 1, 2, 3. No second is skipped, but one in four shows for 1.5s
and the other three for 0.75s, which looks like a clock that stutters.

An age in whole seconds only shows each second for as long when the time is told at an interval that divides a
second. At 1000ms, the time is told about when the ages turn: a tick that comes a little late or early, as the
render thread is busy or the scheduler drifts, shows one second for 2s and skips the next.

## Decision

- `Msg.Clock(now)` tells the model the time every 500ms (`CLOCK_INTERVAL`), and does nothing else: no git, no
  commands. Two per second, so a late or early one cannot skip a second.
- `Msg.Tick` no longer carries the time. It only reads the repository again, every `REFRESH_INTERVAL`, which stays
  750ms and configurable: how often to run git is a matter of cost, not of how the ages look.
- What a tick decides by the time, like whether the worktrees were read long enough ago (ADR 15), uses the time of
  the last clock, at most 500ms old.
- At the start, the app tells the time once at once, as the first clock is 500ms off; the loads that the first tick
  would start are running already.

## Consequences

- Each age shows each second for 1s, whatever the refresh interval is set to.
- An idle app draws 2 frames a second again, as ADR 14 counted on, rather than 1.3: the time changes the model, and
  so asks for a frame. The clock itself costs no git process.
- Tests that need a tick at some time set it on the model (`withNow`) rather than in the tick.

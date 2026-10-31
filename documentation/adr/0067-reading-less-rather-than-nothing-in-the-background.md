# 67. Reading less, rather than nothing, in the background

Date: 2026-10-31

## Context

ADR 58 (`0058-less-reading-in-the-background.md`, decision 4) stopped the timers that read the repository, the
remotes or GitHub while the terminal does not have the focus: their messages (`Msg.Periodic`) were kept, and each
handled once when the focus came back. That holds while nobody looks, but a terminal without the focus is often still
seen: a pane next to the editor, a window on a second screen. There the app showed a status, branches and pull
requests that were as old as the moment the focus went, however long ago.

And one thing went on as with the focus: the clock. `Msg.Clock` comes every second, and `Model.drawn()` takes the time
to the second, so an idle app drew a frame a second, also while away.

## Decision

1. **While away, each message of a timer is handled once it waited a while, and kept otherwise.**
   `Msg.Periodic.whileAway()` tells how long: the tick, which reads the status, every 15 s instead of every second (4
   git processes a minute instead of 60); the fetch and the pull requests overview every 5 minutes instead of every
   minute; the dialogs of a pull request and of checks every minute instead of every 10 s while a check runs.
   `Jobs.awayAt` holds, per message, when it was last handled while away; the first one that comes after the focus
   went starts the wait, so nothing is read the moment the focus goes. A message that is not due yet is kept in
   `Jobs.missed` as before, and one that is handled leaves it, so once back, only what was asked for since it was last
   read is read at once. Losing the focus again while away starts no wait anew.
2. **While away, the time shown moves on every 5 s.** `Model.drawn()` takes the time to `Model.AWAY_CLOCK`, 5 s, while
   away, so the clock still comes every second but a frame is drawn only every 5 s. The ages shown are in minutes, and
   how long ago the remotes were fetched in steps of 10 s, so they are at most 5 s late. `Jobs.missed` and
   `Jobs.awayAt` are not drawn either, so keeping a message draws no frame.

The timers themselves keep their intervals: what is slower while away is decided in `Update.update`, from the time
the model is told, which a test sets.

## Consequences

- A new timer that reads something implements `Msg.Periodic` and tells its `whileAway()`.
- The backoff of a read that failed (ADR 31) counts ticks, so it waits up to 15 times longer while away.
- A merge that is being checked on is, as before, checked on every 2 s; spinners turn while a command shows.
- A terminal that does not report the focus never says the app is away, and nothing changes there.

Amends ADR 58 (`0058-less-reading-in-the-background.md`).

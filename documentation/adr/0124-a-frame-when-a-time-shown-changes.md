# 124. A frame when a time shown changes

Date: 2026-11-14

## Context

`TimerMsg.Clock` comes every second (ADR 33), every message tells the model the time it is (`Update.step`), and
`Model.drawn()`, which tells whether a message calls for a frame (ADR 14), takes that time to the second (ADR 33; ADR
67: to 5 s while away). So an idle app draws a frame a second, and that frame almost never changes a cell: measured on a
repository with a diff, a Markdown and a JSON file open, every idle frame of the files pane, a diff and both rendered
versions changed nothing, at 8 to 11 ms each.

What the screen shows of the time changes far less often, and each on its own beat: an age once a minute, counted from
when it happened, how long ago the remotes were fetched in steps of 10 s from the fetch (ADR 22), how long a check runs
to the second while the checks or a pull request dialog shows it. Taking the time to a coarser step in `drawn()` would
show those late, or need a list of what shows seconds, kept in step with the views by hand.

The app follows the Elm architecture (`ARCHITECTURE.md`). Elm asks the model what it wants to be woken for,
`subscriptions : Model -> Sub Msg`, rather than waking it on a fixed beat. `Spinners.shown(model, now)` does this
already for the frames of a spinner: each view that draws one tells from the model whether it does.

## Decision

1. **A frame comes when a time shown changes, not every second.** `Model.drawn()` no longer holds the time, and the
   timer of `TimerMsg.Clock` is gone. After each frame the app asks when what the model shows of the time changes
   next, schedules one `TimerMsg.Clock` for that moment, and draws a frame when it comes. A frame drawn for any other
   reason already shows the time it is drawn at: every message tells the model the time.
2. **A time shown is a clock: what it reads at an instant.** `Clock` is a function from an instant to the text a view
   draws, made of the very function the view draws it with (`TimeText.age`, `TimeText.readAgo`,
   `CheckTimesText.duration`, ...). When it changes next is found by trying instants after now, doubling the step and
   then halving it, to the millisecond, up to a day ahead. Not: a rule beside each format of when it changes next,
   which would drift from the format.
3. **Each view that shows a time tells its clocks from the model**, beside where it draws them, as `showsSpinner` does:
   the fetch line, the ages and check times of the local and remote branches, the ages of the pull requests, the
   review threads in a diff, and the checks, pull request and comments dialogs. `ShownClocks.next(model, now)` takes
   the first change of all of them. A view tells the clocks of what it could show, not only what is scrolled into
   sight: the widgets keep where they are scrolled (ADR 2), and an age that changes off screen costs a frame at most
   once a minute.
4. **While away, a time shown moves on no sooner than every 5 s** (`Model.AWAY_CLOCK`): the next change is put off to
   at least 5 s after now. This takes the place of ADR 67 decision 2, which took the time in `drawn()` to 5 s.
5. **Only one wake is scheduled.** A frame that finds the same next change keeps the one scheduled; a different one
   takes its place; none cancels it.
6. **A command that runs asks for the frames of the spinner only once it shows**, after `Jobs.SHOWN_AFTER`
   (`FetchLine.showsSpinner`). It asked for them from the moment it started, so each of the short reads the timers
   start drew a frame that changed nothing, once the clock no longer drew one a second to hide them. The spinners are
   looked at every frame of theirs, so a command that comes to show is drawn at most a frame late.

## Consequences

- An idle app draws no frame until a time it shows changes: about one a minute for the ages of the branches, one every
  10 s for the first minute after a fetch, one a second while a dialog shows a check that runs. Measured on the same
  repository, the files pane, the commits, a diff and both rendered versions drew no frame in 20 s idle, where they
  drew 20.
- A view that starts drawing a time must tell its clock, or that time stays as it was until something else draws a
  frame. The tests of the fetch line and of the branches draw the time the moment before the change their clocks tell,
  and the moment of it, and check it changes then and not before; those of the dialogs check what they are woken for.
- Finding the next change asks each time shown whether it changes before the first change found so far: with 300
  branches, some 600 short texts a frame, and some 50 more for each time that is the first so far.
- Spinners and the slides of the panes keep their own frames.

Amends ADR 33 (`0033-show-that-work-is-being-done.md`) and ADR 67
(`0067-reading-less-rather-than-nothing-in-the-background.md`), decision 2.

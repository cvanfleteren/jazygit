# 14. Draw a frame only when something changed

Date: 2026-10-22

## Context

TamboUI sends a tick every 40ms by default, and draws a frame after each one. jazygit kept that default, so it drew
the whole screen 25 times a second, also with nothing changed. A profile of an idle app showed the render thread
taking about a quarter of a core, some 10ms a frame, a third of that in TamboUI's CSS: `StyleEngine.resolve` copies
all rules and variables for every element it styles. Git and the background commands hardly showed.

The ticks also hid that much of what changes the screen draws no frame of its own. TamboUI draws after an event
that is handled, a resize and a tick. What reaches the render thread through `runOnRenderThread` or `runLater` is
run, and drawn on the next tick: the outcome of every command, the reports of the widgets, the periodic ticks of
the model, switching to another repository and coming back from a program the terminal was handed over to.

## Decision

### No ticks

- TamboUI's tick is turned off (`noTick()`). Resizes still draw, as TamboUI checks for them by itself.
- `Program` is told when a message changed the model (`modelChanged`), that is when the new model does not equal
  the old one. Comparing is cheap, as records and vavr's collections stop at the parts they share. The app then
  asks for a frame. That covers the outcome of commands, the ticks of the model, the reports of the widgets and
  dragging the border of the left column, which dispatches every width.
- What is kept only to start the loads is left out of that comparison (`Model.drawn()`): which periodic loads run
  (`Jobs.refreshing`) and when the worktrees were last read (`Jobs.worktreeChangesRead`). Every tick starts the
  periodic loads and each one finishing takes it off again, so compared in full, an idle app drew a frame for each
  of them, some 6 a second, for nothing on the screen. Only what is surely not drawn is left out: leaving out too
  little costs a frame, too much leaves a change undrawn.
- A report that changes nothing in the model asks for no frame. Comparing by reference would have: every frame
  would report, and every report would draw a frame.
- The model tells the time every 500ms (`Msg.Tick`), so an idle app still draws 2 frames a second, which keeps the
  ages it shows up to date. That costs about 2% of a core and is accepted.

### Asking for a frame

- TamboUI has no call for it. The app queues an event of its own, `Redraw`, through `TuiRunner.dispatch`, and a
  global handler handles it, which draws a frame.
- At most one is queued at a time (`redrawPending`), and a frame drawn for any other reason meanwhile makes it draw
  none. So a burst of changes, like the moves of a drag, draws once, the last one, and a key that is handled and
  changes the model draws once rather than twice.
- A fake `TickEvent` would have done too, but TamboUI always draws on a tick, so a handled key that changes the
  model would draw two frames.

### Spinners

- A spinner turns by being drawn: its frame is told by the clock. Every 120ms, the time a spinner frame shows,
  the app asks for a frame if one shows (`Spinners.shown`): on a busy branch, or in the pull request or comments
  dialog while it loads. With none showing, that is one check and no frame.
- Each of those views says when it shows a spinner itself, so what `Spinners.shown` checks does not drift away from
  what is drawn.

### What the model does not know

- The focus is kept by TamboUI (ADR 2). When a frame moves it, as a popup that opens or closes does, or TamboUI's
  own auto-focus after the frame, the panes were drawn with the focus before; the app then asks for another frame.
- A click on nothing that takes the focus, like the command log, makes TamboUI clear it, and handle nothing. Every
  press now asks for a frame, and a frame that starts without a focus gives it back to what had it last, so the
  keys keep working in the pane they did. It used to go to the first pane, on the next tick.
- Switching to another repository and coming back from a handed-over terminal ask for a frame themselves.

## Consequences

- An idle app draws 2 frames a second instead of 25. Keys, clicks and scrolling draw as they did, at once.
- Whatever changes the screen without changing the model, or an event that is handled, must ask for a frame: without
  the ticks, nothing else draws it. The tests run without ticks, as the app does, so they show such a gap.
- To do in TamboUI: a call that asks for a frame, which replaces `Redraw`, and caching the rules and variables in
  `StyleEngine`, which makes every frame cheaper.

Amended by ADR 21: the model is told the time every 5s by a clock of its own, `Msg.Clock`, apart from the
refresh tick.

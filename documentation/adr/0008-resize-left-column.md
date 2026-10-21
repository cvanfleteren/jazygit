# 8. Resizing the left column

Date: 2026-10-21

## Context

The left column, with the panes of the screen, takes a fixed 30% of the width, next to the content panel and the
command log. A pane with long names (branches, pull request titles) is cut off, and a wide diff wants the room the
other way. There is no way to change it.

TamboUI hands every mouse event to the global handlers first. Both backends always turn on button event tracking
(`?1002h`), so a mouse moved with a button held comes in as a `DRAG` event without turning on mouse motion.

## Decision

The width of the left column can be changed with the mouse and with keys. Only the width: the heights of the panes in
the left column stay as they are.

### The width

The width is a percentage of the terminal width, so it keeps its share when the terminal is resized. It is clamped to
15–70%, so neither side disappears. The repository screen and the pull requests screen each have their own width,
both 30% by default.

The width is in the `Model`, as the update clamps it (ADR 2). It is saved in the settings file, as
`repository-width` and `pull-requests-width` in its `[ui]` table; a width that is missing or out of range falls back
to 30%, as an invalid theme falls back to the default one.

### Keys

`<` makes the left column of the shown screen 5 points narrower, `>` 5 points wider. They are global keys, listed in
the help, and are saved right away. Like `q`, they do nothing while a text field has the focus or a popup is open.
There is no key to reset the width.

### Dragging

Pressing the left button on the border between the left column and the content panel, on either of its two columns
(the right edge of the panes, the left edge of the content panel), starts a drag. That press does not move the focus,
as a click on a pane otherwise does. Each `DRAG` event sets the width from where the mouse is, and the layout follows
it; the release saves it. A drag does not start while a popup is open.

The terminal reports a move for every cell the mouse crosses, and TamboUI draws a frame after every event that is
handled. Drawing each width in between lags behind the mouse, so the moves are applied to the model but left
unhandled: the next tick draws the last width only. The press and the release are handled.

That a drag is going on is view state, kept in `JazygitApp` next to the focus: no update reads it. It sends
`Resized(screen, percent)` for each drag event and `Settled(screen)` on release, which saves the width.

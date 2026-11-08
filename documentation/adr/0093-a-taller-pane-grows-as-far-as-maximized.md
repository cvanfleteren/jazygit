# 93. A taller pane grows as far as a maximized one

Date: 2026-11-08

## Context

`+` makes the focused pane of the left column taller by moving its bottom border, taking from the pane under it
only (ADR 92). Once that pane is `MIN_HEIGHT` tall, `+` stops, though the panes further down and the ones above still
have rows. `Z` gives the pane all the rows but 3 for each of the others. Pressing `+` long enough does not get the pane
anywhere near that, which is the size one expects it to end up at.

The heights kept are percentages, each at least `MIN_HEIGHT` (`Settings.heights`), and a maximized pane is not saved.

## Decision

- `+` takes the rows from the panes under the pane first, the nearest first, each down to `MIN_HEIGHT`, then from the
  panes above it, the nearest first. The last pane has none under it, so it takes from the ones above.
- When every other pane is as short as `+` makes it, the next `+` maximizes the pane, as `Z` does. It then slides
  there as `Z` does. `+` on a pane that is maximized and shown that way does nothing.
- It does not maximize a pane that would show no more rows that way: on a column under 30 rows, `MIN_HEIGHT` is fewer
  than the 3 rows a pane keeps beside a maximized one. Nor a stash that shows all its stashes, which `+` does not make
  taller (ADR 92).
- `-` still moves the bottom border of the pane, and from a maximized pane it still starts from the heights shown,
  each raised to `MIN_HEIGHT`.
- A drag of a border still moves that border only.
- Not: letting the heights kept go under `MIN_HEIGHT` to reach 3 rows, which the settings file would not read back.

## Consequences

- Pressing `+` again and again ends where `Z` does, and `-` comes back from there.
- `+` is no longer the key version of dragging the bottom border; `-` still is.
- The heights saved after the last `+` are the ones from before that press: maximized is not saved, so the next
  session shows the pane as tall as `MIN_HEIGHT` for the others allows.

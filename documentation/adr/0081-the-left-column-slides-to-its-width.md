# 81. The left column slides to its width

Date: 2026-11-05

## Context

`<` and `>` make the left column 5 percent narrower or wider (`LayoutUpdate.STEP`): 8 columns at once on a screen 160
columns wide, 12 on one of 240, from one frame to the next. A prototype tried the column jumping there, sliding there
one column a frame, and easing out over 150 and 220 ms; easing out over 150 ms read best.

The app draws a frame only when something changes: TamboUI's ticks are off, and a spinner asks for its frames itself.
TamboUI's ticks and the effects it runs after a frame have nothing to slide a column with: the width of the column
is the layout's, and an effect only changes the cells of a frame drawn.

The column was laid out as a percent of the width, which TamboUI's layout solver turns into columns.

## Decision

- A step of the left column eases out to the width chosen over 150 ms: fast first, slowing down (`ColumnSlide`). The
  model keeps the width chosen, as before; where the column is on its way is the app's, as the cursors of the panes
  are, worked out for the time each frame is drawn at.
- While it is on its way, the app asks for a frame 16 ms after each frame (`JazygitApp.leftColumns`), one at a time,
  and for none once it is there: nothing is drawn while nothing moves, as before.
- Another width chosen on its way goes on from where the column is. A drag of the border, another screen, which has
  a width of its own, and the screen changing width do not slide: the column is there at once.
- The column is laid out in columns, at rest too, the nearest to its percent (`LeftBorder.columns`): a slide in
  columns ends where the column stays, not one column off where the solver puts the percent. It is the inverse of
  `LeftBorder.width`, which turns where the border is dragged to into a percent: on a screen under 100 columns, a
  column dragged to is the column the border is drawn at, and on a wider one, each percent is the percent dragged to.
- The animations can be turned off, in the settings (`m`) and as `animations = false` in the `[ui]` table of the
  settings file. They are on unless turned off. Then the column is at its width at once.
- Not: a slide of one column per frame. A step takes longer the wider the screen, 200 ms on one of 240 columns.
- Not: TamboUI's ticks, which would draw 25 frames a second whether anything moves or not.

## Consequences

- Each frame of a slide changes the width of the content panel, which then draws its lines twice (ADR 80).
- A left column at rest can be a column narrower or wider than the percent the solver laid out before.
- What else moves on the screen later can follow the same setting.
- The heights of the panes slide the same way, with the same setting (ADR 92).

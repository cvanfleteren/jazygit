# 92. Resizing and maximizing panes with keys

Date: 2026-11-07

## Context

`<` and `>` make the left column narrower or wider (ADR 8), and it slides there (ADR 81). Since #250 the panes of the
left column, under the status, are made taller or shorter by dragging their bottom borders, but there is no key for it
(#292). Their heights are percentages of the column (`Settings.resizedHeights`), each at least `MIN_HEIGHT`; a pane
made taller takes from the pane under it, and the last pane takes what the others leave. On the repository screen the
stash is as tall as its stashes, up to its share. The status pane is always 3 rows.

There is also no way to give one pane, or the content panel, all the room for a while: a long log or a wide diff.

Where the focus is, is TamboUI's (ADR 2), but the model keeps which pane or area of the content panel had it last,
outside the popups (`Model.shownPane`), which a closing popup gives it back to. The model does not know how many rows
the left column has: only the frame does.

`{` and `}` widen and narrow the context of the diff. `+` reacts on a comment in the diff of a pull request.

## Decision

### Taller and shorter

- `+` makes the focused pane of the left column taller, `-` shorter. They are global keys, next to `<` and `>`, listed
  in the help. They are characters, not places on the keyboard: on AZERTY, QWERTY and the numpad alike.
- A pane moves its bottom border, as a drag of it does: taller takes from the pane under it. The last pane, which has
  no border of its own, moves the border above it instead.
- A step is 5 percent of the column (`LayoutUpdate.STEP`), or more when 5 percent would not change the rows the pane
  is shown with: a press always moves a border by at least a row. The rows are counted with the rounding the column is
  laid out with, one helper for both.
- On the repository screen, the share of the stash is first the rows it is shown with, but no less than
  `MIN_HEIGHT`: a stash that shows all its stashes is not made taller, and the commits made shorter give rows the
  stash can show.
- No pane gets shorter than `MIN_HEIGHT`: a step stops there. Nothing changes when no step changes the rows of the
  pane, as the panes around it are as short as they get or the stash shows all its stashes; when the content panel or
  nothing on the left had the focus last; or while a popup is open.
- A key saves the heights at once and ends a drag whose release the terminal did not report, as `<` and `>` do.
- The keys send `LayoutMsg.PaneStepped(by)`, as `<` and `>` send `Stepped(by)`. The update steps the pane that had
  the focus last, `Model.shownPane`. Each frame reports how many rows the left column has when the model does not know
  yet (`LayoutMsg.Measured`), as the panes report what they highlight; until then a step is 5 percent. The update
  reads neither the focus nor the terminal.
- The help lists `+`, `-` and `Z` with the other global keys. Run from the help, they act on the pane that had the
  focus when it opened, which the model keeps while it is open.
- The heights kept always pass what the settings file is read with (`Settings.heights`): each pane, the last one
  too, at least `MIN_HEIGHT`.
- Not: `+` and `_`, which are next to each other on QWERTY only. Not: `{` and `}`, which are taken. Not: ctrl or alt
  with the arrows, which terminals and multiplexers take for themselves.

### Maximizing

- `Z` on a pane of the left column maximizes it: it takes all the rows it can, and the other panes, under the status,
  are 3 rows each, a border around the highlighted row. A maximized stash takes the rows too, with nothing in those it
  has no stashes for.
- `Z` on the content panel makes the left column as narrow as it gets, `MIN_WIDTH`. The heights of the panes stay.
- A pane is maximized until `Z` on it again. The focus moving elsewhere does not change it: a pane of 3 rows that gets
  the focus stays 3 rows. `Z` on another pane of the left column maximizes that one instead.
- A pane of the left column and the content panel can be maximized at once: one is about the heights, the other about
  the width. `Z` on either changes only that one.
- `Z` on the status does nothing: it is not resized.
- The model keeps, per screen, which pane of the left column is maximized, if any, and whether the content panel is.
  It is not saved: the next session starts with nothing maximized.
- `+`, `-` or a drag of a bottom border ends the maximized pane; `<`, `>` or a drag of the column border ends the
  maximized content panel. Then the heights or the width shown are kept, and the step or the drag goes on from there.
  The panes of 3 rows can be under `MIN_HEIGHT`, on a column over 30 rows: each is first raised to `MIN_HEIGHT`, with
  the rows taken from the pane that was maximized.
- Not: going back to the heights from before the maximize, which moves the borders away from where they are shown.
- A column too short for 3 rows a pane and 3 for the maximized one is laid out as if nothing were maximized, until the
  terminal is taller.
- Not: maximizing the pane with the focus, wherever it goes. Not: `Z` hiding the left column: that is a mode for the
  whole screen, as lazygit has, not this.

### Sliding

- The heights slide as the width does (ADR 81), out over 150 ms, after `+`, `-` and `Z`; the width slides after `Z` on
  the content panel too, as `ColumnSlide` slides to the width shown, not the one kept. The `animations` setting turns
  both off.
- A drag does not slide, and neither do heights that were not chosen: the stash growing with its stashes, the terminal
  getting taller or shorter, another screen.
- The easing and when it ends are one `Slide`. `ColumnSlide` slides the columns of the left column with it, and a
  `PaneSlide` slides the rows of all the panes at once, from one start, with rows that always add up to the column.
  The app asks for one frame for both.

## Consequences

- The panes are resized and maximized without a mouse.
- `-` is taken: collapsing the file tree, as lazygit does with `-`, needs another key.
- The width and the heights shown can differ from the kept ones while something is maximized; only what is kept is
  saved.
- A pane of 3 rows shows one row, which its cursor moves through.

Amended by ADR 95: `+` takes from the panes under the pane, then from the ones above, and once they are as short as
they get, maximizes it.

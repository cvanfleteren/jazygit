# 104. The left column collapses

Date: 2026-11-11

## Context

The left column is 15 to 70 percent of the width (ADR 8). `Z` on the content panel maximizes it by making the left
column as narrow as it gets, 15 percent (ADR 92): 24 columns on a screen of 160, 36 on one of 240, of panes too
narrow to read. A wide diff wants that room.

A prototype (branch `prototype/icon-rail`) tried a column of 3 to 5 columns with an icon for each pane: in boxes as
tall as the panes, as a bar of icons at the top, and with the names spelled downwards; with Nerd Font icons, plain
Unicode and letters. None of them told more than the title of the content panel does, which names what it shows.
Whether a Nerd Font is there cannot be known: a terminal does not say which font it draws with, and a glyph it lacks
still takes a cell.

## Decision

- The left column of a screen can be collapsed: it is then 3 columns wide, an empty panel with `»` at its top right,
  pointing the way it opens. It shows no pane, not the status either.
- `Z` on the content panel collapses the left column, and `Z` on a collapsed column opens it at the width kept,
  whatever had the focus last. The maximized content panel of ADR 92 is the collapsed column: a pane of the left
  column and the content panel can still be maximized at once.
- `<` at the narrowest width collapses the column. `>` opens it at the narrowest width, which is kept.
- A drag of the border of the column past half the narrowest width, 7 percent, collapses it; dragged back over it,
  it opens. Letting go of a collapsed column keeps nothing.
- A click on the collapsed column opens it at the width kept. Its right edge is the border, which a press drags.
- Leaving the content panel for its pane, with Escape or with Left at its left edge, opens the column, so the pane
  that gets the focus is drawn. A pane that has the focus when the column collapses, by `<` or a drag, gives it to
  the content shown for it. Content that is only a message, like that of the files of a clean worktree, cannot take
  it: the hidden pane keeps it, and `Z`, `>` or a click opens the column.
- `+` and `-` do nothing while the column is collapsed: its panes are not drawn.
- Collapsed is for the session, per screen, as a maximized pane is (ADR 92): the next session starts with the width
  kept. The settings file has no value for it.
- The column slides to 3 columns and back as it slides to a width (ADR 81).
- `LayoutMsg.Expanded(screen)` opens the column; the model keeps the collapsed screens in `Preferences`.
- Not: an icon for each pane. Not: a setting for the icons, or guessing a Nerd Font from the terminal. Not: widths
  between 3 columns and 15 percent, in which the panes cannot be read.
- Not: keeping the column collapsed for the next session, which would start it with no pane drawn.

## Consequences

- A wide diff gets all but 3 columns of the width.
- The width of the column is no longer a percent alone: the layout asks the model whether it is collapsed first.

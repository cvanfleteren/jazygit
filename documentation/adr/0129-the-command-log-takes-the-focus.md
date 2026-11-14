# 129. The command log takes the focus

Date: 2026-11-14

## Context

The command log under the content panel lists the git commands of the last 100 operations (`CommandLog.MAX_ENTRIES`),
each under the title of its operation, and why the last one failed or a notice about it. Without the focus it fits its
lines: up to 8, its borders and title alone when empty, in the rows the content panel spares above 12, and always a row
for the error or notice (#485). So it shows the newest 8 lines at most, and on a short screen as little as the first
row of an error. The rest is kept but cannot be read.

It is display-only: it takes no focus, by Tab, arrow or click, and has no keys.

Focus is view state, kept by TamboUI (`FocusKeeper`), not the model. The model knows the pane or content area that
had the focus last (`shownPane`), which the content panel shows the content of and which a closing popup gives the
focus back to. A popup has the focus aside from that: it is not reported to the model.

## Decision

- `@`, a global key, gives the command log the focus, and so does a click on it. Escape gives the focus back to where
  it was. Tab and the arrows do not move into it: they move between the panes and their content, and the log is
  neither.
- With the focus, it grows: to half the column it is in, or as tall as all it logged when that is less, but never
  shorter than without the focus. It slides there and back as it does to its other heights.
- With the focus, it shows all it logged, the oldest first, from the newest: the commands of every operation, and
  the whole error or notice. Every line is wrapped at words, as the error is without the focus, so a long command or
  error is read whole.
- It scrolls as the areas of the content panel do: the arrows, `j` and `k`, the pages, Home and End, `g` and `G`,
  and the wheel. How far down it is shows on its right border. Not sideways: it wraps.
- The focus in the log is aside, as a popup's is: it is not reported to the model. The content panel keeps showing
  what it showed, the pane keeps its highlighted row, and Escape gives the focus back to the model's `shownPane`.
- The other global keys keep working with the focus in the log.
- The log draws its lines wrapped by `TextWidth.wrapped`, which draws them as a word-wrapping `Paragraph` does: the
  text area scrolls by lines, so a line it wrapped itself would scroll as one.
- Where it is scrolled to is the log's own, kept by its text area while it has the focus. It starts from the newest
  each time it is given the focus.

## Why this way

- A key and a click, not a place in the Tab cycle: the log is looked at now and then, after an operation, and moving
  through it on every Tab between the panes would be in the way.
- Growing on focus, not scrolling in the rows it has: without the focus it can be one row, too few to scroll through.
- Aside, as a popup: the content panel shows what goes with the pane that had the focus, which the log has none of.

## Consequences

- The log has an id, `command-log`, and keys of its own, in `CommandLogPanel`, which is no longer only static.
- `FocusKeeper` treats the log as it treats a popup.
- A click on the log no longer leaves the focus where it was.
- `@` is taken.

# 78. An area is given only the lines that show

Date: 2026-11-05

## Context

A diff is styled once, when the body an area shows changes (ADR 50), but every frame still costs as much as the diff
is long. TamboUI's text area measures how wide each line it is given is, on every render, to know whether it scrolls
sideways, and `Line.width()` is not kept: it goes over every character of every span. The area also went over all the
lines itself each frame, to tint the cursor (ADR 60) and to scroll sideways, and to find the stop of the cursor. A
frame of a scrolled, focused rich diff of code took about 4.5 ms at 5000 lines, 51 ms at 50 000 and 106 ms at
100 000: a diff cut off at `CliDiffs.MAX_LINES` is near a frame of 60 Hz already, and a longer one would scroll at a
few frames a second.

`Line` and `Span` are final, so the app cannot keep their widths itself.

## Decision

- An area gives its element as many lines as the body it shows has, but only those that show are its lines; each of
  the others is one blank line, which is measured at once (`ContentPanel.windowed`). The element keeps its scrollbar,
  and how far it scrolls, as before. The lines that show are those of an area twice as high as the frame before, as
  the area already cut them sideways: a frame drawn after the area grows still shows only lines of the diff.
- The styled body keeps its lines as a `Vector`, the cursor tints the lines of its stop in it, and the stop of each
  anchor is indexed when the body is styled (`DiffCursor.indexes`); a frame only looks for the nearest stop when the
  anchor of the cursor is no stop any more.
- Not: the element given only the lines that show, with a scrollbar of the app's own and the scrolling of the keys
  and the wheel done in the model. It would cost nothing per line, but takes over what TamboUI's text area does now,
  for about 0.4 ms at 100 000 lines.

## Consequences

- A frame of the same diff takes about 0.25 ms at 5000 lines, 1.6 ms at 50 000 and 2.9 ms at 100 000. A longer diff
  still costs as much more to read (ADR 50), to style, once, and to keep: about 30 times its text.
- `CliDiffs.MAX_LINES` is not raised here.
- The element no longer knows how wide the lines that do not show are; the area scrolls sideways itself, and knows
  how wide the widest line is.

Amends ADR 60, whose cursor tinted a copy of all the lines of the diff each frame.

Amended by ADR 79: a diff is read only up to 50 000 lines or 4 MiB, and cut off there.

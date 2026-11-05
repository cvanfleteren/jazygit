# 80. A changed line goes on to the edge of the area

Date: 2026-11-05

## Context

A changed line of the rich view is padded to the width of its file, so its background makes a block (ADR 44); the bar
of the file and the tint of the cursor are as wide (ADR 60). A file of short lines in a wide area makes a narrow block,
with the background of the terminal to the right of it, as far as the edge of the area.

A diff is styled once, when the body an area shows changes, without knowing how wide the area is, and an area is given
only the lines that show (ADR 78).

## Decision

- The bar of each file, each line of its diff and each marker of the lines it leaves out are drawn as far as the edge
  of the area, or past it as far as the area is scrolled sideways: the area lengthens the last span of such a line if
  it is blank, the padding of a changed line, of the bar or of the tint of the cursor (`ContentPanel.filled`). Context
  lines have no padding, so they stay as they are.
- The bar is padded in a span of its own, and the tint of the cursor always pads its line in a span of its own, even
  when the line is as wide as its file, so its padding is what the area lengthens.
- Only the lines that show are lengthened, as the area cuts them sideways (ADR 78); the styled diff keeps the width of
  its files, which is as far as the area scrolls sideways.
- Not: the threads on a line, which are blocks of their own width, nor the plain view, whose lines are not padded.
- Not: drawing the diff once the width of the area is known. A diff would be styled again each time the area changes
  width.

## Consequences

- A changed line, the bar and the tint of the cursor make a band across the whole area.
- The width of the area is that of the frame before, as for scrolling sideways: right after the area changes width,
  one frame draws the lines as wide as the area was.

Amends ADR 44, whose changed lines were as wide as their file.

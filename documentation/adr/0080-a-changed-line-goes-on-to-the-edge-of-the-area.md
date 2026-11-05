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
- TamboUI's text area only learns how big it is while it draws, so the lines it is given fit the size it was drawn in
  the frame before. A frame that draws the area in another size than the one before, like the one after the panes
  are resized, draws it twice (`ContentPanel.Fitted`): once to let it learn its size, after which the cells it drew
  are put back, then with lines fitted to that size. TamboUI registers an element and its id once per frame, so the
  area still gets the keys, the clicks and the focus once. A frame in the same size draws it once.
- Not: asking for another frame once the area finds it was drawn in another size, as the app does when the focus
  moves. The frame drawn in the meantime would show the lines as wide as before, a flicker at the edge while a border
  is dragged.
- Not: working out the size from the area the element is drawn in. Whether the text area keeps a column for its
  scrollbar is TamboUI's to decide.
- Not: the threads on a line, which are blocks of their own width, nor the plain view, whose lines are not padded.
- Not: drawing the diff once the width of the area is known. A diff would be styled again each time the area changes
  width.

## Consequences

- A changed line, the bar and the tint of the cursor make a band across the whole area.
- The first frame after the area changes size fills it already; so does how far it scrolls sideways and how many lines
  it is given, which were also those of the frame before.
- A frame in another size draws the lines that show twice.
- The content panel is a column around its area or areas, as the wrapper of an area is no element TamboUI styles.

Amends ADR 44, whose changed lines were as wide as their file.

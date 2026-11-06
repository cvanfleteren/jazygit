# 87. Sixel paints a picture over its cells

Date: 2026-11-06

## Context

Kitty and Ghostty draw a picture of a review remark themselves (ADR 86): its cells are placeholders that say which
image they are and which part of it, and the terminal draws that part there. Other terminals, such as xterm, foot,
WezTerm, Konsole, mlterm and Windows Terminal, draw images with Sixel.

Sixel has none of that. An image is pixels written at the cursor, as large as they are in pixels, not in cells; the
terminal keeps no image to refer to again, and text written over its pixels takes their place. TamboUI writes only
the cells of a frame that changed, and has no hook to write after them: an image written while a frame is drawn,
as `tamboui-image` does, is written before the cells of the frame, which write over it. And to draw an image as large
as its cells, the size of a cell in pixels has to be known.

## Decision

- With `JAZYGIT_IMAGES=sixel`, a picture of a review remark is painted with Sixel. Not otherwise: no variable tells a
  terminal that has Sixel, and asking the terminal is left for later.
- A picture is drawn in placeholder cells, as for Kitty (ADR 86), so that the cells of a frame tell where each image
  is and which part of it shows: scrolled, cut off at the edge of its pane, or with a popup over it.
- After a frame is drawn, the placeholder cells are made blank, and the images they are of are noted: where each is,
  the part of it that shows, and which cells in that part are not its own, as those of a popup.
- `HandOverBackend`, which the app hands the terminal over with and reads every key through (ADR 58), is told the
  cells TamboUI wrote, and before the frame ends, inside its synchronized update, writes the images: first the cells
  where images were, again, so that their pixels go; then each image, cut to the part that shows, at its first cell;
  then the cells over it, again. Each frame has TamboUI write a synchronized update, by drawing a widget with raw
  output nowhere, so that this is done also when no cell changed.
- That is only done when an image moved, appeared, went, or TamboUI wrote a cell where one is or was. A frame that
  changes nothing near an image writes no image.
- A cell is as many pixels as the terminal tells the system, `TIOCGWINSZ`, as many as the window over its cells;
  `JAZYGIT_CELL_PIXELS=9x19` says otherwise. Where neither tells, as on Windows, a picture is half blocks again
  (ADR 84), as is one whose image is not read.
- An image is shrunk or grown to its cells in pixels, and made at most 255 colours: its pixels to 5 bits a channel,
  split by median cut, each pixel the nearest colour; no dithering. A pixel less than half opaque is not drawn, and
  the terminal is told to leave such a pixel as it is (`P2=1`).
- The 16 images last painted are kept at their size, and the 16 parts of them last written as Sixel, so that
  painting one again does not make it again.

## Consequences

- In a terminal with Sixel, a screenshot in a review remark can be read, but for its colours: a picture of many, as a
  photo or a gradient, shows bands.
- An image is written again when it scrolls, which is more to write than Kitty's, which is sent once: some 100 KB for
  a large screenshot, each time.
- A terminal that writes text over Sixel's pixels in another way than by taking their place shows what is over an
  image, or where it was, wrongly. xterm does as is needed.
- An image that would reach the bottom of the screen could scroll it: a picture is always in a pane, with its border
  under it, so none does.
- Over ssh, `TIOCGWINSZ` still tells the size of the window, if the terminal at the other end tells it.

Amends ADR 86: the placeholder cells of a picture are also how Sixel is told where to paint it.

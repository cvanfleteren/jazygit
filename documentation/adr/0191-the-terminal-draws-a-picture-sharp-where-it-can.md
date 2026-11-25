# 191. The terminal draws a picture sharp where it can: Kitty, else Sixel, else half blocks

Date: 2026-11-24

## Context

An image of a review remark or of a comment of the conversation is drawn in half blocks (ADR 84, 190): a cell is a
pixel wide and two high, a pixel for each 8 by 8 of the image, enough to tell what a screenshot is, not to read its
text. Many terminals can draw images themselves: Kitty and Ghostty with the Kitty graphics protocol; WezTerm, foot,
iTerm2, mintty, mlterm, contour, Konsole and Windows Terminal with Sixel.

ADR 84 left the protocols out: `tamboui-image` reads an image with AWT, which the native executable does not have,
and an image the protocol places is drawn past the cells of the buffer, so it would have to be placed, cut and cleared
again as the text scrolls and popups open over it. TamboUI writes only the cells of a frame that changed, and has no
hook to write after them.

Two prototypes, on a branch of 2026-11-06 that was never merged, found ways around both, one for each protocol. This
decision takes them in, as a chain: the sharpest a terminal can draw, and half blocks, which every terminal draws,
where it can draw neither.

## Decision

- **Which.** `JAZYGIT_IMAGES` says `kitty`, `sixel` or `halfblocks`, or `auto`. Otherwise it is told from the
  environment, never by asking the terminal, whose answer would come in as keys do:
  - half blocks inside tmux or screen (`TMUX`, `STY`), which pass neither protocol on without being told to;
  - Kitty's protocol in Kitty and Ghostty (`TERM` `xterm-kitty`, `xterm-ghostty`, or `TERM_PROGRAM` `ghostty`);
  - Sixel in WezTerm, iTerm2 and mintty (`TERM_PROGRAM`), foot, mlterm, contour and yaft (`TERM`), Konsole
    (`KONSOLE_VERSION`) and Windows Terminal (`WT_SESSION`);
  - half blocks elsewhere.
- **Kept sharp.** A picture read from an image keeps the image too, no larger than its most cells at 16 by 32 pixels
  each (1600 by 768), twice a cell's 8 by 16, as a screen at a larger scale has more pixels to a cell and a terminal
  shrinks an image better than it grows it. Its pixels are RGBA, compressed with zlib, in base64 (`Sharp`): up to
  some 4.9 MB of pixels before they are compressed. jazygit decodes and shrinks it itself, as ADR 84 says, so still
  no AWT; only those pixels reach the terminal, never the bytes of the image as its writer wrote them. A picture
  fitted to its place keeps it.
- **Placeholder cells.** With either protocol, each cell of a picture is Kitty's Unicode placeholder in place of a half
  block: `U+10EEEE`, a diacritic for its row, one for its column and one for the highest byte of its image's id, in the
  colour of the rest of the id. The id says which image it is and in how many cells: 12 bits how many columns and rows,
  the rest a hash of its pixels. TamboUI keeps a diacritic in the cell of the character before it for only some of
  them, the first 100 of Kitty's among them, so only those are used. These are cells like any other: they scroll, are
  cut off at the edge of a pane and are covered by a popup as text is.
- **Which pictures.** A feature tells which pictures its views may draw (`Feature.picturing`): GitHub's, those of the
  review threads and those of the conversation. The app knows them only as pictures, and a cell finds its image by
  the hash in its id.
- **Kitty.** After a frame is drawn, the cells of the whole screen tell which images are on it: each that was not
  sent yet is sent, before the frame reaches the terminal, as `a=T,U=1` with its columns and rows, in chunks of 4096,
  with `q=2`, so that the terminal does not answer. The terminal then draws each placeholder as its part of the image.
- **Sixel.** After a frame is drawn, the placeholder cells are made blank, and the images they are of are noted:
  where each is, the part of it that shows, and which cells in that part are not its own, as those of a popup.
  `HandOverBackend` is told the cells TamboUI wrote, and before the frame ends, inside its synchronized update, writes
  the cells where images were, again, so that their pixels go; then each image, cut to the part that shows, at its
  first cell; then the cells over it, again. Only when an image moved, appeared, went, or TamboUI wrote a cell where
  one is or was. A cell is as many pixels as the terminal tells the system (`TIOCGWINSZ`), or as
  `JAZYGIT_CELL_PIXELS=9x19` says; where neither tells, as on Windows, a picture is half blocks after all. An image is
  shrunk or grown to fit its cells in pixels keeping its shape, at their top left, what it leaves of them not drawn,
  and made at most 255 colours by median cut, without dithering; a pixel less
  than half opaque is left as it is (`P2=1`). The 16 images last painted are kept at their size, and the 16 parts last
  written as Sixel. An image is as high as the whole bands of six pixels that fit its cells, as Konsole draws the
  last band whole; and the column right of it and the row under it are written again after it, and when it goes, as
  a terminal at a fractional scale draws a cell a fraction smaller than it tells it is.
- **Half blocks** stay as ADR 84 has them: where neither protocol is, for a picture with no sharp image, and for one
  too large for an id to tell (more than 100 columns or 32 rows).
- Popups, the colours of the selected text and the dimming behind a popup leave the colour of a placeholder as it is,
  as it is the id of its image.
- Before the terminal is handed over to another program, and when the app stops, Kitty is told to delete every image
  (`a=D,d=A`); once the terminal is back, images are sent and painted again as they are drawn.
- The tests draw half blocks whichever terminal they run in: the build sets `JAZYGIT_IMAGES=halfblocks`.

## Consequences

- In Kitty and Ghostty, and in a terminal with Sixel, a screenshot in a review remark or a comment can be read; with
  Sixel, a picture of many colours, as a photo or a gradient, shows bands.
- A Kitty image is sent once for each size it is drawn at; the terminal keeps it until the app stops or hands the
  terminal over. A Sixel image is written again each time it scrolls: some 100 KB for a large screenshot.
- What is behind a popup is dimmed, but a picture there is not.
- Two images whose hash is the same draw as one, maybe the wrong one: of the 409,600 hashes, a pull request has 40
  images at most, 20 of its threads and 20 of its conversation.
- A terminal that is told it has a protocol, with `JAZYGIT_IMAGES`, but has not, shows characters of the private use
  area, or Sixel's sequences as text. One that has Sixel but is not on the list draws half blocks unless told.
- Over ssh, `TERM` and `TERM_PROGRAM` come along when the ssh client passes them, and the terminal at the other end
  draws the images, if it can.

Amended by ADR 202: a feature tells the pictures its views may draw by filling `KernelSlots.PICTURING`;
`Feature.picturing` is gone.

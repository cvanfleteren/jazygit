# 86. Kitty and Ghostty draw a picture themselves

Date: 2026-11-06

## Context

An image of a review remark is drawn in half blocks (ADR 84): a cell is a pixel wide and two high, a pixel for each
8 by 8 of the image, enough to tell what a screenshot is, not to read its text. Kitty and Ghostty can draw images
themselves, with the Kitty graphics protocol.

ADR 84 left the protocols out: `tamboui-image` reads an image with AWT, which the native executable does not have,
and an image the protocol places is drawn past the cells of the buffer, so it would have to be placed, cut and
cleared again as the diff scrolls and popups open over it. `tamboui-image` also sends the whole image again with
each frame, outside the synchronized update, and covers the popups drawn over it.

The protocol has a way around the second: Unicode placeholders. An image is sent once, with an id, as a virtual
placement of so many columns and rows; then each cell that is the character `U+10EEEE`, with diacritics for its row
and column, in the colour whose 24 bits are the id, is drawn as that part of the image. Those are cells like any
other.

## Decision

- In Kitty and Ghostty, a picture of a review remark is drawn by the terminal, as large as its half blocks would be,
  but as sharp as its cells have pixels. Which terminal it is, `TERM` says (`xterm-kitty`, `xterm-ghostty`) or
  `TERM_PROGRAM` (`ghostty`); not inside tmux or screen (`TMUX`, `STY`), which do not pass the protocol on without
  being told to. `JAZYGIT_IMAGES=kitty` asks for it anywhere, `JAZYGIT_IMAGES=halfblocks` never. Elsewhere, and for a
  picture that has no sharp image, it stays half blocks.
- A picture read keeps its image too, as the protocol is sent it: no larger than its most cells at 8 by 16 pixels each
  (800 by 384), its pixels as RGBA, compressed with zlib, in base64 (`f=32,o=z`). jazygit decodes and shrinks it
  itself, as ADR 84 says, so still no AWT; and only those pixels reach the terminal, never the bytes of the image as
  its writer wrote them, which the terminal would then have to read.
- Each cell of the picture is a placeholder in place of a half block: `U+10EEEE`, a diacritic for its row, one for
  its column and one for the highest byte of its image's id, in the colour of the rest of the id. The id says which
  image it is and in how many cells: 12 bits how many columns and rows, the rest a hash of its pixels. TamboUI keeps a
  diacritic in the cell of the character before it for only some of them, the first 100 of Kitty's among them, so
  only those are used.
- After a frame is drawn, the cells of the whole screen tell which images are on it: each that was not sent yet is
  sent, before the frame reaches the terminal, as `a=T,U=1` with its columns and rows, in chunks of 4096, and with
  `q=2`, so that the terminal does not answer, which would be read as keys typed. What is sent is looked up in the
  pictures the model has read, by the hash in the id.
- Popups, the colours of the selected text and the dimming behind a popup leave the colour of a placeholder as it is,
  as it is the id of its image.
- Before the terminal is handed over to another program, and when the app stops, the terminal is told to delete
  every image (`a=D,d=A`); once it is back, they are sent again as they are drawn.
- Not: a query of the terminal, to tell whether it has the protocol. Its answer comes in as keys do, and is to be read
  apart from them first.
- Not: WezTerm, Konsole, iTerm2 or Sixel: they have no Unicode placeholders, or no Kitty protocol.

## Consequences

- In Kitty and Ghostty, a screenshot in a review remark can be read as it is.
- The picture scrolls, is cut off at the edge of its pane and is cleared by a popup as text is: they are cells.
- A picture is sent once for each size it is drawn at; a pane made wider or narrower sends it again. The terminal
  keeps what it was sent until the app stops or hands the terminal over.
- What is behind a popup is dimmed, but a picture there is not.
- Two images whose hash is the same draw as one, maybe the wrong one: of the 409,600 hashes, a pull request has 20
  images at most.
- A cell of an image is drawn as such only in a terminal that has the placeholders; one that is told it has, with
  `JAZYGIT_IMAGES=kitty`, but has not, shows characters of the private use area.
- Over ssh, `TERM` comes along, and the terminal at the other end draws the images, if it can.

Amended by ADR 87: with `JAZYGIT_IMAGES=sixel`, the placeholder cells also tell Sixel where to paint a picture.

# 84. An image in a review remark is drawn in half blocks

Date: 2026-11-05

## Context

An image in a comment is a placeholder, `[image]` (ADR 82). In review remarks they are mostly screenshots, pasted
in, and the small icons of bots, such as the one that says Claude is working; what they show is often the point.

`tamboui-image` draws images, with the Kitty, iTerm2 or Sixel protocol where the terminal has one, else in half
blocks. It reads an image with AWT's `ImageIO`, which works in the native image only with eight libraries of the JDK
next to the executable, about 5 MB: on its own the executable fails with `UnsatisfiedLinkError: Can't load library:
awt`. jazygit is one file, in its release and in its Nix package. And the protocols draw past the cells of the
buffer, so an image would have to be placed, cut and cleared again as the diff scrolls; half blocks are cells like
any other.

## Decision

- An image of a review remark is read once the remarks are read, and drawn in half blocks: `▀`, its upper half a
  pixel in the colour of the text, its lower half the pixel under it in that of the background, so a cell is a pixel
  wide and two high. A pixel less than half opaque is not drawn, so the background shows there; `▄` when only the
  lower one is drawn.
- jazygit reads PNG, every colour type and depth, interlaced or not, and the first frame of a GIF, itself, without
  AWT or `tamboui-image`. A JPEG, a WebP, an SVG and an image that cannot be read stay the placeholder.
- An image is as large as a browser shows it, with a pixel of the screen for each 8 of it, as a cell is about 8
  pixels wide and 16 high: as its tag says, `width="14px"`, the side it leaves out as the image has it, else as large
  as it is. It is never wider than the text of the remark, less what the quotes and list items it is in indent it by
  as tamboui draws them, nor higher than 24 rows, and keeps its shape; shrunk, each pixel is the average of those it
  covers, weighed by how opaque they are. A quoted screenshot, as GitHub's "Quote reply" makes one, is drawn too.
- A block of HTML of only `<img>` tags at the top becomes a paragraph of its own; in a quote or a list it stays in
  its item.
- One row high, a picture is drawn in the line, where the placeholder was, as the icon of a bot is. Higher, only an
  image that is all there is of its paragraph is drawn, as a pasted screenshot is, a block of rows; in a line of text
  it stays the placeholder.
- Only images GitHub keeps are read, over https: `github.com` and `*.githubusercontent.com`. Any other host would be
  told who reads the remark, and when.
- An image is read with `gh api <url>`, as bytes, up to 10 MiB, and then up to 4096 × 4096 pixels; more is not read.
  gh sends its token to `github.com` only. The images of a pull request are read once while its files are listed, at
  most 20 of them, and kept with them, as the picture that is drawn, not the image.
- An image is written by whoever wrote the remark, so it is read as one made to harm: no more than its header says
  is read, and a header that says more than is ever read is not believed: no more than 4096 × 4096 pixels, no more
  than 64 MiB of PNG data inflated, a bit depth its colour type has, a GIF frame no larger than its screen. Images are
  read one at a time, as each may take some 150 MB while it is; one that runs out of memory all the same stays the
  placeholder. A broken image is no picture, never a failure. Only cells of colour reach the terminal, never the
  image's bytes, as they would with Sixel or Kitty.
- tamboui draws the Markdown, wrapping and indenting, and the picture is read back into it: each cell of a picture is
  a character of the private use area of Unicode while it is drawn, a column wide, the next character for the next
  picture, which are then replaced by the cells of the picture, in order.
- A picture is no failure to tell: one that cannot be read stays the placeholder.
- Not: the comments of the conversation of a pull request; they keep the placeholder, as tamboui draws them on the
  screen without reading them back.
- Not: the Kitty, iTerm2 or Sixel protocol, nor AWT and its libraries next to the executable.

## Consequences

- A screenshot in a review remark shows what it shows, roughly: a pixel for each 8 by 8 of it at most, enough to
  tell what it is, not to read its text.
- A private repository's images that GitHub does not give the token of `gh` stay the placeholder.
- Reading a PNG or GIF is jazygit's own code, some 500 lines, tested against what the JDK writes.
- A comment that holds a character of the private use area itself may have it drawn as a cell of a picture.

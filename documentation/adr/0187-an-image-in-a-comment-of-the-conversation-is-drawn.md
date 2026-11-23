# 187. An image in a comment of the conversation is drawn too

Date: 2026-11-24

## Context

ADR 84 draws the images of review remarks in half blocks, but left out the comments of the conversation on a pull
request, the Comments dialog: tamboui draws them straight onto the screen, scrolled, and nothing read them back to put
the pictures in. A screenshot pasted into a comment, which GitHub writes as `<img width="…" alt="image"
src="https://github.com/user-attachments/assets/…" />`, stayed `[image]`, though that is often the point of the
comment.

## Decision

- The images of the conversation are read as those of the review threads are (ADR 84): once the comments are read,
  only those GitHub keeps over https, each once, at most 20 for the conversation, in the background with `gh api`,
  each a picture or none. They are kept with the comments in the popup, as the comments are, and read again when the
  dialog opens again; read again while it is open, those read stay read.
- The highlighted comment draws them as a review remark does, with the same rules for size, place and what stays the
  placeholder (`MarkdownImages.drawn`), at the width of the pane.
- To draw them, `MarkdownScroll` renders the comment whole, off the screen, over a copy of what the pane shows where it
  is in view; puts the cells of each picture in, in the order they are read; and copies what is in view to the screen.
  So a picture scrolled half out of view shows the rows that are in view, and where a picture is see-through the pane
  shows. A comment without pictures is rendered as before, straight onto the screen.
- The row of a comment in the list stays text: `[image]`.

## Consequences

- A screenshot in a comment of the conversation shows what it shows, as roughly as in a review remark.
- Opening the dialog reads its images each time, as it reads its comments.
- A comment with pictures is rendered whole on each frame, not only what is in view.

Amended by ADR 188: where the terminal draws images, with the Kitty graphics protocol or Sixel, the pictures of the conversation are drawn sharp.

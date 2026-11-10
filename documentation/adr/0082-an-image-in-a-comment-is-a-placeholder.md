# 82. An image in a comment is a placeholder

Date: 2026-11-05

## Context

A comment, a review remark or the description of a pull request may hold images: Markdown images, `![alt](url)`, and
`<img>` tags, which GitHub writes for an image that is pasted in and which bots use for icons. The terminal shows none
of them. tamboui draws a Markdown image as `[image: alt](url)`, the whole URL, and an `<img>` as the tag it is, which
is often longer than the text around it: the status icon of a bot,
`<img src="https://github.com/user-attachments/assets/…" width="14px" height="14px" style="vertical-align: middle;
margin-left: 4px;" />`,
took three lines after one line of text.

`tamboui-image` can draw images, with the Kitty, iTerm2 and Sixel protocols where the terminal has one and with
half blocks or braille where it has not. That needs the image downloaded, with the token of `gh` for a private
repository, and decoded with AWT, which the native image does not have yet; it is left for later.

## Decision

- Each image of a comment is a short text that says one is there: `[image]`, or `[image: alt]` with its alt text,
  unless that says no more than "image", which is the alt text GitHub gives what is pasted in. It is drawn as a link
  to the image, where it is not in a link already.
- That is an `<img>` in the text, a block of HTML of only `<img>` tags, and a Markdown image. Which they are,
  commonmark says, so what is in code is left as it is; each is replaced where its source is, before tamboui draws the
  Markdown, as HTML comments are.
- The first line of a comment, which a collapsed comment, the list of comments and the question to delete one show
  as it is written, has the same text without the link.
- Not: other HTML. A block of HTML with more than `<img>` tags, such as `<p align="center">` around one, is left as it
  is.

## Consequences

- A comment with an image takes as many lines as its text, and says where the image is.
- What the image shows is not seen in jazygit; the link, or GitHub, shows it.
- Showing the image itself, with `tamboui-image`, can replace the placeholder where the terminal can, and keep it
  where the image cannot be read.

Amended by ADR 84: an image in a review remark is drawn in half blocks, read without AWT or `tamboui-image`.

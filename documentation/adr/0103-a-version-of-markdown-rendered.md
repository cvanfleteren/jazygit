# 103. A version of Markdown rendered

Date: 2026-11-11

## Context

A version of a file (ADR 102) shows the whole new file or its original in place of its diff, as source. For a README,
a design note or an ADR, the source is not what its readers see: GitHub shows a Markdown file rendered, its headings,
lists, tables and code blocks drawn, and in a pull request it can show that rendered file in place of its diff.

jazygit draws Markdown already: the description of a pull request with tamboui's `MarkdownElement`, and a review
remark (ADR 55) top-level block by top-level block with `MarkdownView`, its rows read back as lines, so that an image
can be drawn in it (ADR 84). tamboui parses with commonmark-java, with tables, strikethrough and task lists. It prints
raw HTML as it is written, has no front matter, draws an image as `[image: alt](url)`, highlights a code block with a
highlighter of its own, which can be replaced, and styles Markdown by the kind of element only, not block by block.

Markdown is not the only text a reader would rather see drawn: a CSV file is a table, a notebook (`.ipynb`) is cells
of Markdown and code, not the JSON it is stored as. Images are files to draw too, but of bytes, not of text, and a
version of ADR 102 has no bytes: it is made from a diff read with the whole file as context, in which a binary file
is one line saying it differs.

## Decision

### What is rendered

- A version of a Markdown file can show rendered: drawn as Markdown, in place of its source. A file is Markdown by its
  extension: `.md`, `.markdown`, `.mdown`, `.mkd`. Not: `.mdx`, which holds JSX. Not: the language
  `.gitattributes` gives a file.
- One table says which extensions have a renderer, and which. A renderer is given the text of a version, the numbers
  of its lines that changed, a width and the styles of the theme, and gives the rows it is drawn as, each with the
  lines of the source it was drawn from. Markdown is the only renderer now; CSV and notebooks are to come as
  renderers of their own. Not: images in the same seam, which need bytes, of both sides, and have no lines; they are
  for an ADR of their own.
- What is rendered is the version ADR 102 shows, read as it reads it: no other command of git is read for it, and a
  version cut at the limit of ADR 79 is rendered up to where it is cut, and says so after it, as the version does.
- Only a version is rendered, not the diff. Not now: a rendered diff, its added blocks and its removed ones marked, as
  GitHub's rich diff of prose has them. The state below leaves room for it.

### The key

- Ctrl+T, in the diff (`Scopes.DIFF` and `Scopes.PULL_REQUEST_DIFF`), renders the area the cursor is in, or shows its
  source again, as Ctrl+O cycles it (ADR 102). Not: a third style next to plain and rich (Ctrl+D): rendered is no way
  to draw a diff. Not: more steps in the cycle of Ctrl+O, diff, new version, rendered, original, rendered, five
  presses round. Not: Ctrl+O's versions always rendered for Markdown, which leaves no way to read its source whole.
  Not: Ctrl+R, which opens the recent repositories everywhere: in the diff it would hide that, and where it does not
  apply, a key the diff leaves goes on to the global keys and would open them after all.
- An area is shown as two things: which version, as ADR 102 has it, the diff, the new version or the original, and
  whether it is rendered. Ctrl+T on the diff renders the area and shows its new version, or its original for a deleted
  file. Ctrl+T on a rendered version shows that version as source. Ctrl+O while rendered cycles the diff, the new
  version rendered and the original rendered: the diff shows as a diff, but the area stays rendered, so the next
  Ctrl+O shows a version rendered again. Once a diff can be rendered, Ctrl+T on the diff renders it instead.
- Where it cannot apply, Ctrl+T does nothing, as Ctrl+O does: wherever Ctrl+O does nothing, and on a file without a
  renderer: Ctrl+T means nothing outside the diff, so the key the diff leaves goes on to no other. `?` lists it as it
  lists the other keys of the diff. Not: a message saying why.

### How long it holds

- Rendered is kept with the version of ADR 102, and dropped with it: once the area shows another file, coming back
  shows the diff. Not: kept for the session, which would open every Markdown file rendered while its changes are
  what is reviewed.

### How it is drawn

- A version is rendered top-level block by top-level block, as a review remark is: each block of the document drawn
  with `MarkdownView` on its own, its rows read back as lines, and the lines of the source it was parsed from kept with
  them. A list is a block for each of its top-level items. The code doing it for remarks is drawn out of `RemarkLines`
  for both. Not: `MarkdownElement` as it is, as the description of a pull request is drawn, which gives no rows to mark
  or to scroll to.
- A paragraph, a heading and a list are wrapped to the width of the area. A code block and a table keep their width,
  and scroll sideways with the area, as the lines of a diff do.
- A table is drawn by jazygit: each column as wide as its widest cell, aligned as its row of dashes says, two spaces
  between columns, its first row bold, each cell drawn as tamboui draws Markdown. Not: tamboui's table, which gives
  each column an equal share of the width, so a short first column takes as much as a long second one, and cuts off
  what does not fit.
- A code block is highlighted by the lexer of jazygit (`jazygit-syntax`), in the theme, for the language its info
  string names, through `MarkdownView`'s highlighter: so code reads as in the diff. A language the lexer does not know
  is plain. Not: tamboui's own highlighter, with its own colours.
- Front matter, a block of YAML between `---` lines at the top, is drawn as a `yaml` code block, where GitHub draws it
  as a table. A comment of HTML is left out, as it is in the description of a pull request. Other HTML is drawn as it
  is written.
- An image is drawn as the placeholder of ADR 82. Drawing them, an image with a relative path read from the same
  version and one GitHub keeps read as ADR 84 reads it, is for the ADR on images: it needs a file read as bytes.
- A link stays as tamboui draws it, a hyperlink where the terminal has them. A relative link, to another file of the
  repository, leads nowhere outside it: its text is drawn without a target. Not now: opening links as in a comment
  (ADR 96).

### Where it changed

- A top-level block that holds a line that changed has a bar, `▎`, in the gutter beside its rows: in the colour of an
  added line in the new version, of a removed one in the original. In a theme without a background for changed lines,
  as `mono`, it is `+` or `-`, as the marker of ADR 102 stays there. So a rendered version shows where the changes are
  in it, as a version shows them tinted. Not: the block tinted, which fights the style of a code block or a table.
- The gutter is two columns, the bar and a space: no numbers, as a row drawn is no line of the file.

### Title, cursor, review threads and copying

- The title of the area says it: `Files of #403 · new version, rendered`, with `(reading)` and `(could not be read)`
  as ADR 102 has them. The bar of the file is as in the version: `new version  README.md`.
- A rendered version has no cursor in its blocks: it scrolls. The bar of its file is its only stop, whose lines the
  rows after it are, so `j` and `k` scroll through them, and the keys of the diff, as Ctrl+O and Ctrl+T, still have a
  cursor to act on. Rendering it scrolls to the block that holds the line of the cursor, at the row the line was on;
  a key acts on the first line of the block at the top, or of the first block while the bar is at the top, so showing
  its source again puts the cursor there. Not: a cursor going from block to block, which is for when a block can be
  acted on.
- While rendered, `y` and `v` do nothing, a thread cannot be started, and no review threads are shown.

## Consequences

- A version has two looks, and an area holds which, with the version.
- Rendering a block costs a `MarkdownView` drawn off the screen; a version is rendered again only when its text, its
  width or the theme changes, not each frame.
- Other formats come in as a renderer and a line in the table of extensions; images come in with an ADR of their own,
  which reads files as bytes, and may draw them as PR #378 does.
- A rendered diff can come later as Ctrl+T on the diff, marking the blocks added and removed from both versions read
  whole.

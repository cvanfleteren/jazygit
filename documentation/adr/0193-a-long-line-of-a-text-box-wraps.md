# 193. A long line of a text box wraps, and the commit description marks column 72

Date: 2026-11-24

## Context

The text boxes of the popups (a comment, the body of a pull request, the description of a commit) scrolled sideways
when a line was longer than the box. A paragraph of a comment is one long line, and only the part around the cursor
showed: what was typed a moment before had scrolled out of sight.

TamboUI's text area wraps lines itself since 0.7.0 (`TextAreaElement.wrapWord()`): it draws a long line over the rows
below it, puts the cursor on the row it is on, and moves Up and Down by the rows shown. All five boxes are a
`SubmittableTextArea`.

A commit is different from a comment. A forge reflows the Markdown of a comment, but `git log` shows the lines of a
commit message as they are, and git's own tools and most projects keep them to 72 characters. Wrapping the box hides
that a line is longer.

## Decision

- **Every `SubmittableTextArea` wraps at a word.** The wrapping is only how the text is shown: the text gets no line
  break it was not given, so what is saved is what was typed.
- **A ruler marks a column past a line length**, where a box asks for it (`SubmittableTextArea.ruler`): the column
  after the first so many characters of text gets the style of the theme's `Span.ruler`, as vim's `colorcolumn` does.
  Text that reaches it is too long. Only the background of the rule is laid over the column, or, where the rule gives
  none, its attributes, so the text under it keeps its colour.
- **The commit description has a ruler at 72.** The summary, a one-line field, has none.
- **Each theme colours the ruler as the header of a review thread**, a quiet background of its own; the theme without
  colours underlines it. Not reversed: the text area draws its cursor as a reversed cell, which would not show on a
  reversed ruler.

Turned down:

- **Breaking the lines of a commit at 72 as they are typed**, as an editor's hard wrap does: a URL or a pasted line
  would be broken too, and Ctrl+O already opens the message in the user's editor for that.
- **Not wrapping the commit description**, so a long line shows as one: the same problem as a comment's paragraph,
  for a description that does not keep to 72.

## Consequences

- No box scrolls sideways any more.
- The ruler marks the same column on a row that continues a wrapped line, as vim's does; a line wraps only past the
  width of the box, which in the commit popup is well over 72 columns, so such a line is past 72 anyway.
- Ctrl+A and Ctrl+E go to the start and the end of the line typed, not of the row shown, as in other editors that
  wrap.
- The text area's syntax highlighting only works on a box that does not wrap; no box uses it.

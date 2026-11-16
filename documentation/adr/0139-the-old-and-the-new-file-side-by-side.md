# 139. The old and the new file side by side

Date: 2026-11-16

## Context

The rich view (ADR 44) draws a diff unified: a bar for each file, the numbers of each line in the old and the new
file, the words that changed highlighted, the code coloured by its syntax (ADR 53), and the lines of a block shown in
pairs where they can be (ADR 83). Ctrl+D switches it with the plain view; the choice is kept as `ui.diff-style`
(`DiffStyle`, `DiffLook`). ADRs 44 and 76 planned side-by-side as another field of `DiffOptions`, which says how git
reads a diff: changing it reads the diffs again.

What the rich view draws is built of what was worked out when the diff was read (ADR 50): `ParsedDiff` holds the
segments of each line, which `WordDiff` compared first with first in each block of removed and added lines, and its
tokens. `RichDiff` draws it as one `Line` a row, each drawn when it shows (`Rows`), with the stops of the cursor
(ADR 60): each stop is the anchor of what it is, and the row it starts on. An anchor names a line by its side and its
number; a context line is a stop by its number in the new file, as GitHub takes it.

The area of the content panel scrolls sideways by cutting columns off the start of each line (`ContentText.windowed`).
The cursor is tinted over the rows that show (`DiffCursor.tinted`), and the matches of a search over those (ADR 126),
both by patching the lines drawn. `h` and `l` scroll sideways, `[` and `]` show more lines on a marker of lines a file
leaves out (ADR 77), and the arrows only leave the panel (ADR 118).

## Decision

### A third style

- `DiffStyle` gets `SPLIT`: the rich view with the old file on the left and the new one on the right. It is kept as
  `ui.diff-style = "split"`. Not a field of `DiffOptions`: it changes how a diff is drawn, not how it is read, and
  the split view reads a merge against its first parent, as the rich view does. Not a style of the plain view.
- `|`, a key that works everywhere, switches between the rich and the split view; from the plain view it goes to the
  split one. Ctrl+D switches between the plain and the rich view as it did; from the split view it goes to the plain
  one. Not: Ctrl+D through all three, which takes two keys from the split view back to the rich one.
- Every diff the rich view draws can be split: of a commit, a stash, the working tree and a pull request. A version of
  a file shown in place of its diff (ADRs 102, 103) and a conflicted file are drawn as the rich view draws them,
  under their bar, as wide as the area.
- The split view needs about 100 columns, so each half shows about 40 of code. In a narrower area the rich view is
  drawn, and the title of the area says why.

### The rows

- Each row is cut in two halves of the area, with a rule between them. A context line shows on both sides. A block of
  removed lines followed by added ones is lined up first with first, as `WordDiff` compares them, and the side with
  fewer lines is filled with blank rows to the end of the block, so the lines below stay level. That is so for a
  block whose lines were rewritten too: a row is not read as a claim that its two lines are one line changed, as a
  pair in the unified view is (ADR 83). A note of git's, like `\ No newline at end of file`, goes in the half of the
  line it follows. The rows are worked out by a pure function in `git.model` (`LinePairs.rows`).
- Each half numbers its line in its own file only. The gutter marks where a thread ends and what it covers, as in the
  rich view, in the half of the side of the thread.
- The bar of a file, a marker of lines left out, a thread about a whole file and the threads that cannot be placed
  are as wide as the area. The rule where a hunk starts is drawn in the gutter of each half.
- A thread on a line goes under its row, in the half of its side, as wide as that half.

### Scrolling sideways

- Both halves scroll sideways together, by as many columns, and their gutters stay. A `‹` at the start of a half, and
  a `›` at its end, mark code cut off there.
- A row is drawn with the whole code of each half, and the area cuts each half to its width, from the column it is
  scrolled to, when it shows (`Halves`). The tint of the cursor and of the matches of a search patch the line drawn
  before it is cut, so they move with the code. Not: drawing the rows again for each column.

### The cursor

- The cursor of the split view is on one side: only the half of its line is tinted. `[` takes it to the old side and
  `]` to the new one, on the same row, or the closest that has a line on that side. On a marker of lines left out,
  `[` and `]` show more lines, as they did. `h` and `l` still scroll sideways in every view.
- A context line on the left is a stop by its number in the old file: its anchor is on the left side. The unified
  view finds the stop of that line by either anchor, so the cursor keeps its line when the style changes: a removed
  line goes to the left, an added or a context line to the right.
- The cursor keeps to its side, so it does not jump across the screen as it moves. Only `[`, `]`, a click on a half
  and going to a match of a search take it to the other side. `j` and `k` pass over a row that holds no line on its
  side, as a row of only an added line on the left. On a stop as wide as the area, a bar or a marker, the cursor
  stays on the side it was on, and goes on there. A scroll that pulls the cursor along, with the wheel or a page,
  pulls it to the nearest line on its side. A diff shown anew starts on the new side. The side is kept by the area,
  not the model, and not with the preferences. Not: taking the side of the last line before a bar, which is the new
  side whenever that row has a line there, and sent the cursor across the screen.
- A click on a half puts the cursor on its line; on a blank half it does nothing.
- A range (`v`, ADR 60) keeps to the side it was started on, and holds the lines of that side between its ends, as
  the column shows them. While it is open, `[` and `]` do nothing, and a click or a match on the other side is
  outside it. The tint, a copy (`y`, ADR 94) and a thread started on it hold what the range holds. A range over the
  old and the new lines of a change is made in the unified view. Not: a range from one side to the other holding
  every line between its ends in the order of the diff, which the cursor would have to cross the screen for. The
  hunk a range is open in is not shown as git gives it: the split view puts no pair together that a range would have
  to take apart.

### Searching

- Both halves are searched (ADR 126). The matches of a row are in the order of its halves, the old one first. A
  context line is searched once, on the new side, as a match there and on the left would be the same text twice.
  Going to a match takes the cursor to its side and scrolls both halves so it shows.

## Consequences

- A context line is a stop on either side in the split view: a review thread can be started on the old side of one,
  as on GitHub.
- `DiffLines.between` holds the lines of one side when asked for the split view.
- A key, `|`, is taken everywhere; `[` and `]` act on lines too in the split view.
- The plain view has no split: `diff -y` laid out as git prints a diff would need its own rows and a cursor the
  plain view does not have.

# 140. Going to a line

Date: 2026-11-16

## Context

A line number comes from elsewhere: an error of the compiler or of a build, a remark in a review, a stack trace. The
content panel shows a file in many ways: a rich diff unified or split (ADRs 44, 139), the new version or the original
of the file whole (ADR 102), a version rendered as Markdown, JSON or YAML (ADRs 103, 122, 123), the plain diff, the log
of a branch, the body of a commit or a pull request. Finding line 212 means scrolling and reading the gutter, and in a
diff the line may not show at all: it lies in a marker of the lines left out (ADR 77).

Each stop of the cursor of a rich diff is an anchor: a line by its side and its number in the file of that side
(ADR 60). A context line is found by its number in either file (ADR 139). A row of JSON or YAML rendered is a stop
anchored to its node and the line it was drawn from (ADR 122); a block of Markdown rendered keeps the lines of the
source it was drawn from, which Ctrl+T scrolls to (ADR 103). `DiffStops.nearest` finds the closest stop of a line that
is gone.

`/` opens a line above the area to type a search in (ADR 126); `:` is not taken in the content panel, nor everywhere.

## Decision

### The key

- `:` in an area of the content panel opens a line above it, after a dimmed `:`, to type a line number in, as vim's
  `:42`. The number typed shows as it is typed. Only digits go in: another character is left out.
- Enter closes the line and goes to the line typed; Escape closes it and goes nowhere; an empty line goes nowhere.
  The area losing the focus closes it too.
- What is typed is kept by the area, not in the Model: unlike a search, nothing of it lasts past Enter, and no
  update reads it. Not: `42G`, as less and vim also have, whose count is not seen while it is typed.

### Which line a number is

- A line of the file the cursor is in: in a diff of several files, the file of the stop the cursor is on; on the
  lead of a commit, its first file.
- In the file of the side the cursor keeps to: the new file in the unified view, as a context line is numbered there
  (ADR 60); in the split view, the side of the cursor, as `[` and `]` take it there (ADR 139). A file with no line on
  that side, as a deleted file or the original shown whole, is numbered on its other side. Not: a prefix for the
  other side, which is left for later.

### Where it goes

- A line that is shown: the cursor goes to it, the view following it as `n` follows a match (ADR 126).
- In a rich diff, a line of the new file that a marker leaves out: the lines around it, five above and five below
  within that marker, are shown as Enter shows the lines of a marker (ADR 77), reading the file whole first; the
  cursor is on the line once it shows, and on the closest line shown until then. Not: going to the closest line
  shown, as search leaves the lines of a marker hidden: a search finds what it finds, but a line asked for by its
  number is the one wanted. Not: showing the new version in its place, which changes what the area shows.
- A line of the old file a marker leaves out, or one past the end of the file: the cursor goes to the closest line
  shown, and the title says so: `line 212 is not shown · at 215`.
- The new version or the original shown whole: every line is shown, so the cursor goes to it.
- Markdown rendered: the view scrolls the block drawn from the line to its top, as Ctrl+T leaves it, so Ctrl+T back
  to the source has the cursor on that block. JSON or YAML rendered: the cursor goes to the row drawn from the line;
  in a closed node, to that node, which stays closed, as a search opens none (ADR 126).
- While a range is open (ADR 60), a line outside its hunk is refused, and the title says why, as a match is.
- A search kept stays kept; the match gone to is let go of, as any other move lets go of it.

### Where it does nothing

- The plain diff, the log of a branch, the body of a commit or a pull request and the builds have no line numbers of
  a file: `:` opens no line there, and the title says so.

## Consequences

- `:` is taken in the content panel.
- The area keeps a second line it can open above itself, beside that of a search.
- Going to a hidden line reads the file whole, as expanding a marker does, and leaves those lines shown.
- The old side of a split diff gets no lines shown for a number: its markers are numbered in the new file.

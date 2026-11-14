# 126. Searching the content panel

Date: 2026-11-14

## Context

`/` filters the files pane (ADR 105) and the lists of popups, and searches the commits pane (ADR 4). The content panel
has no search: finding a name in a long diff, a file shown whole (ADR 102) or the body of a pull request means
scrolling and reading.

The content panel shows, in one area per pane, rich and plain diffs, a version of a file as source or rendered
(ADRs 103, 122, 123), the log of a branch, and the body of a commit or a pull request. The files pane has two areas
that show at once: the staged and the unstaged diff (`ContentArea`). What an area shows is kept as `Rows`: one
supplier per row, which draws it only when it shows (`ui/text/Rows.java`). Each row is drawn from text kept anyway: a
line of the diff, the path of a file, the body of a comment. A body has up to 50 000 lines (ADR 79).

A rich diff has a cursor that stops on its lines, the bars of its files and its threads (ADR 60). It is kept in the
Model as the anchor of its stop, worked out by the widget, which alone has the rows, and sent in `ContentScrolled`;
it is tinted over the rows that show (`DiffCursor.tinted`), not styled into the body. The other views only scroll.

In the diff of a pull request, `n` and `N` already go to the next and the previous thread.

## Decision

`/` in the content panel searches what it shows, and `n` and `N` go from one match to the next.

### The match

- A substring, ignoring case unless the query has an uppercase letter, as `ListFilter` matches. No regex, and not the
  first letters of words (ADR 106): that is for names, and in the text of a diff it finds what was not meant.
- A match lies within one line.
- It is looked for in the text shown: the lines, the paths in the bars of files, and the text of threads. Not in the
  gutter of a rich diff: line numbers and the `+` and `-`.
- Where a line of a hunk keeps its prefix as git printed it, it is left out of the match: as many columns as the
  header of its hunk has `@` signs less one, so one for `@@` and two for the `@@@` of a combined diff, of a conflicted
  file or a merge commit (`UnifiedDiff.conflicted` keeps those as printed in the rich view too). That is every hunk of
  the plain diff, which has no gutter. A query finds the same code in both views and in every kind of hunk. The `@@`
  headers and the `diff --git` and `index` lines of the plain diff are text like any other, and are searched.
- Only what shows is searched. A collapsed thread (ADR 62), a folded node of JSON or YAML (ADRs 122, 123) and the
  lines around a hunk not expanded (ADR 77) are not, nor are they opened to show a match.
- Every view of the panel is searched: rich and plain diffs, the versions of a file, the log of a branch, and the body
  of a commit or a pull request.

### Reading the text, not drawing it

- A row is searched in the text it is drawn from, kept next to its supplier in `Rows`: a line of the diff, the path of
  a bar, the body of a comment. Searching draws no row: no syntax, changed words or layout of threads.
- The matches of a body are found once per body and query, by the widget as it gets a new body, and kept with it: a
  scan of the text of up to 50 000 lines, without styling. Where the text of a row and what it shows differ, as a
  thread wrapped to the width of the area, the match is placed in the row when that row is drawn.

### The keys

- `/` opens a `FilterLine` above the area. Typing searches nothing yet: a first letter would match everywhere.
- Enter closes the line and searches, from the cursor in a rich diff, else from the top of the screen.
- Escape in the line puts back the search from before `/`: none, when there was none.
- `/` while a search is kept opens the line again with its query in it.
- While a search is kept, `n` and `N` go to the next and the previous match, in every view. In the diff of a pull
  request they then no longer go to threads; they do again once the search is cleared. The help says which they do.
- Past the last match `n` goes on at the first, and `N` past the first at the last; the title then says
  `search wrapped` until the next key.
- Escape in an area goes one layer at a time: it drops an open range (ADR 60) first, then clears the search, then does
  what it did before.

### Going to a match

- The widget, which has the matches, works out where `n` and `N` go, as it works out the moves of the cursor, and sends
  one `ContentScrolled` with the match gone to.
- In a rich diff the cursor goes to the stop holding the match, and the view follows it as ADR 60 has it: the cursor
  is never off the screen. In the other views the view scrolls until the match shows.
- When the match lies past the right edge, the area scrolls sideways until it shows.
- While a range of lines is open in the diff of a pull request (ADR 60), a match in its hunk reaches the range to it,
  as a click does. A match outside it is refused, and the title says why.

### The areas of the files pane

- The staged and the unstaged diff show at once. The matches are styled in both.
- `n`, `N`, the count and the current match belong to the area focused. Enter searches from its cursor, or from the
  unstaged area's when a pane has the focus.

### What shows

- Every match is styled `.search-match`, the one gone to `.search-current`. Both are laid over the rows that show, as
  the tint of the cursor is (ADRs 60, 78), over the styles of syntax, changed words and the cursor: neither a new
  query nor `n` styles the body again, and nothing is drawn for rows off the screen. Each theme gives both a value.
- The title of an area gets the query and which of its matches is current after what it says already:
  `Diff · /foo 3/17`. While none is current, as in a new body, it says how many there are: `/foo · 17 matches`. With
  no match it says `/foo · no matches`, nothing is styled, and the view stays where it is.

### How long it lasts

- There is one search for the panel, as vim has one last search, whichever area shows it.
- It stays while another item is highlighted, in any pane: its matches are found in what shows next, the view does
  not move, and `n` goes on from where the new content opens. The match gone to in an area is dropped when the area
  gets a new body, so none is current until `n`, `N` or Enter. A search with no matches in one file finds them in the
  next.
- It is kept per repository, and not for the next session.

### Where it lives

- In the Model: the query, and the match gone to in each area. In a rich diff that match is the anchor of its stop
  (`Anchor`, ADR 60) and which match it is within the stop, so it is found again after the body is styled again, as
  the cursor is; in the other views, the line and which match it is within the line. Their Msgs and update are in
  `state`, as commit search has; `state` never sees the rows (ADR 107).
- The matches themselves, their places in the rows, stay with the body in the widget.
- Finding the matches in the text of rows is a pure function, tested on its own.

### Why this way

- On Enter, not as you type: what is shown can be long, and a match after every letter jumps through all of it.
- Searching, not filtering as the files pane does: hiding lines breaks the hunks a diff is read in.
- `n` and `N` taken over while searching, not other keys: they are the keys of vim and less, and the threads keep
  them the rest of the time.
- What shows only: opening threads and folds for a match changes what the user set, and is left for later.
- The text rows are drawn from, not the rows drawn: a search kept while moving through files would otherwise draw
  every row of each diff highlighted, which `Rows` was made to avoid.
- The widget finds the matches and the Model keeps where they are, as with the cursor: the rows are `ui`, and an
  anchor survives a restyle where a row does not.

## Consequences

- `n` and `N` in the diff of a pull request do one of two things, by whether a search is kept.
- Escape in the content panel has one more layer.
- `Rows` keeps the text each row is drawn from beside its supplier.
- Each new body is scanned once while a search is kept, without styling: about as much work as reading its text.
- `ContentScrolled` carries the match gone to, as it carries the cursor.
- The themes get two classes.

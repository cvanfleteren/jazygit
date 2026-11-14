# 126. Searching the content panel

Date: 2026-11-14

## Context

`/` filters the files pane (ADR 105) and the lists of popups, and searches the commits pane (ADR 4). The content panel
has no search: finding a name in a long diff, a file shown whole (ADR 102) or the body of a pull request means
scrolling and reading.

The content panel shows, in one area per pane, rich and plain diffs, a version of a file as source or rendered
(ADRs 103, 122, 123), the log of a branch, and the body of a commit or a pull request. Each is styled once into lines
kept in memory (ADR 78), so a search needs no git process. A rich diff has a cursor that stops on its lines, the bars
of its files and its threads (ADR 60); the other views only scroll.

In the diff of a pull request, `n` and `N` already go to the next and the previous thread.

## Decision

`/` in the content panel searches what it shows, and `n` and `N` go from one match to the next.

### The match

- A substring, ignoring case unless the query has an uppercase letter, as `ListFilter` matches. No regex, and not the
  first letters of words (ADR 106): that is for names, and in the text of a diff it finds what was not meant.
- A match lies within one line.
- It is looked for in the text shown: the lines, the paths in the bars of files, and the text of threads. Not in the
  gutter: line numbers and the `+` and `-` of a diff.
- Only what shows is searched. A collapsed thread (ADR 62), a folded node of JSON or YAML (ADRs 122, 123) and the
  lines around a hunk not expanded (ADR 77) are not, nor are they opened to show a match.
- Every view of the panel is searched: rich and plain diffs, the versions of a file, the log of a branch, and the body
  of a commit or a pull request.

### The keys

- `/` opens a `FilterLine` above the area. Typing searches nothing yet: a first letter would match everywhere.
- Enter closes the line and searches, from the cursor in a rich diff, else from the top of the screen.
- Escape in the line puts back the search from before `/`: none, when there was none.
- `/` while a search is kept opens the line again with its query in it.
- While a search is kept, `n` and `N` go to the next and the previous match, in every view. In the diff of a pull
  request they then no longer go to threads; they do again once the search is cleared. The help says which they do.
- Past the last match `n` goes on at the first, and `N` past the first at the last; the title then says
  `search wrapped` until the next key.
- Escape in the panel clears the search only. The next Escape does what it did before.

### Going to a match

- In a rich diff the cursor goes to the stop holding the match, and the view follows it as ADR 60 has it: the cursor
  is never off the screen. In the other views the view scrolls until the match shows.
- When the match lies past the right edge, the area scrolls sideways until it shows.
- While a range of lines is open in the diff of a pull request (ADR 60), a match in its hunk reaches the range to it,
  as a click does. A match outside it is refused, and the title says why.

### What shows

- Every match is styled `.search-match`, the one gone to `.search-current`, over the styles of syntax, changed words
  and the cursor. Each theme gives both a value.
- The title gets the query and which match is current after what it says already: `Diff · /foo 3/17`. With no match
  it says `/foo · no matches`, nothing is styled, and the view stays where it is.

### How long it lasts

- There is one search for the panel, as vim has one last search, whichever area shows it.
- It stays while another item is highlighted, in any pane: its matches are styled in what shows next, the view does
  not move, and `n` goes on from where the new content opens. A search with no matches in one file finds them in the
  next.
- It is kept per repository, and not for the next session.

### Where it lives

- The query and the current match are in the Model, next to the cursor of the diff (ADR 60), with their Msgs and
  update, as commit search has.
- Finding the matches in the lines of a styled body is a pure function, tested on its own.

### Why this way

- On Enter, not as you type: what is shown can be long, and a match after every letter jumps through all of it.
- Searching, not filtering as the files pane does: hiding lines breaks the hunks a diff is read in.
- `n` and `N` taken over while searching, not other keys: they are the keys of vim and less, and the threads keep
  them the rest of the time.
- What shows only: opening threads and folds for a match changes what the user set, and is left for later.

## Consequences

- `n` and `N` in the diff of a pull request do one of two things, by whether a search is kept.
- Escape in the content panel has one more layer.
- The styled body of an area is styled again when the search changes; matches are found once per body and query, not
  per frame.
- The themes get two classes.

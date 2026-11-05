# 77. Showing more of one hunk

Date: 2026-11-05

## Context

ADR 76 gives every diff one amount of context. To see what one change sits in, more context for every hunk of
every file is too much: GitHub shows more lines above or below one hunk, where the user asks for them, and two hunks
that grow into each other become one.

The rich view draws a dotted rule between two hunks of a file (ADR 44). A hunk header gives where it starts in the old
and the new file and how many lines it has there, so the lines left out between two hunks, and above the first, can be
counted without reading anything; how many follow the last hunk cannot, as git does not say how long a file is.

## Decision

### Where the lines come from

- On the first expansion in a file, the diff of that one file is read again with the whole file as context
  (`DiffContext.WHOLE_FILE`), the way that diff was read: the working tree's staged or unstaged changes, a commit, a
  stash, or the files of a commit, a stash, a branch or a pull request, with the options of the moment. It is shown
  folded: the lines within the amount of context of a change, and those the user expanded.
- Not: reading the file at a revision (`git show rev:path`, the index, the file on disk) and numbering it against
  the hunks by hand, once for each kind of diff. Reading the diff again keeps ignoring whitespace, renames and untracked
  files as they are, and a file whose code is lexed whole is coloured better than one lexed per hunk.
- Folding the whole file at the amount of context gives the hunks git gives, so a file looks the same once it is read
  whole, and two hunks whose gap was expanded away are one: what is between two markers is one hunk.
- A file read whole is cut at the 5000 lines jazygit reads of a diff (ADR 76); a file longer than that cannot be
  expanded past the cut. A read that was cut tells no length: when the changes are all before the cut, the file is
  folded up to there, and the marker after its last hunk says `more` until the lines shown reach the cut, then that
  the file is too long to show more of; when a change is after it, every marker of the file says so, and the file
  shows as git gives it.

### Markers

- Between two hunks of a file, the rule says how many lines it leaves out: `┄ 42 lines hidden ┄`. Above the first
  hunk, when it does not start at the first line, and after the last one, there is a marker too. Until the file is read
  whole, the one after the last hunk says `┄ more ┄`, as nothing tells how many follow; once it is, it says how many,
  or goes when none do. An added or a deleted file has none: all of it is shown.
- A marker is a stop of the cursor (`Anchor.Gap`, by its path and the first line it leaves out in the new file), so
  `j` and `k` land on it. On a marker, `]` shows the next 10 lines below the hunk above it, `[` the 10 lines above the
  hunk below it, and Enter, or a click on it, all the lines it leaves out. The keys are a scope of their own,
  `Scopes.DIFF`, which `?` lists.
- While the file is read whole, the marker says `┄ loading ┄`; the lines asked for show once it is read, and the
  cursor stays on the marker, or on the one closest to it.
- Markers are only in the rich view: the plain one shows git's text.

### What an expansion is kept with

- The expansions are kept in `RepoView.expanded`, by the id of the area of the content panel, with what the area
  showed (`ScrolledContent`) and, per path, the file read whole and the lines expanded, by their numbers in the new
  file. One area has the expansions of one diff: expanding another diff in it drops those of the one before.
- They are shown while the area shows the same changes, read again or not (`ScrolledContent.sameChanges`), and for
  the working tree while it shows the same path. Not: dropped when another item is highlighted; coming back to the
  item before another one is expanded shows them again.
- Changing the options, the amount of context (ADR 76) or ignoring whitespace, drops all of them.
- When the diff of the working tree is read again, as after a file was saved (ADR 58), the files read whole are read
  again, and the lines expanded are kept by their numbers: after an edit they may be a few lines off. While a file
  read whole does not have the changes the diff shown has, the diff is shown as git gives it.
- There is no folding back: changing the amount of context, or another item, shows the hunks as git gives them.

### Review threads

- Threads are placed on the folded diff, so one on a line outside git's hunks is drawn on its line once the line is
  shown (ADR 55).
- A thread can be started on an expanded line, and on a range in one hunk of the folded diff: a range cannot cross a
  marker. GitHub's hunks keep three lines; a thread GitHub refuses says so, as with `-w` (ADR 60).
- Staging a hunk, once it is built (wanted.md), takes a hunk as it is shown: the lines between two markers.

## Consequences

- A file expanded once is read whole, parsed and lexed: an expansion in a long file waits for that.
- `Anchor` gets a fifth stop, the gap, which the cursor, the review's keys and `DiffStops.nearest` know.
- A hunk's header is read for its lengths too, not only for where it starts.

# 59. A cursor in the rich view, to comment on lines

Date: 2026-10-30

## Context

The review remarks of a pull request show next to their lines in the rich view of its files (ADR 55, ADR 57), but
they can only be read: replying to a thread, resolving it or starting one means going to the browser. ADR 55 left
these out, as the content panel cannot highlight a line or a thread.

The content panel only scrolls: `j`/`k`, `h`/`l`, Home and End move the view of a `richTextArea`, and where it is
scrolled to is kept in the model (`Msg.ContentScrolled`, `Scrolled`). `RichDiff.lines` gives styled lines only: once
drawn, nothing says which line of the diff, or which thread, a row is. The diff is styled again when the theme, the
diff or its remarks change, and once a minute for how long ago a comment was written (ADR 55).

`ReviewThread` has no id, nor have its comments, and the details of a pull request do not read its node id: GitHub's
mutations need them. `addPullRequestReviewThread` takes the pull request's id, a path, a line and a side, and a start
line and start side for one on several lines, or a `subjectType` of `FILE`; GitHub refuses a thread whose first and
last lines are in different hunks. `addPullRequestReviewThreadReply` takes the thread's id, `resolveReviewThread` and
`unresolveReviewThread` too. A thread says whether the viewer may reply to it, resolve it or unresolve it
(`viewerCanReply`, `viewerCanResolve`, `viewerCanUnresolve`).

The popup that comments on a pull request (`CommentDialog`, ADR 6) is a text area: Enter adds a line, Ctrl+S sends,
Ctrl+O opens it in the editor, Escape goes back; a failed send says why under the comment.

`wanted.md` (1) wants hunks and lines staged from the diff, which needs a line to be highlighted too.

## Decision

### The cursor

- Every rich diff has a cursor in the content panel: of a commit, a stash, the working tree and a pull request. Only
  that of a pull request's files can comment yet; elsewhere it only moves, for staging hunks and lines later
  (`wanted.md` 1). The plain view keeps only scrolling.
- It stops on each line of a hunk (added, removed and context), on the bar of each file, and on each thread as one
  stop, whether placed after its line, under the bar of its file, or in the list at the end of its file. Not: the
  blank lines, the preamble before the first file, what is before the files of a whole commit, a file's line saying
  why it shows no lines, the lines of a hunk header.
- `j`/`k` move it to the next or previous stop, and the view follows it when it reaches an edge; PageUp and PageDown
  move it a page, Home and End to the first and last stop. `h`/`l` still scroll sideways. The wheel scrolls the view,
  and pulls the cursor along to the nearest stop on the screen when it would leave it, as vim does: the cursor is
  never off the screen.
- It is kept in the model, next to where the panel is scrolled to, as the anchor of its stop: a line by its file, its
  side and its number on that side, a file by its path, a thread by its id. Each time the diff is styled again or read
  again, it is put back on its anchor, or on the nearest stop when the anchor is gone. Not: a row, which another
  styling moves; nor view state kept in the widget, as the panes' cursors are: where the panel is scrolled to is
  already in the model, and the cursor moves it.
- A diff starts with the cursor on its first stop, the bar of its first file: a comment on the whole file is one key
  away, the first line one `j`. A diff shown again, as when its node of the tree is highlighted again, puts it back on
  its last anchor while that is still in the diff, as its scroll is kept now.
- `RichDiff` gives, next to its lines, the anchor of each, or none: the stops are worked out with the lines, from the
  same placing of the threads (`PlacedRemarks`), and tested on their own.
- The row of the cursor is tinted as wide as its file, in the colour of the new class `diff-cursor`, which every theme
  has; `mono` and `none` reverse it. On a thread, the `┃` rule of the thread is reversed. The cursor is drawn only
  while the panel has the focus.

### Ranges

- `v` fixes one end of a range at the cursor, and `j`/`k` move the other; `v` again drops it, and so does Escape,
  which then gives the focus back to the tree only when no range is open.
- A range stays in its hunk: the cursor does not move past its first or last line while a range is open. So a range
  is in one file, and holds only lines, no bar nor thread. It may hold removed and added lines both, and start on the
  old side and end on the new one, as GitHub allows.
- Its rows are tinted in `diff-cursor`, and marked `┃` in the gutter, as the lines a thread covers are.
- A removed line is on the old side (`LEFT`), with its number in the old file; an added line and a context line on the
  new side (`RIGHT`), with their number in the new file, as GitHub takes a context line that is clicked.

### Keys

- In the content panel, on the diff of a pull request's files:
  - `c` starts a thread on the line of the cursor, on the range, or on the whole file when the cursor is on its bar;
  - `r` replies to the thread of the cursor;
  - `x` resolves the thread of the cursor, or unresolves it;
  - `Space` opens or closes the thread of the cursor;
  - `v` starts or drops a range;
  - `n` and `N` move the cursor to the next or the previous thread, and the view with it.
- A key the stop of the cursor does not take says why, as `put the cursor on a thread to reply`. The panel's help
  (`?`) lists them. `c` lists the files with remarks in the tree (ADR 57): the tree and the panel are other panes, as
  `j`/`k` already are in both.
- `r` and `x` work on every thread, also those listed at the end of a file, outdated ones too. A new thread is only
  started on a line the diff shows, so never on an outdated one.

### When it can comment

- Starting a thread, replying and resolving need the remarks read at the commits of the tree (`onLines`, ADR 55) and a
  pull request that is not merged: on another commit, a line number numbers another line than the diff shows. A
  closed pull request can be commented on, as on GitHub. Else the key says why: `the pull request moved on; R reads it
  again`, or that it is merged.
- A key the viewer has no right to says so before asking GitHub: replying, resolving or unresolving, as the thread's
  `viewerCanReply`, `viewerCanResolve` and `viewerCanUnresolve` say.
- With whitespace ignored (Ctrl+W), a range that is in one of git's hunks may be in two of GitHub's: it is sent all the
  same, and GitHub's refusal is shown in the popup. Not: refusing ranges while whitespace is ignored, for what is rare.

### Writing a comment

- `c` and `r` open a popup as `CommentDialog` does: Enter adds a line, Ctrl+S sends, Ctrl+O opens it in the editor,
  Escape goes back, and a failed send says why under the comment. Its title says where the comment goes,
  `src/Foo.java · lines 40–45`, or the file; above the text, it shows the lines of the range, or the last comment of
  the thread replied to. What both popups have alike is shared, not copied.
- Each comment is sent on its own, as soon as it is: a thread with `addPullRequestReviewThread`, a reply with
  `addPullRequestReviewThreadReply`. Not: a pending review, gathering comments to submit them with an approval or a
  request for changes, which would show one's own pending comments, left out on purpose (ADR 55) (`wanted.md`).
- `x` resolves or unresolves at once, with `resolveReviewThread` or `unresolveReviewThread`, without asking: `x` again
  undoes it. The thread says `resolving…` while it is sent; a failure is told as that of any other command.
- Once GitHub took it, the popup closes, and the remarks are read again as `R` reads them; the cursor then goes to the
  thread made or changed, by its id. While they are read, the cursor stays where it is.

### One thread open or closed

- `Space` opens or closes the thread of the cursor, against what `t` says for all: the threads opened or closed so
  are a set of ids kept with the tree, as `t` is, which `t` empties. Not: `t` only setting how a thread starts, which
  leaves no way to make them all alike again.

### Reading

- `ReviewThreadsQuery` reads the `id` of each thread and of each of its comments, and the thread's `viewerCanReply`,
  `viewerCanResolve` and `viewerCanUnresolve`. The ids of the comments are not used yet, but editing and deleting a
  comment will need them, and reading them costs a field of a query that is made anyway.
- The details of a pull request read its node `id` too, next to `baseRefOid` and `headRefOid`.
- Editing and deleting a comment are for later (`wanted.md`).

### Delivery

1. This ADR, and in `wanted.md`: a pending review, and editing and deleting a comment.
2. The anchors of `RichDiff`'s lines, and the cursor in every rich diff: its stops, the keys that move it, the wheel,
   `diff-cursor` in every theme, kept in the model and put back on its anchor, each tested on its own, with an
   acceptance test.
3. The ids and rights in `ReviewThreadsQuery` and the pull request's id in its details; replying, resolving, `Space`,
   `n` and `N`, and the guard on when it can comment, with an acceptance test against a fake gh.
4. Ranges, `c` on a line, a range and a file, and the popup shared with `CommentDialog`, with an acceptance test
   against a fake gh.

## Consequences

- The remarks of a pull request can be answered, resolved and started without the browser, one comment at a time.
- Every rich diff has a cursor where it only scrolled: `j`/`k` move it, not the view, which follows it.
- The cursor is kept in the model, unlike the cursors of the panes.
- A comment on a range that crosses GitHub's hunks while whitespace is ignored is refused by GitHub, not by jazygit.
- Reading the remarks reads a few more fields: the ids of threads and comments, and the viewer's rights.
- Staging hunks and lines (`wanted.md` 1) can build on the cursor and its ranges.

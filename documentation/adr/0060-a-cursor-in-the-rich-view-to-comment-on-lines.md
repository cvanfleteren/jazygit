# 60. A cursor in the rich view, to comment on lines

Date: 2026-10-30

## Context

The review remarks of a pull request show next to their lines in the rich view of its files (ADR 55, ADR 57), but
they can only be read: replying to a thread, resolving it or starting one means going to the browser. ADR 55 left
these out, as the content panel cannot highlight a line or a thread.

The content panel only scrolls: `j`/`k`, `h`/`l`, Home and End move the view of a `richTextArea`, and where it is
scrolled to is kept in the model (`Msg.ContentScrolled`, `Scrolled`), sent by the widget, which alone knows how high
the panel is. It has no keys of its own: no `Keymap.Scope`, so nothing it does is listed by `?`, and `R`, `t` and `c`
are keys of the tree (`Scopes.COMMIT_FILES`), which only gets them while the tree has the focus. `RichDiff.lines`, in
`ui`, gives styled lines only: once drawn, nothing says which line of the diff, or which thread, a row is; and
`state` may not depend on `ui` (`ArchitectureTest`). `ScrollArea` keeps the text it styled until the body or the
theme changes, as styling a rich diff colours its syntax and lays out its threads. The diff is styled again when the
theme, the diff or its remarks change, and once a minute for how long ago a comment was written (ADR 55).

A whole commit shows its header, its message and git's `--stat` before its first file (ADR 44), which can be taller
than the panel. A thread can be too, with a long comment or many replies. A conflicted file is drawn from a combined
diff whose lines are dimmed notes, with no side and no number (ADR 47).

`ReviewThread` has no id, nor have its comments. GraphQL's `addPullRequestReviewThread` and
`addPullRequestReviewThreadReply` add a comment to the viewer's pending review, which only they see until it is
submitted; REST's review comments are published at once, as GitHub's own "Add single comment": `POST
pulls/<n>/comments` with the head commit, a path, a line and a side, and a start line and start side for one on
several lines, or a `subject_type` of `file`, and `POST pulls/<n>/comments/<id>/replies` with the REST id of the first
comment of the thread. GitHub refuses a thread whose first and last lines are in different hunks. Resolving a thread
is GraphQL's only, `resolveReviewThread` and `unresolveReviewThread`, which take its node id. A thread says whether the
viewer may reply to it, resolve it or unresolve it (`viewerCanReply`, `viewerCanResolve`, `viewerCanUnresolve`).

The popup that comments on a pull request (`CommentDialog`, ADR 5) is a text area: Enter adds a line, Ctrl+S sends,
Ctrl+O opens it in the editor, Escape goes back; a failed send says why under the comment.

`wanted.md` (1) wants hunks and lines staged from the diff, which needs a line to be highlighted too.

## Decision

### The cursor

- Every rich diff has a cursor in the content panel: of a commit, a stash, the working tree and a pull request. Only
  that of a pull request's files can comment yet; elsewhere it only moves, for staging hunks and lines later
  (`wanted.md` 1). The plain view keeps only scrolling.
- It stops on each line of a hunk (added, removed and context) that has a side and a number, on the bar of each file,
  and on each thread as one stop, whether placed after its line, under the bar of its file, or in the list at the end
  of its file. What comes before the first file, the preamble or the header, message and `--stat` of a whole commit,
  is one stop too, the lead, which no key but the ones that move takes, and which is not tinted.
- Each stop holds its rows and the rows after it that are no stop, up to the next stop: the blank lines, a file's line
  saying why it shows no lines, the lines of a hunk header, the notes of a conflicted file, which have no side nor
  number (ADR 47), and the lines of a thread. So every row is in a stop, and can be reached.
- `j`/`k` move it to the next or previous stop, and the view follows it when it reaches an edge. When the rows of the
  stop it is on are not all on the screen, `j` and `k` first scroll through them, a row at a time, and only move on
  once the edge of the stop is shown: as vim does with a wrapped line higher than its window. So a thread or a lead
  higher than the panel is read with the keys, as now. PageUp and PageDown move a page, Home and End to the first row
  of the first stop and the last row of the last. `h`/`l` still scroll sideways. The wheel scrolls the view, and pulls
  the cursor along to the nearest stop with a row on the screen when it would leave it, as vim does: the cursor is
  never off the screen.
- It is kept in the model, next to where the panel is scrolled to, as the anchor of its stop: the lead, a line by its
  file, its side and its number on that side, a file by its path, a thread by its id. Not: a row, which another
  styling moves; nor view state kept in the widget, as the panes' cursors are: where the panel is scrolled to is
  already in the model, and the cursor moves it.
- The stops are worked out in `state`, next to `PlacedRemarks`, from the parsed diff and the threads placed in it, by a
  pure function tested on its own: the update reads them to know what the cursor is on, for the keys that act on it,
  `n` and `N`, and a range in its hunk. `RichDiff` gives the row each stop starts at, as it draws them. The widget,
  which alone knows the rows and how high the panel is, works out each move, as it works out a scroll now, and sends
  one `Msg.ContentScrolled` with where the panel is scrolled to and the anchor of the cursor, which `Scrolled` holds:
  the model never has a cursor off the screen, not for a frame. Not: a message for the cursor and one for the scroll.
- Each time the diff is styled again or read again, the cursor is put back on its anchor. When that is gone, as after
  Ctrl+W, a force-push read with `R`, or a thread deleted on GitHub: a line goes to the line of the same file and side
  with the closest number, else to the bar of its file, else to the first stop; a thread to the line it was on, and
  from there as a line. A diff without stops has no cursor, and the keys that act on it say why.
- A whole commit starts with the cursor on its lead, the top of the diff shown as now; a diff of files starts on its
  first stop, the bar of its first file: a comment on the whole file is one key away, the first line one `j`. A diff
  shown again, as when its node of the tree is highlighted again, puts it back on its last anchor while that is still
  in the diff, as its scroll is kept now.
- The rows of the cursor are tinted as wide as their file, in the colour of the new class `diff-cursor`, which every
  theme has; `mono` and `none` reverse them. On a thread, the `┃` rule of the thread is reversed. The cursor is drawn
  only while the panel has the focus. The tint is laid over the text `ScrollArea` keeps: only the rows of the cursor
  and of a range are drawn again, the diff is not styled again. Not: the cursor as part of what is styled, which would
  style a whole diff, syntax and threads, on each `j`.

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
  - `n` and `N` move the cursor to the next or the previous thread, and the view with it, from the last thread to
    the first and back;
  - `R` reads the remarks again, as in the tree.
- They are the bindings of a new scope, `Scopes.PULL_REQUEST_DIFF`, active while a content area has the focus on the
  diff of a pull request's files: `ScrollArea` gives it a key it did not take to move or scroll, through
  `Keymap.handle`. So `?` lists them, and a key bound twice is found as in the panes. Not: keys matched in `ScrollArea`
  itself, which neither does.
- A key the stop of the cursor does not take says why, as `put the cursor on a thread to reply`. `c` lists the files
  with remarks in the tree (ADR 57): the tree and the panel are other panes, as `j`/`k` already are in both.
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
- Each comment is sent on its own, and published as soon as it is: a thread and a reply with REST's review comments,
  a thread at the head commit of the tree, a reply to the first comment of its thread. Not: GraphQL's
  `addPullRequestReviewThread` and `addPullRequestReviewThreadReply`, which leave the comment pending in the viewer's
  review. Not: a pending review, gathering comments to submit them with an approval or a request for changes, which
  would show one's own pending comments, left out on purpose (ADR 55) (`wanted.md`).
- `x` resolves or unresolves at once, with `resolveReviewThread` or `unresolveReviewThread`, without asking: `x` again
  undoes it. The thread says `resolving…` while it is sent; a failure is told as that of any other command.
- While something is sent for a thread, a reply or its resolving, `x`, `r` and `Space` on it say it is being sent,
  and do nothing else: the ids of the threads being sent are kept in the model, until the remarks are read again or
  the send failed. Not: a second `x`, which would send the same again on what was read before, or race an unresolve
  against the resolve.
- Once GitHub took it, the popup closes, and the remarks are read again as `R` reads them. The cursor stays on the
  thread replied to or resolved; after a thread is started, it goes to its last line, which the thread is drawn right
  after: REST does not say which thread a comment started.

### One thread open or closed

- `Space` opens or closes the thread of the cursor, against what `t` says for all: the threads opened or closed so
  are a set of ids kept with the tree, as `t` is, which `t` empties. Not: `t` only setting how a thread starts, which
  leaves no way to make them all alike again.

### Reading

- `ReviewThreadsQuery` reads the `id` of each thread and of each of its comments, the REST id of each comment
  (`fullDatabaseId`), which a reply is sent to, and the thread's `viewerCanReply`, `viewerCanResolve` and
  `viewerCanUnresolve`. The node ids of the comments are not used yet, but editing and deleting a comment will need
  them, and reading them costs a field of a query that is made anyway.
- Editing and deleting a comment are for later (`wanted.md`).

### Delivery

1. This ADR, and in `wanted.md`: a pending review, and editing and deleting a comment.
2. The stops of a diff in `state`, the row of each in `RichDiff`, and the cursor in every rich diff: the keys that
   move it, through stops higher than the panel too, the wheel, the anchor in `Scrolled`, put back on its anchor or
   the nearest stop, and `diff-cursor` in every theme, laid over the styled text, each tested on its own, with a test
   that moving the cursor does not style the diff again, and an acceptance test.
3. The ids and rights in `ReviewThreadsQuery`; `Scopes.PULL_REQUEST_DIFF`
   with replying, resolving, `Space`, `n`, `N` and `R`, the threads being sent, and the guard on when it can comment,
   with an acceptance test against a fake gh that presses `x` twice.
4. Ranges, `c` on a line, a range and a file, and the popup shared with `CommentDialog`, with an acceptance test
   against a fake gh.

## Consequences

- The remarks of a pull request can be answered, resolved and started without the browser, one comment at a time.
- Every rich diff has a cursor where it only scrolled: `j`/`k` move it, not the view, which follows it, but scroll
  through what is higher than the panel.
- The cursor is kept in the model, unlike the cursors of the panes; the widget works out where it moves, as it works
  out a scroll.
- The content panel has keys of its own, which `?` lists.
- A comment on a range that crosses GitHub's hunks while whitespace is ignored is refused by GitHub, not by jazygit.
- Reading the remarks reads a few more fields: the ids of threads and comments, and the viewer's rights.
- Staging hunks and lines (`wanted.md` 1) can build on the cursor and its ranges.

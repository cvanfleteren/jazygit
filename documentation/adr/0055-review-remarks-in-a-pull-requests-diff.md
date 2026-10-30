# 55. Review remarks in a pull request's diff

Date: 2026-10-30

## Context

The pull request dialog says how many review threads a pull request has, and how many are not resolved (ADR 7), and
the comments popup shows its conversation (ADR 6). The remarks of a review, threads on a line of a file of the diff,
are not shown anywhere: reading them meant going to the browser. They are about a line, so they read best next to it.

The only diff of a pull request jazygit shows is that of the preview of the pull requests screen, read with
`gh pr diff` and drawn plain (ADR 47). GitHub refuses a diff of more than about 300 files or 20 000 lines there, and
the preview covers pull requests of any repository, most of them not checked out.

What GitHub shows as the changes of a pull request is a three-dot diff: its head commit against the merge base of
its head and base commits. git makes the same diff of a repository that has both commits: `git diff <base>...<head>`.
A thread is pinned to a line by its number in the old or the new file (`diffSide` `LEFT` or `RIGHT`), not to a hunk,
so its place does not depend on how the diff is laid out. A thread whose line changed after it was made is outdated:
GitHub no longer gives it a `line`, only its `originalLine` in a commit of the past.

`gh pr view --json` has no review threads; GraphQL does: `pullRequest.reviewThreads`, with the path, line, start line,
side and whether it is resolved, outdated or about a whole file, and its comments.

The rich view (ADR 44) draws a file's lines with their numbers in the old and the new file, which is what a thread
needs to find its line. The content panel does not wrap: a long line scrolls sideways. tamboui draws Markdown as a
widget (`MarkdownView`) or an element (`MarkdownElement`, which the comments popup uses); its layout into lines is
internal, and a code block or a table is laid out as a widget, not as lines.

## Decision

### Where

- The remarks show in a diff of the pull request read with git, in the repository that is open. Not: in the preview
  of the pull requests screen, whose pull requests are mostly of repositories that are not checked out; that keeps
  `gh pr diff`, drawn plain, without remarks (`wanted.md`).
- `f`, **Files changed** as GitHub names it, in the pull request dialog opens it: from a branch, or from the pull
  requests screen for a pull request of this repository, the only ones the dialog shows. It is there for an open pull
  request, a draft too, and for one closed without being merged, whose diff is what it was while it was open, once
  its details are read. Not: for a merged one, whose diff GitHub keeps as it was merged, while `base...head` no
  longer shows it: after a merge commit, the base took the head in and it is empty; after a squash or a rebase, the
  merge base is still where the head left the base, so it shows the changes of the pull request mixed with what the
  base got since, and `baseRefOid` no longer moves. Reading the diff as it was merged takes another commit for each
  way of merging (`wanted.md`).
- It works as `F` on a commit (ADR 44): the files pane lists the files the pull request changes as a tree, titled
  `Files of #123`, and the content panel shows the diff of the highlighted file or directory, titled
  `#123 · src/Foo.java`. The dialog closes; from the pull requests screen, the repository screen is shown. Escape
  closes the tree and gives the focus to the branches pane. Not: the whole diff of the pull request under its title
  and description, as a commit is shown: the tree always has a node highlighted, and the dialog and the comments popup
  show the rest.

### Reading the diff

- The details of the pull request read its base commit too, `baseRefOid`, next to its head commit, `headRefOid`.
- The files are read with `git diff --name-status -z -M <base>...<head>`, the diff of a node with
  `git diff -M <base>...<head> -- <paths>`, with `CliDiff.PREFIXES` and the diff options (Ctrl+W), parsed where it is
  read (ADR 50).
- When either commit is not in the repository, it is fetched first, without asking:
  `git fetch --no-write-fetch-head --refmap= <remote> refs/pull/<n>/head <baseRefOid>`. GitHub keeps
  `refs/pull/<n>/head` in the base repository, also for a pull request from a fork, and serves a commit it has by its
  sha. The base is fetched by its sha, not as `refs/heads/<base>`: the branch may have moved on since the details were
  read, and git would update `refs/remotes/<remote>/<base>` through the remote's fetch refspec. `--refmap=` leaves no
  fetch refspec to apply, so it writes no ref, not even `FETCH_HEAD` nor a remote-tracking branch, and it needs no
  guard of the remote refs (ADR 27). The remote is the one whose URL names the repository of the pull request; when
  none does, nothing is fetched, and the read fails as git says. It runs in the background, as it can take the
  network's time, while the files pane says it is loading.
- Not: `gh pr diff`, which has GitHub's limits, cannot be read file by file, and shows all whitespace.

### Reading the remarks

- `ReviewThreadsQuery`: `gh api graphql` on `pullRequest.reviewThreads`, a page of 100 threads at a time
  (`--paginate --slurp`), with their path, line, start line, side, original line, whether they are resolved or
  outdated and whether they are about a line or a file, and up to 50 comments each: who wrote it, when, its Markdown,
  and whether it is hidden and why. A thread with more says how many more there are. It reads the head and base commits
  of the pull request too.
- A comment of a review that is not submitted yet, pending, is left out, as only its author sees it (ADR 6); a thread
  of only those is left out.
- They are read when the tree opens, and again with `R` in the tree or its diff. When the head or the base commit
  changed, the files and the diff are read again too, at the new commits. Not: read every so often, which costs a
  GitHub request each time for what changes seldom.
- When `R` finds the pull request merged or closed, the tree stays open at the commits it last read, its remarks are
  read again, and its title says so: `Files of #123 · merged`. Not: closing it, as a dropped stash does (ADR 44): a
  stash's name then names another stash, while the number of a pull request still names it.

### Placing them

- A thread on a line goes after the line with its number on its side: `RIGHT` the number in the new file, `LEFT` in
  the old one, in the file with its path as GitHub gives it: the new path of a renamed file, on either side, and the
  old path of a deleted one. A file matches on its path or on the path it was renamed from. One on several lines (`startLine` to `line`) goes after its last line. A
  thread about a whole file goes under the bar of the file.
- A thread that cannot be placed goes in a list at the end of its file, dimmed, each saying the line it was made on:
  an outdated one, one on a line the diff does not show, as its file is cut off after `CliDiff.MAX_LINES` lines, or as
  its line is outside the hunks git made. Not: left out with a count, nor placed on its `originalLine`, which may now
  hold another line.
- This is a pure function of the parsed diff and the threads, tested on its own.

### Drawing them

- Only in the rich view. In the plain view, the line that starts each file says how many remarks it has, and that
  Ctrl+D shows them.
- A thread is a block after its line, starting two columns after the gutter, with a `┃` rule down its left in the
  colour of the new class `diff-thread`, which every theme has (`mono` and `none` too, `none` without a colour). Each
  comment starts with a line `author · 2 days ago`, then its text. The line a thread is on shows `●` in the gutter in
  `diff-thread`, and the other lines of a thread on several lines `┃`.
- A block wraps at a fixed width: the width of its file, at least 60 and at most 100 columns. Not: the width of the
  panel, which would make the styled text depend on it, and be styled again each time it changes.
- The text is Markdown, drawn by tamboui's `MarkdownView` into a buffer off the screen, as wide as the block and as
  high as it measures, whose cells are read back as styled lines; HTML comments are taken out first, as the comments
  popup does. It takes the theme's Markdown styles, those of `MarkdownElement`. This draws code blocks, tables and
  lists as the comments popup does, with public classes only. Not: plain text, nor tamboui's internal layout. It is
  drawn where the diff is drawn, with the rest of its styled text, which the content panel keeps until the diff, its
  remarks or the theme change: it depends on the theme's styles, which the thread that reads the diff does not know.
- A resolved thread starts collapsed, to one dimmed line: `✓ resolved · author: first line`. So does a hidden
  comment: `hidden · reason`. `t` opens every thread of the tree, and closes them again; it is kept in the model with
  the tree, and not in the settings.
- The files pane shows `💬2` after a file with unresolved threads, their count. A directory does not add up those of
  its files.

### Not now

- Replying, resolving and starting a thread: that needs the content panel to highlight a line or a thread, which it
  does not yet (`wanted.md`).

### Delivery

1. This ADR.
2. The tree of a pull request's files and its diff, read with git, without remarks: the base commit in the details,
   the fetch, the reads, `f` in the dialog, with an acceptance test.
3. `ReviewThreadsQuery`, and placing the threads in a parsed diff, each tested on their own.
4. Drawing them: the Markdown read back as lines, the blocks, the marks in the gutter, `t`, the list of threads that
   cannot be placed, the count in the plain view and in the tree, `R`, and `diff-thread` in every theme, with an
   acceptance test against a fake gh.

## Consequences

- The remarks of a review can be read next to their lines, without the browser, for a pull request of the repository
  that is not merged.
- Opening the tree can fetch: the commits of a pull request that were not fetched yet are fetched, without a ref.
- Opening it takes two more reads: the review threads from GitHub, and the files from git.
- A remark on a line that git's diff shows otherwise than GitHub's, as its hunks were cut elsewhere, is listed at the
  end of its file. So is a `LEFT` remark on a file git pairs with another file than GitHub does, when their rename
  detection differs: it has GitHub's new path, which git's diff does not have.
- The preview of the pull requests screen keeps its plain diff without remarks.

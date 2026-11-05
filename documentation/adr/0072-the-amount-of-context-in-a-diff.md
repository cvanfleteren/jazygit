# 72. The amount of context in a diff

Date: 2026-11-05

## Context

Every diff jazygit reads shows git's three lines of context around a change. To see what a change sits in, the user
leaves jazygit for an editor or for GitHub, which can show more lines around one hunk, or the whole file.

There are two ways to show more: one amount of context for every diff, as `git diff -U<n>` reads it, or more lines
around one hunk, as GitHub's arrows above and below a hunk show them. This ADR is the first; showing more of one hunk
is ADR 73, built on it.

`DiffOptions` already holds how diffs are read (ignoring whitespace, a merge against its first parent), and changing
it reads the diffs shown again (`DiffOptionsUpdate.changed`), dropping what was read with the options before
(`LoadUpdate.stale`).

## Decision

### One amount, for every diff, for the session

- The amount of context is a field of `DiffOptions`, `DiffContext`, which `CliDiffs.flags` reads as `-U<n>`. So it
  holds for every diff git reads: the working tree, staged and unstaged, a commit, a stash, the files of a commit, of
  a branch and of a pull request.
- It is the same for all of them. Not: an amount per pane, which would be state the user cannot see.
- It is kept for the session only, as ignoring whitespace is (`Preferences`), and starts at 3. A wider context is
  wanted to look at one thing, not to keep. It can become a setting when it turns out to be.
- jazygit always passes `-U3` until the user changes it. Not: git's own `diff.context`, which jazygit would have to read
  to step from it, for an amount the user can change in one key anyway.
- The diff of a pull request on its screen (`gh pr diff`) keeps GitHub's three lines: `gh` has no flag for it, and that
  screen is to be reworked.

### Steps

- `}` shows more context and `{` less, anywhere outside a dialog, as Ctrl+W does.
- They step along 0, 1, 3, 5, 10, 20, 50 and the whole file. Not: one line a step, which takes many keys to see a
  function around a change. Zero shows the changed lines only.
- The whole file is a context larger than any file jazygit reads, `-U1000000`: git then shows each changed file as one
  hunk. A file longer than the 5000 lines jazygit reads of a diff is cut there, as any diff is.
- `}` on the whole file and `{` on zero change nothing, and read nothing again.

### What shows

- A title of a diff says the amount when it is not 3, as it says that whitespace is ignored: "Diff · 10 lines of
  context", "Diff · the whole file".
- Changing the amount reads the shown diffs again, as toggling whitespace does: they keep showing until they are read.
  The cursor and the scroll are kept by their anchor, a line by its number (ADR 60), so they stay on the line they
  were on.
- A review thread is placed on its line by number (ADR 55), so a thread on a line outside git's hunks, listed at the
  end of its file, is drawn on its line once the context shows it.

## Consequences

- A whole file is read, parsed and coloured as one hunk; a long file reads slower than its changes alone.
- With more context, two hunks closer than twice the context are one, so the cursor's range and a review thread can
  span what were two of git's hunks. GitHub's hunks keep three lines; a thread GitHub refuses says so, as with `-w`
  (ADR 60).
- `DiffOptions` gets a third field. Side-by-side, the other diff option still to do, would be a fourth.

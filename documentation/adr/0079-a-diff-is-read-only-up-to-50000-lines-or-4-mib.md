# 79. A diff is read only up to 50 000 lines or 4 MiB

Date: 2026-11-05

## Context

A diff is cut off after `CliDiffs.MAX_LINES` lines, 5000, and so is the diff of a pull request of another repository
(`PreviewQuery.MAX_DIFF_LINES`). That kept a frame short while every frame cost as much as the diff was long; since
an area is given only the lines that show (ADR 78), a frame costs about the same however long the diff is. What a
longer diff still costs is reading it, styling it once and keeping it, all of which grow with how much text it has,
not with how many lines:

| diff                    | lines  | text    | read   | styled | kept   |
| ----------------------- | ------ | ------- | ------ | ------ | ------ |
| code                    | 50 000 | 3.3 MB  | 92 ms  | 39 ms  | 104 MB |
| an added file           | 50 000 | 4.0 MB  | 46 ms  | 44 ms  | 117 MB |
| lines of 2000 characters| 5 000  | 9.4 MB  | 302 ms | 33 ms  | 41 MB  |
| lines of 2000 characters| 50 000 | 94.5 MB | 1.7 s  | 228 ms | 400 MB |

Lines alone are the wrong measure: 5000 lines of a data file cost more to read than 50 000 lines of code.

And the cut only came after all of git's output was read, into memory several times over: as bytes, as a string, and
as a list of its lines. A large file added by mistake, like a dump of a database, makes a diff of hundreds of MB or
more. That ran out of time (30 s) after a spike of memory, or ran out of memory; and an `OutOfMemoryError` that
struck zt-exec's own thread was printed over the TUI, and one that struck while the failure was reported left the
diff loading for good. A diff within the cut could still overflow the stack: `UnifiedDiff` recursed once per file and
once per hunk, and a diff of 50 000 lines read with `-U0` has tens of thousands of hunks.

## Decision

- A diff is cut off after 50 000 lines, or before the line that would make it longer than 4 MiB of characters,
  whichever comes first; always after a whole line. The same for the diff of a pull request in its preview. A first
  line longer than that leaves nothing of the diff but the note that it is truncated, which is as before.
- Git's output is read only up to there (`Exec.read`, with a `Limit`): it is read as UTF-8 text while git prints it,
  and once it is longer than the limit, git and what it started are stopped, and the rest is never read. Its exit
  code then says nothing.
- Every other program's output is read up to 64 MiB, far more than is ever meant to be; more is a failure, "printed
  too much to read", not a cut. Of the error output only the first 64 KiB is kept: what went wrong is said at its
  start.
- Two diffs shown as one, the tracked and the untracked files of a stash, are each read up to the limit, and cut off
  again as one.
- `UnifiedDiff` is unfolded, not recursive.
- A command that throws also when it reports that it failed is still done. What a thread does not catch goes to the
  bug log.
- Not: a limit in bytes. Git's output is read as text, and the characters are what is parsed, compared and styled.
- Not: parsing the diff while it is read. It would save one copy of at most 4 MiB, but the parser, the budget of the
  word diff (ADR 50) and the lexer, which reads a file at a time, would have to work a line at a time.
- Not: a limit on the heap. What is read is bounded instead; a smaller heap would only run out sooner.
- Not: the size of a file looked at before it is diffed. A large file with a small change has a small diff, and many
  files that are each small make a large one.

## Consequences

- A diff of code is shown in full up to about 50 000 lines: about 100 ms to read off the render thread, about 40 ms
  to style, once, and about 100 MB kept while it is shown.
- A diff of long lines is cut off sooner than before: 2000 lines of 2000 characters, where it was 5000.
- A diff of any size is read in about the time and memory of 4 MiB of it, and shown cut off.
- Large output that is not a diff, like the status of a working tree with a million untracked files or a long
  reflog, fails at 64 MiB rather than taking the memory; it is not cut off.
- Staging a hunk, once there is a way to (`wanted.md`), has to refuse a hunk of a diff that is truncated: its last
  hunk may have fewer lines than its header says.

Amends ADR 78, which left `CliDiffs.MAX_LINES` as it was.

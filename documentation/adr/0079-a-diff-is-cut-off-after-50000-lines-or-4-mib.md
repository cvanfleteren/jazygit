# 79. A diff is cut off after 50 000 lines or 4 MiB

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

## Decision

- A diff is cut off after 50 000 lines, or before the line that would make it longer than 4 MiB of characters,
  whichever comes first; always after a whole line. The same for the diff of a pull request in its preview.
- The note that it is truncated is as before.
- Not: a limit in bytes. Git's output is read as text, and the characters are what is parsed, compared and styled.

## Consequences

- A diff of code is shown in full up to about 50 000 lines: about 100 ms to read off the render thread, about 40 ms
  to style, once, and about 100 MB kept while it is shown.
- A diff of long lines is cut off sooner than before: 2000 lines of 2000 characters, where it was 5000.
- The whole output of git is still read before it is cut off.

Amends ADR 78, which left `CliDiffs.MAX_LINES` as it was.

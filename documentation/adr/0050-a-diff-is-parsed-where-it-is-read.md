# 50. A diff is parsed where it is read

Date: 2026-10-29

## Context

The rich view (ADR 44) parses a diff and compares the words of its changed lines where the diff is drawn, on the
render thread: the content panel does it each time the body it shows changes. `WordDiff.BUDGET` bounds the
comparisons so that a diff of many long lines does not hold the screen up for seconds, yet drawing a diff of
`CliDiff.MAX_LINES` lines still holds it up for one frame of up to about 120 ms (`RichDiffBenchmark`), half of which
is parsing and comparing.

## Decision

- A diff is parsed and its words compared where its `DiffText` is made: on the thread that read it from git.
  `DiffText` carries a `ParsedDiff`, the `UnifiedDiff` and the segments of the lines of each hunk; the rich view only
  styles it. Not: a command of its own, run once the diff is read, which makes the model wait for a second result
  and show the diff once unparsed or not at all in between.
- What the content panel adds after a diff, the line that says it is truncated, or the one that says only whitespace
  changed, is added to the `ParsedDiff` as the note it would have parsed to (`ParsedDiff.withNote`), so the rich
  view draws it as before without parsing the diff again.
- Every diff read is parsed, also while the plain view is chosen, so that switching views (Ctrl+D) does not read it
  again.

## Consequences

- Drawing a diff in the rich view holds up the render thread for about half as long: 2.9 ms instead of 6.3 for a
  commit of 100 files, 65 ms instead of 116 for 4000 long, similar lines. What is left is styling the lines, most of
  it measuring how wide they are.
- `git-io` and the background threads spend what the render thread no longer does: 2.7 ms for the commit of 100
  files, 49 ms for the long, similar lines. A diff read again unchanged, after a refresh, is parsed again.
- The plain view pays for the parsing too.
- The budget no longer bounds how long a frame takes, only how long reading a diff takes; it is not raised here.

Amends ADR 44, whose comparisons ran where the diff is drawn.

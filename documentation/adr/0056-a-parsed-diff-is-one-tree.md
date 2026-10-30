# 56. A parsed diff is one tree

Date: 2026-10-30

## Context

`ParsedDiff` (ADR 50) held the diff parsed, a `UnifiedDiff`, and beside it the segments of each line of each hunk
(ADR 50) and its tokens (ADR 53): three lists in the shape of the hunks and their lines, kept in step by their order
only. The rich view put them back together where it drew a diff: it cut the segments and the tokens of all hunks into
those of each file, and zipped them with the hunks and the lines, together with the widths of the lines and the review
threads on them (ADR 55). Each zip nested a tuple in the one before, and the drawing code read
`line._1._1._1._1`.

## Decision

- A `ParsedDiff` is one tree: its preamble, and its files (`ParsedFile`), each with its hunks (`ParsedHunk`), each with
  its lines (`ParsedLine`), and each line with its segments and its tokens. Each part wraps the part of the
  `UnifiedDiff` it is in, so a line is drawn from one value.
- The words of all hunks are still compared at once, in order, as they share one budget (ADR 51), and then given to
  their files where the tree is made (`ParsedDiff.of`). `ParsedDiff.withNote` adds the note to the tree.
- `ParsedDiff.diff()` gives the diff without its words and tokens, for what reads the diff only: the review threads
  are placed on it (`PlacedRemarks.place`), and the plain view counts them on its files.
- What only the view knows, the width of a line and the threads on it, is not in the tree: `jazygit-git` does not
  know how wide the terminal draws a text, nor the review threads of a pull request. The rich view pairs a line with
  them where it draws it.

## Consequences

- A line, its words and its tokens cannot get out of step: each `ParsedLine` holds them.
- Each part of the tree also holds the part of the diff it wraps, which holds the parts below it: a `ParsedFile` has
  its `FileDiff`, whose hunks are those of its `ParsedHunk`s. Only `ParsedDiff.of` and `withNote` make a tree, and
  `ParsedDiffTest` checks that both keep them alike.
- Two `ParsedDiff`s are equal when their trees are, which compares each line as often as it is wrapped; `DiffText`
  still leaves its `ParsedDiff` out of its equality (ADR 50).

Amends ADR 50, whose `ParsedDiff` held the segments beside the diff, and ADR 53, whose `ParsedDiff` held the tokens
beside it.

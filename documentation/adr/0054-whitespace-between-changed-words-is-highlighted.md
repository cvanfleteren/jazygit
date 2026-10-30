# 54. Whitespace between changed words is highlighted

Date: 2026-10-30

## Context

`WordDiff` splits a pair of lines into words, runs of whitespace and single other characters, and highlights the
tokens outside their longest common subsequence (ADR 44). In a phrase that was rewritten within a line that was not,
the words differ but the single spaces between them are found in the other line too, so they are kept. Each changed
word is then a block of its own, with a gap of the line's colour after it: a changed sentence reads as a row of
blocks, one per word.

## Decision

- Whitespace kept between two changed tokens is highlighted as changed, so changed words with only whitespace
  between them are one segment. A run of whitespace is one token, so the gap is always a single token.
- Only whitespace is bridged. Not: also a short gap of punctuation, like `, `, which would highlight characters that
  did not change, more often in code than in prose, and needs a rule for which characters and how many.
- Whether a pair was rewritten is decided before, on the tokens the comparison keeps, so bridging highlights no line
  that was drawn whole before.
- Each line is bridged on its own: a space bridged in the removed line may sit between kept words in the added one.

## Consequences

- A rewritten phrase is one block per line instead of one per word.
- `Segment.changed` no longer only means that a part is not in the other line: it is also whitespace between two
  parts that are not.
- Bridging is a pass over the tokens of a compared pair, and spends none of the budget.

Amends ADR 44, which highlighted only the tokens outside the longest common subsequence.

# 51. A rewritten line spends none of the word budget

Date: 2026-10-30

## Context

The changed words of a pair of lines are found by filling a table of the tokens of both lines (ADR 44), and the
comparisons of a whole diff share `WordDiff.BUDGET` cells. A pair of which less than half of the characters is kept
was rewritten, and is drawn whole, but only once its table is filled. A diff of many rewritten lines spent the whole
budget on tables it then threw away, and the lines after them that changed in a word or two were drawn whole too:
of 2000 rewritten pairs followed by 500 pairs changed in two words, 2 lines were highlighted.

The longest common subsequence keeps no token more often than both lines have it. Counting the tokens both lines
have, in whatever order, bounds what it can keep, without a table.

## Decision

- Before filling the table of a pair, `WordDiff` counts the tokens both lines have between what they start and end
  with alike. A pair that could not keep enough to be highlighted even on that count was rewritten: it is drawn
  whole, and spends none of the budget.
- A pair that passes the count is compared and spends its cells as before, also when the comparison then finds too
  little kept, as for a line whose words moved. Not: skipping those too, which would let a diff of shuffled lines
  get around the budget.
- The count is skipped for a table of no more cells than it has tokens to count.

## Consequences

- The lines after a run of rewritten lines are highlighted: 1000 lines instead of 2 in the case above, which takes
  9.6 ms instead of 7.7, the comparisons the rewritten lines used to starve.
- A diff of rewritten lines alone is checked in full, and is a little faster: 6.3 ms instead of 7.2.
- Counting costs a map of the tokens of each pair that is compared.

Amends ADR 44, whose every comparison drew on the budget.

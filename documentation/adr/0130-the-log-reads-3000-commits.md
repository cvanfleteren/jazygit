# 130. The log reads 3000 commits

Date: 2026-11-14

## Context

`History.MAX_COMMITS`, 300 since ADR 4, caps the log of HEAD, the log of a branch, each side of a divergence, the
walk for the commits ahead of the base (ADR 20) and a search. Past the first 300 a commit cannot be scrolled to, so
an older one needs a search.

Reading more from git is cheap, but laying out the graph was not (#493). On git.git, 10000 commits with 2693 merges
and a graph up to 512 rails wide took 3.5 s to arrange and 3 s to lay out, and every key press in the commits pane
took 70 to 230 ms, as TamboUI measured each of the 10000 rows on every frame. The causes, and what was done about
them:

- `CommitGraph.arrange` walked everything below each merge twice, through persistent sets, to find what it brought
  in. It now knows a commit by its position, and makes one pass down from a merge that marks what the first parent
  and what the others reach, stopping once nothing only the others reach is waiting: a merge costs about as much as
  the branch it brought in. Where `git log` lists a parent before its child, as with clocks that were off, the pass
  goes in an order with each commit before its parents.
- `CommitGraph.layout` looked up, for each rail, the leftmost rail waiting for the same commit, and the commits
  pane, and `LogGraph` for the log of a branch in the content panel, looked up each column in the lists of rails:
  both the square of the rails per row. Each row now works this out once, on arrays of its own.
- The rows of a list tell TamboUI their height, so it no longer measures the text of the rows it does not draw.

The arrays are local to a call and never escape it: from outside, the functions stay pure.

## Decision

`MAX_COMMITS` is 3000. On git.git, measured outside the app with the JIT warmed up:

| commits | arrange: before | after | layout: before | after |
|---|---|---|---|---|
| 1000 | 35 ms | 2 ms | 20 ms | 15 ms |
| 3000 | 566 ms | 5 ms | 343 ms | 50 ms |
| 10000 | 10.7 s | 12 ms | 7.4 s | 0.4 s |

At 3000 a new HEAD costs under a tenth of a second of graph work on a history with many merges; drawing the gutters
of the log of a branch of 3000 commits takes 54 ms, where it took 675 ms. 10000 still
costs about half a second, on the render thread, so it waits for a layout that does less per row.

## Consequences

- Scrolling reaches ten times further back before a search is needed; a search finds up to 3000 commits, and the
  title then says `(first 3000)`, and the commits pane says `3000+ ahead`.
- The graph of a long history with many merges can be wide: hundreds of rails, cut off by the pane.
- `CommitGraph` and `LogGraph` keep a slow, plain version of themselves in their tests, which the fast one is checked against on random
  histories.

# 30. Move commits back onto the branch's own line

Date: 2026-10-25

## Context

Move commits (ADR 1) moves the selected commit and every commit above it, and moves the branch back to the parent of
the selected one. The commits pane lists every commit reachable from the branch, also those of a branch merged into
it, and any of them could be selected:

```
* C            ← main
*   M          merge of side
|\
| * S2         ← selected
| * S1
* | B
|/
* A
```

The range, `main ^S2^`, then holds C, M, B and S2: B moves along, though it is on the other side of the merge. As it
holds a merge, only a new branch at the parent is offered (ADR 1), and `main` is moved back to S1: a commit of the
merged branch, that was never its tip. Nothing is lost, as the new branch has it all, but `main` ends up somewhere
the user did not choose, and nothing said so.

What matters is where the branch is moved back to, not the selected commit. Selecting S1 moves `main` back to A, on
its own line: the range, `main ^A`, is the one selecting B moves, and that is fine.

A commit on the merged side is only reachable from the branch through a merge, so such a range always holds one.

## Decision

A commit whose parent is not on the line of the branch itself, which its tip follows through first parents, is not
moved. The error dialog says why, and what to pick instead:

> `a1b2c3d` can't be moved: it came into `main` through the merge `e4f5a6b`. It isn't one of `main`'s own commits, so
> moving it would leave `main` on `9c8d7e6`, a commit of the merged branch. Pick `e4f5a6b` or a commit above it, or
> one on `main`'s own line.

- It names the merge, so the rule can be found in the graph, and the commit `main` would be left on, so it is clear
  what moving it would do.
- The check is on the range that is read anyway (`git rev-list --parents`, now with `--topo-order`): no other git
  process. The commits on the line follow first parents from the tip, within the range. The line leaves the range at
  one commit, the first parent of the last commit on it, and the parent of the selected commit is on the line only
  when it is that commit. The merge named is the last commit on the line that has the selected commit as an
  ancestor.
- It is refused when the range is read, before any dialog opens.
- A merge on the line of the branch itself still moves, to a new branch at the parent (ADR 1): selecting B moves B,
  M and C, with S1 and S2 through M, and moves `main` back to A.
- The first commit of a merged branch moves as well: selecting S1 moves the same commits as selecting B.

### Considered

- **Refusing a selected commit that is not on the line itself.** S1 is not, so it would be refused, and the message
  would call A a commit of the merged branch, though moving S1 leaves `main` on its own line.
- **Refusing every range that holds a merge.** It fixes this too, but also refuses moving a merge on the branch's own
  line, which works as ADR 1 has it.
- **Reading the range with `--first-parent`**, so the branch is always moved back onto its own line. Then the
  selected commit would not be in what moves, and moving it would mean something else than what was selected.
- **Offering to move from the merge instead.** Friendlier, but it guesses what was meant; it can come later.
- **Checking on the commits loaded in the commits pane**, before any git process. The first parents up to the
  selected commit need not all be loaded, and the log is not read in topological order.

## Consequences

- The branch is only ever moved back onto a commit of its own line.
- A commit of a merged branch other than its first can no longer be moved out of it with this command; moving from
  the merge does that, with the commits of the merged branch.

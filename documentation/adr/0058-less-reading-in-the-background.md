# 58. Less reading in the background

Date: 2026-10-30

## Context

ADR 56 (`0056-fewer-git-processes-after-a-commit.md`) cut what a commit costs. Two things in it did not hold:

- Its decision 3 read the parents of the branches by a `LoadParents` of their own, guarded by a stamp of the config,
  and gave them to `LoadBaseDistance`. The branches and the parents then each asked for the distances, and came back
  in either order: at the start, one of the two counts was thrown away, and every count was checked against the load
  the model would make now.
- Its decision 2 stamped the worktrees by their HEAD files. A reftable repository leaves those as stubs git never
  rewrites, and keeps each HEAD in its tables: the main worktree's with the branches, a linked one's in tables of its
  own. A checkout there did not list the worktrees again.

## Decision

1. **The branches, their parents and their distances are read by one load, into one message.** `LoadBranches` reads
   the branches, then the parents, then counts the distances (ADR 46) against the tips and the main branch of the
   branches it just read, and comes back with all three in `BranchesLoaded`. `LoadParents` and `LoadBaseDistance` are
   gone, and with them the stamp of the config and the check that dropped an outdated count. When the parents cannot
   be read, the branches come back without them, and the parents and distances read before stay, as a failed count
   did. The parents are read with every read of the branches again: a commit takes 7 git processes, not 6, but no
   two loads race over what the distances go by. The config stays in the stamp of the branches, so a parent jazygit
   records reads the branches again.
2. **A reftable repository stamps its tables for the worktrees.** The stamp of the worktrees holds the tables of the
   repository and those of each linked worktree; the stamp of the branches holds those of each linked worktree too.
   There a commit, which writes the tables of the branches, lists the worktrees again.

The other chains of loads stay as they are: the commits ahead of HEAD go by the HEAD of the status and the bases of
the branches, read by loads of their own, and the log of the highlighted branch, the divergence and the operations
run when the user asks. Those keep the checks of ADR 49.

## Consequences

- `BranchesLoaded` holds the parents and the distances, when they could be read. `AllBranches` tells the tips and
  the main branch the distances go by.
- The branches show only once their distances are counted, one `for-each-ref` later than before.

Amends ADR 56 (`0056-fewer-git-processes-after-a-commit.md`).

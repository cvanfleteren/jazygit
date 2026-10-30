# 57. Fewer git processes after a commit

Date: 2026-10-30

## Context

ADR 49 brought a commit down to 9 or 10 git processes. It counted one too few: `git worktree list` runs too, as
the stamp of the worktrees (ADR 15) holds that of the branches, which a commit changes. So a commit costs 10 or 11:

| Load | git processes |
|---|---|
| `LoadStatus` | `status` |
| `LoadCommits` | `log` |
| its `LoadAhead` | `log tip ^base`, `rev-list --count` when the log is full, `merge-base`, `log --no-walk <fork>` |
| `LoadBranches` | `log --walk-reflogs`, `for-each-ref` |
| its `LoadBaseDistance` | `config --get-regexp`, `for-each-ref %(ahead-behind:…)` |
| `LoadWorktrees` | `worktree list` |

Some of these read what a commit cannot have changed, or what an earlier process of the same read already says
(issue #262):

- `git log --boundary` also lists the commits on the base at which it stopped, marked `-` by `%m`. Every merge-base
  of the tip and the base is one of them, when `--max-count` did not cut the walk short: on a path from the tip to a
  merge-base, the commits before it are not on the base, or one of them would be a better common ancestor. ADR 49
  turned `--boundary` down for the walks cut short.
- One commit more than are shown tells whether there are more, without a `rev-list --count`.
- A commit on a branch moves the branch, not the HEAD that points to it, nor the config. Looked at on a commit, in the
  main worktree and in a linked one, the HEAD files, `.git/config` and `.git/logs/refs/heads` keep the time they were
  modified at. That holds for the files backend: a reftable repository leaves its HEAD files as stubs git never
  rewrites, and keeps each HEAD in its tables, the main worktree's in the tables of the branches.
- `git worktree list` reads what each worktree has checked out from its HEAD; the commit it is at is the one thing
  that moves with a commit, and no view shows that commit of a worktree on a branch.
- The distances to the bases (ADR 46) go by the branches and by the parents (ADR 49), and are read by a load of their
  own once the branches arrive. The loads that go by what other loads read come back in any order, so each of their
  results is checked against the load the model would make now, and dropped when it is outdated. Reading the
  parents on their own as well, when only the config changed, would make one more such pair: at the start, the
  branches and the parents would each ask for the distances, and one of the two counts would be thrown away.

## Decision

A commit reads what it changed, in as few processes as git allows. What jazygit shows does not change.

1. **The commits ahead of a base are read with one `git log --boundary --max-count=<max + 1>`.** A walk of more than
   `max` commits says there are more, and shows `max` of them. Where the walk was not cut short, the boundary commits
   tell the fork point:
   - one boundary commit is the fork point, read in full by the same process;
   - none, with commits walked, says the histories never met;
   - no commit walked says the tip is on the base, and the fork point is the tip itself: one `log --no-walk`.

   Several boundary commits, as when the base was merged into the branch, or a walk cut short, ask `git merge-base`
   and `log --no-walk` for the fork point, as before.
2. **The worktrees are listed again when a HEAD changes, not a branch.** Their stamp holds the HEAD of each worktree,
   the directory of the worktrees, and the `gitdir` and `locked` of each, no longer that of the branches. The commit of
   a worktree on a branch is where that branch was when the worktrees were listed. In a reftable repository the stamp
   holds the tables too, the main ones and those of each linked worktree, as its HEADs are there: there a commit, which
   writes the tables of the branches, lists the worktrees again.
3. **The branches, their parents and their distances are read by one load, into one message.** `LoadBranches` reads
   the branches, then the parents, then counts the distances against the tips and the main branch of the branches it
   just read, and comes back with all three in `BranchesLoaded`. `LoadBaseDistance` is gone. What the model holds of
   them was read together, so no result is checked against what the model would ask for now, and the start counts the
   distances once. When the parents cannot be read, the branches come back without them, and the parents and distances
   read before stay, as a failed count did (ADR 46). The parents are still read with every read of the branches: a
   stamp of the config alone would save that process on a commit, but only with the parents read by a load of their
   own again. The config stays in the stamp of the branches, so a parent jazygit records reads the branches again.

The other chains of loads stay as they are. The commits ahead of HEAD are already read with its log (ADR 49), but go
by the HEAD of the status and the bases of the branches, which are read by loads of their own; and the log of the
highlighted branch, the divergence and the operations run when the user asks. Those keep the checks of ADR 49.

### What a commit costs

| Load | before | then |
|---|---|---|
| `LoadStatus` | 1 | 1 |
| `LoadCommits` | 1 | 1 |
| its `LoadAhead` | 3–4 | 1; 2–3 when the base was merged into the branch, or more commits are ahead than are shown |
| `LoadBranches` | 2 | 4: the reflogs, `for-each-ref`, `config --get-regexp`, `for-each-ref %(ahead-behind:…)` |
| its `LoadBaseDistance` | 2 | — |
| `LoadWorktrees` | 1 | 0 in the files backend |
| **total** | **10–11** | **7** for a branch made from its base |

A file modified less than 2s ago makes its stamp tell nothing (ADR 15). The ticks right after a commit read the
branches again, with their parents and distances, but no longer the worktrees, whose files the commit left alone.

### What stays

- The reflogs that tell when a branch was made (ADR 41) are read with every read of the branches. A commit appends
  to the reflog of its branch, which leaves the directory `logs/refs/heads` as it was, while making, renaming or
  deleting a branch, or expiring reflogs, changes it: a stamp of it could guard that read too. But what was read
  of the reflogs goes into the order of the branches and into each one as `git for-each-ref` reads it, so it would
  have to be kept in the model and put together with the branches there. That is left for later.
- The rest of what ADR 49 kept: `LoadCommits` and `LoadAhead` as one log, the distances in the `for-each-ref` of
  the branches, the reflogs with `for-each-ref`, and `worktree list` with `for-each-ref`, as issue #262 found.

### Consequences

- The fork point of a branch made from its base needs no process of its own. That of a branch the base was merged
  into still takes two.
- `BranchesLoaded` holds the parents and the distances, when they could be read; `LoadBaseDistance`, its message
  and the check that dropped an outdated count are gone. `AllBranches` tells the tips and the main branch the
  distances go by.
- The branches show only once their distances are counted, one `for-each-ref` later than before.
- `Worktree.head` can name an older commit of its branch than the branch is at.

Amends ADR 49.

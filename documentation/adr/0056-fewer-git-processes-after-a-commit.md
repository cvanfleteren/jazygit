# 56. Fewer git processes after a commit

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
  modified at.
- `git worktree list` reads what each worktree has checked out from its HEAD; the commit it is at is the one thing
  that moves with a commit, and no view shows that commit of a worktree on a branch.
- The parents of the branches (ADR 49) are in the config, and are read with every read of the branches.

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
   a worktree on a branch is where that branch was when the worktrees were listed.
3. **The parents are read when the config changes.** A stamp of the config file guards a new `LoadParents`, like the
   branches, the stashes and the worktrees (ADR 15). The parents read are kept in the `Repo`; `LoadBaseDistance` is
   given them, with the tips, and only counts. A read of the parents that changes them counts the distances again,
   and reads again the commits ahead whose base is another one by them. A parent jazygit records changes the config,
   so its stamp, which ADR 49 had the read of the branches that follows do.

### What a commit costs

| Load | before | then |
|---|---|---|
| `LoadStatus` | 1 | 1 |
| `LoadCommits` | 1 | 1 |
| its `LoadAhead` | 3–4 | 1; 2–3 when the base was merged into the branch, or more commits are ahead than are shown |
| `LoadBranches` | 2 | 2 |
| its `LoadBaseDistance` | 2 | 1 |
| `LoadWorktrees` | 1 | 0 |
| **total** | **10–11** | **6** for a branch made from its base |

A file modified less than 2s ago makes its stamp tell nothing (ADR 15). The ticks right after a commit read the
branches again, and the distances; no longer the worktrees or the parents, whose files the commit left alone.

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
- `GitMetadata` has a stamp of the config; `CheckMetadata.GUARDED` holds `LoadParents`, so reading the repository
  again after an operation reads the parents too.
- `LoadBaseDistance` holds the parents it counts by: one read with older parents is dropped, as one read for older
  tips is (ADR 49).
- `Worktree.head` can name an older commit of its branch than the branch is at.

Amends ADR 49.

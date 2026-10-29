# 46. How far a branch is behind its base

Date: 2026-10-29

## Context

The branches pane shows how far a local branch is from its upstream (`↑2 ↓5`, ADR 45), but not how far it has
fallen behind the branch it is meant to merge into. A feature branch that is pushed and then left alone stays in
sync with its upstream, `origin/<name>`, while `main` moves on: a branch 58 commits behind `main` shows nothing.

ADR 20 tells the commits of a branch from those on its base, and names the base in the title of the log of the
highlighted branch: "3 ahead of origin/main". It left counting out of the branches list, as that "would take a git
process per branch on every read".

Since git 2.41, which the 2.45 jazygit needs is past, `git for-each-ref --format=%(ahead-behind:<base>)` counts the
commits ahead and behind of every ref against one base, in one process. It took 5 ms for the branches of jazygit's
own repository.

GitHub's `behindBy`, which the pull request dialog shows, only exists for a branch with a pull request,
compares with the base of the pull request on GitHub, and takes a request.

## Decision

### What shows

- A local branch whose base has commits it does not have shows how many on line 2 of its row, as `⇣58`: dimmed, in
  a group of its own right after `↑2 ↓5` or `gone`.
  ```
  worktree-pr-title-garbled
    4d  ⇣58  ✓ #148  +  wt
  ```
- From one commit on, without a threshold; nothing for none.
- Only the count: the base is not named, also when it is not the main branch. The log of the highlighted branch
  names it in its title.
- Only behind: how far a branch is ahead of its base stays in the title of its log.
- Not the main branch, which has no base, nor remote branches, which have no recorded parent.
- `⇣` is one column wide everywhere (East Asian Width `N`) and looks unlike `↓`, behind the upstream. The legend of
  the help of the branches pane names it.

### The base

That of ADR 20, read locally: the branch it was made from, as `branch.<name>.gh-merge-base` records it, else the main
branch; of each, `origin/<name>` when there is one, else the local branch; never the branch itself. A parent that is
gone falls back to the main branch, without saying so.

### How it is read

- `GitInfoProvider.behind(main)` reads the parents of all branches with one `git config --get-regexp`, the refs that
  could be bases with one `for-each-ref`, and then, for each base that some branch has, one
  `for-each-ref --format=%(ahead-behind:<base>)` over the local branches. The base is given by the commit the first
  `for-each-ref` found it at, not by its name: a branch name can hold a `)`, which ends the atom, and the ref can be
  gone by then. The branches of a base whose count cannot be read show none; the others still do.
- Which refs are the bases of a branch, `BranchRef.bases`, is the one list both this and the commits ahead of the
  base in the log (ADR 20) go by.
- `Cmd.LoadBehind` runs it after every read of the branches, in the background: after a fetch, a pull, a checkout,
  and whatever else changes the refs.
- The counts live in `Repo.behind`. Until the first read is done, no branch shows one; a read again keeps the counts
  that were there until it replaces them, so the rows do not flicker. A read that fails keeps them too.

ADR 20's "the branches list does not count the commits of each branch" no longer holds: it counts those behind the
base, not those ahead.

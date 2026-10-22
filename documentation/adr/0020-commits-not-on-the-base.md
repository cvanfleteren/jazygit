# 20. Telling the commits of a branch from those on its base

Date: 2026-10-22

## Context

The commits pane and the log of a highlighted branch list everything reachable from the tip, up to 300 commits.
Which of those are the branch's own work, and which it shares with the branch it came from, could not be told
without counting shas or leaving for `git log main..`. The divergence view (`u`, then `b`) lists them, but in place
of the branches, and only for a local branch.

A line where the branch forked off does not work: once the base was merged into the branch, its commits and the
branch's are interleaved, and there is no single place to draw it.

## Decision

### The base

The commits of a branch are compared with its base:

1. the branch it was made from, as `branch.<name>.gh-merge-base` records it (as ADR 0003 reads it);
2. else the main branch, the one `Branch.isDefault` marks.

Of each, the remote-tracking `origin/<name>` when there is one, else the local branch. A parent that is gone falls
back to the main branch without saying so. A detached HEAD is compared with the main branch.

A branch is never compared with itself: for the main branch, `main` or `origin/main`, there is nothing to tell, and
nothing is marked.

### What shows

Both in the commits pane, for HEAD, and in the log of the branch highlighted in the branches pane:

- The commits that are on the base are dimmed, the whole row or block: sha, tags, rails and message. They can still
  be highlighted and acted on.
- The title names the base and counts the others: `Commits · 4 ahead of origin/main`.
- `b`, in the commits pane and in both tabs of the branches pane that show a log, lists only the commits that are
  not on the base, and then the commit the branch forked from (`git merge-base`), dimmed and marked
  `⑂ origin/main`. The title then says `only 4 ahead of origin/main`. `b` again shows the whole log. It is one
  setting for both logs, `RepoView.onlyAhead`; it does nothing for a log without a base.
- More than 300 commits not on the base: the title says `300+ ahead`, no row is dimmed, and the fork point is left
  out.
- A search (`/`) while `b` is on looks only through the commits not on the base. Its results are dimmed by the same
  rule.

The branches list does not count the commits of each branch: that would take a git process per branch on every
read.

### How it is read

`GitInfoProvider.ahead(Option<BranchRef> branch, List<BranchRef> bases)` takes the first of `bases` that exists, and
reads for it `git log <tip> ^<base>`, laid out by `CommitGraph.arrange`, at most 300; whether there are more
(`rev-list --count --max-count=301`); and the fork point, `git merge-base` then that one commit. That gives
`Ahead(base, commits, more, forkPoint)`.

- `Cmd.LoadAhead` reads the parent of the branch, makes the list of bases, and asks for it. It is run once the log
  it goes with is read (`CommitsLoaded`, `BranchLogLoaded`), and again when the tip of its base moved, e.g. after a
  fetch. HEAD's lives in `Repo.ahead`, the highlighted branch's in `RepoView.branchAhead`, keyed by the branch, so
  what arrives for a branch no longer highlighted is dropped.
- Failing to read it shows nothing marked; a later read tries again.

## Consequences

- Each read of a log takes a few more git processes: the parent, a rev-parse per base tried, the log of what is
  ahead, the count, the merge-base and the commit at it.
- Dimming is by sha, so it is right with merges in both directions, where a separator could not be.
- The marking shows after the log, as it is read once the log is.

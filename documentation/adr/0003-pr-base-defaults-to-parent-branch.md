# 3. A new pull request merges into the branch it came from

Date: 2026-10-20

## Context

Creating the pull request of a branch first asks which branch it merges into. That list put the default branch first,
so a branch made from `development` offered `main`, and merging into `development` took an extra step every time.

git does not record which branch another one was made from. `gh` has a convention for it: the config key
`branch.<name>.gh-merge-base`, which `gh pr create` takes as the base when it is set.

## Decision

The base list puts the branch's **parent** first, then the default branch, then the others in their old order. The
cursor starts on the first row, as it already did. Nothing marks the parent as such.

### Recording the parent

When jazygit creates a branch from a branch, it sets `branch.<new>.gh-merge-base` to that branch:

- **New branch** from a local branch: that branch.
- **New branch** from a remote-tracking branch, e.g. `origin/development`: the name it has there, `development`, as
  `gh` expects. Checking out a remote branch to follow it records nothing, as it is the same branch.
- **New branch** at a commit or a tag: nothing.
- **Move commits to a new branch** (ADR 1): the branch it starts on, or else the branch the commits came from.

git moves or deletes the `branch.<name>` section along with the branch, so renames and deletes need no handling. The
chosen base is never written back. Failing to record the parent does not fail creating the branch: it is only a hint.

### Finding the parent

Before the base list opens, a command reads, for the branches that can be chosen (the others that are on their
remote):

1. The recorded `gh-merge-base`, if it names one of them. Then nothing else is read.
2. Otherwise, how many commits the branch has that each one, as it is on its remote, does not:
   `git rev-list --count --max-count=1000 <base>..<branch>`. The closest is the parent; on a tie, the earliest in the
   old order, so the default branch when it is among them. 1000 or more counts as too far to tell apart.

If reading fails, the list opens in the old order.

The guess has known blind spots: it counts against the remote-tracking branches as last fetched, so a stale one can
mislead it; and a base the branch was squash-merged into looks further away than it is.

### Why this way

- The recorded value is exact, and `gh` itself uses it; the history covers branches made elsewhere.
- The reflog ("Created from X") was rejected: it often says `HEAD`, is local only, and expires.
- `git for-each-ref --format=%(ahead-behind:…)` counts for all branches in one process, but walks the whole history
  without a commit-graph (4.4 s on nixpkgs). `rev-list <base>..<branch>` only walks the branch's own commits, a few
  milliseconds each; the cap bounds a branch far from a base.
- The list opens once the parent is known, so it never reorders under the cursor.

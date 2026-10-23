# 26. How many commits a pull request has

Date: 2026-10-23

## Context

The pull request dialog shows how big a pull request is on its "Changes" line: the lines it adds and removes, and
the files it changes, as in `+328 −28 in 7 files`. It does not say how many commits it has, which tells how it was
put together, and whether a merge that keeps them will bring many. The line also says "in 1 files" for one file.

## Decision

- The line reads `+328 −28 in 12 files, 2 commits`, one message with a choice for each count, so `in 1 file,
  1 commit` for one and `in 0 files, 0 commits` for none, and a translation can put them in another order.
- The count is GitHub's `commits { totalCount }` of the pull request, as its "Commits" tab shows it: read in the
  query of the details the dialog reads already, on the connection of its latest commit, so it takes no more
  requests. Not counted with git, which would need its branches fetched, and could count otherwise than GitHub.
- `PullRequestDetails` holds it as `commits`, after `changedFiles`, a plain `int` as GitHub always says it.
- Shown as GitHub says it, whatever the state of the pull request: for a merged one, the commits it was merged
  with; for a stacked one, those on top of its own base.

## Consequences

- The dialog tells how many commits a pull request has without reading anything more.
- Every place that makes a `PullRequestDetails` gives the count too.

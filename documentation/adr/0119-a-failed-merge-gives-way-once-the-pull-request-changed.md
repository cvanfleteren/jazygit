# 119. A failed merge gives way once the pull request changed

Date: 2026-11-13

## Context

A merge that GitHub failed shows `!` on the marker of its pull request, and of the ones below it in its stack, instead
of their checks and review, until it is merged again (ADR 24). That keeps the failure seen once its popup is closed.

A merge most often fails because the pull request is not ready yet: its required checks are still running, or a review
is missing. Once they passed, the pull request can be merged, but the marker still shows `!` and hides the checks that
now pass. Nothing tells that merging it again goes now; only a restart of jazygit, which forgets merges, does.

## Decision

- Once a merge failed, it keeps how each pull request it merges stood then, as the pull requests listed it: its
  checks, its review, whether it is a draft and the commit its branch is at (`PullRequestMerge.Standing`).
- Each read of the pull requests, the list or the details of one, drops a failed merge once one of them no longer
  stands so: changed, or no longer listed. Its marker then shows its checks and review again, as before the merge.
- A read that shows them standing the same keeps the failure, so it stays seen as before.
- A pull request that was not listed when the merge failed is not looked at: when none was, the failure stays until it
  is merged again, as before.
- Not: showing the failure next to the checks, which keeps a stale `!`; nor dropping it after a while, which hides a
  failure that still holds.

## Consequences

- A merge that failed while the checks ran shows the checks once they finished, passing or failing, within the next
  read of the pull requests.
- A failure that a change does not fix, like a conflict, gives way too once anything of those changes; merging again
  tells it anew.
- ADR 24 is amended: a failed merge shows until it is merged again, or until a pull request it merges changed.

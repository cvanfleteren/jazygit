# 112. Merging a pull request when it is ready

Date: 2026-11-11

## Context

`m`, `s` and `r` in the pull request dialog merge it on GitHub through `merge-async` (ADR 24). A pull request that
is blocked, by checks still running, by a review still required or by any other rule of its base branch, is offered
the same keys, and GitHub refuses the merge: the user has to come back once the checks pass.

GitHub merges a pull request by itself once it is no longer blocked, when auto-merge is enabled on it:

- `enablePullRequestAutoMerge(pullRequestId, mergeMethod, expectedHeadOid)` and `disablePullRequestAutoMerge`
  (GraphQL);
- a `PullRequest` has `autoMergeRequest { mergeMethod enabledAt enabledBy }`, null when it is off, and
  `viewerCanEnableAutoMerge` and `viewerCanDisableAutoMerge`;
- a `PullRequest` has `mergeStateStatus`: `CLEAN`, `UNSTABLE` and `HAS_HOOKS` can be merged now, `BLOCKED` cannot,
  `DIRTY` conflicts, `BEHIND` is behind a base that has to be caught up with, and `UNKNOWN` is not worked out yet.
  Jazygit reads only `mergeable` so far.

The repository has to allow it (`autoMergeAllowed`), and its base branch has to require a check: without one, a pull
request that does not conflict can be merged at once, and is never blocked.

Tried against GitHub, on this repository with auto-merge allowed and `build-ok` required on `main`:

- `viewerCanEnableAutoMerge` is false on a pull request that can be merged now (`mergeStateStatus` CLEAN), true on
  one that is blocked.
- On a stacked pull request it is true, but enabling fails: `Auto-merge is not supported for stacked pull requests.`
- Enabling it again, with another method, while it is on, does nothing and says nothing: the method stays.
  `viewerCanEnableAutoMerge` is false while it is on.
- `expectedHeadOid` is only checked when it is enabled. What is pushed after is merged too, once it is ready.

## Decision

Jazygit merges a pull request when it is ready through the auto-merge of GitHub. Not: jazygit waiting for the checks
itself and merging then, which only works while it runs.

### The keys

`m`, `s` and `r` stay the keys of the three methods. Which they do depends on what GitHub says of the pull request,
the first row that fits:

| Pull request                                                    | `s`                                                       |
|-----------------------------------------------------------------|-----------------------------------------------------------|
| stacked                                                         | Squash and merge, as before (ADR 24)                      |
| auto-merge on, with squash, `viewerCanDisableAutoMerge`         | Cancel auto-merge                                         |
| auto-merge on, otherwise                                        | disabled: auto-merge is on (rebase), enabled by `<login>` |
| `CLEAN`, `UNSTABLE`, `HAS_HOOKS`                                | Squash and merge, as before                               |
| `BLOCKED`, `viewerCanEnableAutoMerge`                           | Squash and merge when ready                               |
| `BLOCKED`, `autoMergeAllowed` false                             | disabled: auto-merge is off for this repository           |
| `BLOCKED`, otherwise                                            | disabled: GitHub does not allow merging it yet            |
| `UNKNOWN`, or anything else                                     | Squash and merge, as before                               |

And so for `m` (Merge when ready) and `r` (Rebase and merge when ready). One that conflicts stays disabled, as
before, through `mergeable`.

- *When ready*, not *when checks pass*: GitHub waits for whatever blocks it, reviews and other rules too. Its details
  say so: GitHub merges it once nothing blocks it any more.
- GitHub only lets auto-merge be enabled on a pull request it cannot merge now, so a key merges now or when ready,
  never both, and is never a merge GitHub said it refuses for being blocked. Not: a key of its own for merging when
  ready, next to the three.
- `UNKNOWN` merges now, as before: GitHub has not worked out yet whether it can, and may still refuse it. Not:
  disabling the keys until it has, which is often for a while after a push.
- The rows of auto-merge on come before those of `mergeStateStatus`: while it is on, the pull request is blocked or
  about to be merged, and the keys only cancel it.
- A stacked one is merged as before, with the ones below it (ADR 24): its details say GitHub has no auto-merge for
  stacked pull requests. Jazygit tells it is stacked itself, as `viewerCanEnableAutoMerge` says it can be enabled.
- Cancelling is offered only when `viewerCanDisableAutoMerge`: also on auto-merge someone else enabled.
- The method is changed by cancelling and choosing again. Not: the other keys disabling and enabling it in one go,
  as enabling it again does not change the method, and between the two it would be off.
- Enabling it sends the head of the pull request as last read as `expectedHeadOid`, as a merge sends it (ADR 24).
  What is pushed after it is enabled is merged too, as GitHub does.
- The commit message is the one GitHub makes, as when merging now: nothing is asked.
- Enabling and cancelling are operations like a merge: logged, and the details are read again once done.
- When GitHub refuses, the error shows as any failed operation, and the details are read again. That is also when the
  pull request could be merged now by the time the key was pressed: the keys then merge now. Not: merging it then,
  which takes telling that refusal from the others by its message.

### Shown

- The dialog gets a line when it is on: `Auto-merge  squash · by cvanfleteren · 3m ago`.
- The marker shows `◇` after its checks and review, `#95 ● + ◇`, in the class `pull-request`: the hollow `◆` of a
  merged one, as it will be. The checks stay, as they are what it waits for.
- `◇` is of ambiguous width, as `◆`, `●` and `≡` are (ADR 45, 48): in a terminal that draws those wide, the rest of
  line 2 moves a column to the right. That cost is taken as it is for them; should it matter, `⬦` (U+2B26), of
  neutral width, takes its place, as `⬥` would take that of `◆`.
- Enabling auto-merge forgets how an earlier merge of the pull request went (ADR 24), as a new merge replaces it: one
  whose merge failed, which showed `!` instead of its checks, shows its checks, review and `◇` again. Not: keeping
  `!` until GitHub merges it, which hides the checks it waits for.
- The summaries of the pull requests read `autoMergeRequest { mergeMethod }`, so the marker shows it on the branches
  and on the pull requests screen, also when it was enabled on github.com.
- Once GitHub merged it, a read of the pull requests finds it merged, as any merged one: no notice of its own.

### Not part of it

- A merge queue: with one, GitHub ignores the method and puts the pull request in the queue when it is ready. This
  repository has none; the labels do not say so.
- A pull request with auto-merge on that is stacked, from jazygit or elsewhere: what GitHub does with it is shown as it
  says.
- Merging when ready from anywhere but the pull request dialog, as merging now.

## Consequences

- A blocked pull request is merged with one key, and needs no coming back once its checks pass; quitting jazygit
  does not stop it.
- The keys of the dialog depend on what GitHub says of the pull request when its details were read: the same key
  merges now on one and when ready on another, which its label says.
- Auto-merge only does something in a repository that allows it and whose base branch requires a check. This one
  does: `build-ok` (`.github/workflows/build.yml`), which runs on every pull request, also when only documentation
  changed.
- Stacked pull requests keep merging only now, until GitHub has auto-merge for them.
- The details and summaries read more fields from GitHub: the details `mergeStateStatus`, `autoMergeAllowed` of the
  repository, and those of auto-merge.
- A blocked pull request that GitHub does not let be merged when ready is no longer offered a merge: its keys say why.

Amends ADR 24 (`0024-stacked-pull-requests.md`): the merge keys of the dialog enable auto-merge on a pull request that
is blocked.

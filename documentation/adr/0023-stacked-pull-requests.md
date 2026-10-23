# 23. Stacked pull requests

Date: 2026-10-23

## Context

GitHub lets pull requests form a stack: each one targets the branch of the one below it, and the bottom one the base
of the stack. Merging one of a stack with `gh pr merge` fails, as the `mergePullRequest` mutation it calls does not
take stacked pull requests:

> GraphQL: This pull request is part of a stack and must be merged using the asynchronous merge REST API.

GitHub tells which pull requests are stacked: a `PullRequest` has a `stack` (its `number`, `size` and `entries`) and
a `stackEntry` (its `position`, 1 being the closest to the base), both null when it is not stacked. The REST endpoint
`PUT repos/{owner}/{repo}/pulls/{n}/merge-async` merges a pull request in the background, and one of a stack together
with the open ones below it. It answers 202 with the id of the merge, which `GET .../merge-async/{id}` tells the
outcome of: `pending`, `merged`, `enqueued` (into a merge queue) or `failed`, with a message.

## Decision

Jazygit merges stacked pull requests and shows them as stacked. Creating or restacking a stack is not part of it.

### Telling a pull request is stacked

- The lists and the details read its `stackEntry { position }` and `stack { number size }`; `PullRequest`,
  `PullRequestSummary` and `PullRequestDetails` get a `PullRequestStack` (number, position, size) when it has one.
- `gh pr list --json` has no such fields, so the pull requests of the branches are read with GraphQL instead, all
  pages of them, with the states of the checks on their last commit as before.
- Its marker shows where it sits in its stack, on the branch and in the rows of the pull requests screen:
  `#95 📚2/2 ✅ 👍`.
- The dialog gets a line for it: `Stack  #96 · 2 of 2 · merging also merges the 1 below`, and the merge options say
  how many it merges: "Squash and merge 2 PRs".
- Merging one with others below it asks nothing more than merging any other: the dialog says so before.

### Merging

- Every pull request is merged with `merge-async`, stacked or not, with `merge_method` and the `sha` of its head as
  it was last read, so GitHub does not merge what was pushed since. `gh pr merge` is no longer used.
- An answer of 202 makes it a pending merge, kept in the model by its number with the id of the merge. The branch is
  no longer busy then: the merge goes on in the background, and the dialog closes as before.
- An answer of 200 is the outcome straight away: merged, or already in a merge queue.
- Anything else fails as a merge failed before, with what gh says. That includes 409, when a merge of it is already
  pending: gh only says why, not the id of that merge, so it cannot be waited for. Jazygit does not ask for a merge
  of a pull request it is merging already.

### Waiting for the outcome

- A `PullRequestMsg.MergeTick` every 2s asks GitHub how each pending merge is doing, in a read of its own
  (`CheckMerge`) that runs in the background, is not logged and does not wait behind the reads of the pull requests
  (ADR 12). A merge is not asked about again while it is asked about, nothing is asked when none is pending, and a
  failed ask is asked again at the next tick.
- `merged`: the pull requests, the branches and the remotes are read again, as after a merge before.
- `enqueued`: the command log says it was added to the merge queue.
- `failed`: fails as a merge failed before, with the message of GitHub: logged, and in the error popup when no other
  popup is open.
- Still pending after 10 minutes: the command log says it is still pending on GitHub, and it is no longer asked
  about nor shown.
- Pending merges are only kept in memory: quitting forgets them, and GitHub merges them all the same.

### Shown on the marker

While a merge goes on, and after, its outcome shows on the marker of the pull request instead of its checks and
review, which no longer matter:

| Merge     | Marker   |
|-----------|----------|
| pending   | `#95 🔀` |
| enqueued  | `#95 🚦` |
| failed    | `#95 💥` |
| merged    | `#95 🟣` |

- The ones below it in its stack show the same, as it merges them too; they are the ones listed below it when
  GitHub is asked for the merge.
- A merged pull request shows 🟣 until the next read of the pull requests no longer lists it.
- A failed one shows 💥 until it is merged again, so the failure stays seen after its popup is closed.
- An enqueued one shows 🚦 until it is merged again, or no longer listed once the queue merged it; one the queue
  dropped keeps 🚦 too, as nothing tells it was dropped.
- Only the pull requests of this repository show it on the pull requests screen: the numbers are of this one.
- These outcomes are kept by number, apart from what is read from GitHub, so a read of the pull requests does not
  wipe them.

## Consequences

- Stacked pull requests merge from jazygit, and so does every other one, through a single path that also takes merge
  queues.
- Jazygit calls the REST API of GitHub for the first time, through `gh api`, with `{owner}` and `{repo}` filled in by
  gh from the working tree.
- A merge is no longer done when the dialog closes: it is seen done up to 2s after GitHub merges it.
- Merging the top of a stack merges the ones below; only the line in the dialog and the count in the options say so.

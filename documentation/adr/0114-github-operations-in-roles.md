# 114. GitHub operations in roles

Date: 2026-11-13

## Context

ADR 69 split the git operations into roles and left `GitHub`, `CliGitHub` and `StubGitHub` as they were (decision
8): with 30 operations then, the gain looked smaller, and it was to be reconsidered once the git roles were in.

A review found it is time. `GitHub` has grown to 38 operations, `CliGitHub` to 515 lines and 63 methods, and each of
the two was touched by 5 of the last 20 commits: a new gh operation edits the interface, `CliGitHub` and
`StubGitHub`, the same three files whatever it is about, so parallel work on pull requests, reviews and stacks keeps
editing the same files. `CliGitHub` also mixes what has nothing in common: the GraphQL mutations of comments, the
browser an account opens links in, and the commands of `gh pr`.

## Decision

1. **The gh operations are split into 5 roles, each implemented by a package-private class that holds its own
   code**, as the git roles are (ADR 69):

   | Role | Operations |
   |---|---|
   | `PullRequests` | ofRepository, mine, preview, details, image, create, checkout (×2), merge, mergeStatus, close, setDraft, edit, `MAX_IMAGE_BYTES`, `Created`, `Merging` |
   | `Reviews` | conversation, comment, deleteComment, editComment, submit, remarks, startThread, reply, resolve, deleteRemark, react, editRemark, `Edited` |
   | `Checks` | ofBranches, runs, recent, protectedBranches |
   | `Stacks` | create, add, unstack |
   | `Account` | version, logins, current, ghBrowser, openPullRequest (×2), openInBrowser, openLink, browse |

   Method names are short within their role: `pullRequests().details(n)`, `reviews().reply(...)`,
   `stacks().unstack(n)`. On the comments, `Reviews` tells the comments of the conversation (`comment`,
   `deleteComment`, `editComment`) from those of review threads, which jazygit calls remarks (`remarks`,
   `deleteRemark`, `editRemark`).
2. **Everything that opens the browser is in `Account`.** The browser is chosen by the account the repository uses
   (ADR 88), so opening a pull request, a review comment, a link or a branch goes with the account, as everything
   that passes credentials to git went to `Remotes` (ADR 69, decision 2). `Account` is the only role that knows the
   browsers, the environment and the system they are chosen by.
3. **The roles are reached through a record**, `GitHub(PullRequests pullRequests, Reviews reviews, Checks checks,
   Stacks stacks, Account account)`, with Lombok's `@With` on each. `CliGitHub` is its one public factory, with
   `of(...)` in place of its constructors. The four roles that only run gh share one `GhProcess`; no role class holds
   another. The static helpers a stub or a query reads stay on the class of their role (`CliPullRequests.review`,
   `CliReviews.commentCommand`). An ArchUnit rule keeps the app away from the `Cli` classes but `CliGitHub`.
4. **Every signature keeps its types**: errors, results and parameters are as they were, only moved.
5. **Each role has a stub, and a fake overrides only what its test needs.** `StubGitHub.of()` gives a `GitHub` with
   every stub, and a test replaces a role with `StubGitHub.of().withPullRequests(new StubPullRequests() { … })`.
6. **The tests of `CliGitHub` stay as they are**, against the roles through `CliGitHub.of`; they are split per role
   when they next change, as the git ones were (ADR 71).

## Consequences

- A new gh operation is written in its role's interface, its `Cli` class and its stub; no file holds every operation
  any more.
- Every caller of GitHub changes, from `gitHub.pullRequestDetails(n)` to `gitHub.pullRequests().details(n)`, and every
  fake in the tests is rewritten against the roles.
- `Account` holds the operations that open a pull request in the browser, which by what they open belong to the pull
  requests: where the browser is chosen decides.
- This amends ADR 69, decision 8.

Follows from the review's section 2e, the half of section 1a that was not done.

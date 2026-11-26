# 204. The forges split their packages by flow

Date: 2026-11-26

## Context

`features-github` keeps a package for each flow of a pull request: `create`, `show`, `browse`, `checks`, `checkout`
and others. `features-forgejo` kept all of its pull requests in one package, `forgejo.pulls`, of 35 files: creating
one, showing and merging one, opening one in the browser, the list read and the plain-HTTP check. GitHub's own
`github.state`, of 30 files, held besides its part and its stores how long the checks take (ADR 32), the checks of the
branches, and the pull requests not seen yet (ADR 29, 63). Which part of one forge has a twin in the other could not be
seen from the packages, which a code shared by both forges (as a `features-forge` module) needs to see first.

## Decision

- **Forgejo's pull requests split as GitHub's do**:
  - `forgejo.create`: creating the pull request of a branch, the twin of `github.create`.
  - `forgejo.show`: the dialog of a pull request, and merging it from there, as `github.show` keeps its merge.
  - `forgejo.browse`: opening one in the browser, as `github.browse`.
  - `forgejo.pulls` keeps the part, the list read and its update (`PullRequestMsg`/`PullRequestUpdate`, on which
    every flow lands), the remotes and the plain-HTTP check, as `github.state` keeps GitHub's list.
  - `ForgejoFailure` goes to the root package, beside `ForgejoFeature`, as `GitHubFailure` is beside
    `GitHubFeature`: the checks use it too.
- **GitHub's state gives up what stands apart**:
  - `github.checks.timings`: how long the checks take, and the checks read one by one with their timing (ADR 32).
  - `github.checks.branches`: the checks of the branches, which the branches pane shows (Forgejo's twin is
    `forgejo.builds`).
  - `github.prs.unseen`: the pull requests not seen yet.
  - `github.state` keeps the part, its stores and reads, the list of pull requests and its merge, the protected
    branches, the account and what is shared by the flows (`FromGitHub`, `Reading`, `FailingRead`, `StackChange`).
- **Only what a split package needs of another is made public**: on Forgejo, `ForgejoRemotes` and `MergeUpdate`, and a
  method each of `PullRequestUpdate` and `ForgejoCreatePullRequestUpdate`; the test fixtures of the pull requests
  stay in `forgejo.pulls` and are public. GitHub's split needed none.

## Consequences

- The packages of the two forges line up where they have twins (`create`, `show`, `browse`, `checks`), so code that
  both share can be found by its package.
- `forgejo.builds` and `github.checks.branches` are twins under different names; renaming one is left for when the
  shared code is drawn out.
- The packages of a forge use each other both ways, as they did as one package: `forgejo.pulls` lands every flow's
  messages, and the flows read its part.

# 169. GitHub's state leaves the kernel

Date: 2026-11-21

## Context

ADR 167 left two steps: GitHub's state, `kernel.state.github`, moves to `features-github` once nothing below it reads
it (step 6), and an architecture rule then keeps `kernel..` from `core.gh..` (step 7). ADR 168 took GitHub's screen out
of the kernel, but more than the screen still read that state from below `features-github`:

- the kernel itself: `Update` gave a read of the open pull requests its id as it started (ADR 119), and the parents of
  the branches went by `GitHubBases`, which `LoadBranches`, the branches pane and `PullRequestsUpdate` built from
  `GitHubState` (ADR 72);
- `features-branches`, which comes before `features-github`: setting a base refused a branch whose pull request
  states it, the protected branches were roots for a rebase, a worktree for a pull request was checked out with gh, and
  the template of a worktree's location took GitHub's `owner/name` (ADR 134);
- the `Env`: the stores of the unseen pull requests and of the check timings were fields of it, and `forgeAccount()`
  read GitHub's configured account;
- the kernel's notices held the stack's `StackNotListed`, with a `PrNumber`.

Neither step could be done as a move. Step 7, as written, could not pass at all: the review threads of the rich diff,
which ADR 167 left for a decision of their own, are made of `core.gh` types in about 25 kernel files, and their loads,
in `features-changes`, run gh from the `Env` and fail with `Failure.GitHub`.

## Decision

- **What the branches are built on is stated, not GitHub's**: what a pull request merges into, the branch below it in
  its stack, the protected branches are how GitHub states a base or a root; another forge, or a setting, could state
  them too. The kernel has `StatedBases` (by the branch as the remotes name it, the base and what states it, as a
  `Phrase`; the roots; the remotes it is of) and `BranchBases`, what all the features state. A part states its own with
  a hook, `Part.stating`, as it knows its commits; `BranchBases.in(model)` composes those of the parts kept: a branch
  is a root when any says so, and its base is that of the first part that states one, in the order of their names.
  Nothing is copied into a part of the kernel's, so nothing is kept twice. `GitHubState.bases()` states GitHub's.
  `BranchError.BaseFromPullRequest` becomes `BaseStated`, which says what states the base ("its pull request #12 on
  GitHub") and that it is changed there.
- **The repository on the host is named the same way**: `Part.naming` gives `owner/name`, which the template of a
  worktree takes when its source has none of its own. `GitHubState.repository` names it, and `PrOverview` no longer
  keeps it a second time: its `repositoryName` goes.
- **The reactions are told which commands started**: `Reactions.Reaction` has a default `started(model, cmds)`, which
  `Update` calls once the commands of a step are stamped. GitHub's reaction gives a read of the open pull requests its
  id there (ADR 119). It is a reaction, not a part's hook, as a part that is not kept is not asked, and the first read
  starts before anything of GitHub is kept.
- **A worktree's source says how it is added**: `WorktreeSource` is open. A source adds the worktree (`add(env, path)`)
  and may share a guard, have templates of its own, name its repository and give variables of its own, as
  `{number}`. A pull request is `features-github`'s `PullRequestWorktree`, with its template key,
  `jazygit.pullRequestWorktreeTemplate`, unchanged.
- **The `Env` holds what a feature runs against under a key the feature declares**, as the `Model` holds a part:
  `Env.Service<T>` has a name and what it is when none is given, and `env.service(key)` reads it. The unseen pull
  requests and the check timings are `GitHubStores.UNSEEN` and `CHECK_TIMINGS`; their files stay in `kernel-settings`,
  where the TOML is. The forge account is the kernel's own key, `Env.FORGE_ACCOUNT`, as the agent reads it without
  knowing a forge (ADR 162); the app gives GitHub's, `GitHubAccount.ON_FORGE`.
- **A feature's notice says itself as a phrase**, as its errors do: `Notice.Phrased` is open to a feature's sealed set,
  and the stack's is `StackNotice.NotListed`.
- **`kernel.state.github` is `features.github.state`**, with its tests, and its texts move to the bundle of
  `features-github`.
- **The rule is added with the review threads named as its exception**: `AppRules.theKernelNamesNoForge` keeps
  `kernel..` from `core.gh..`, but for `AppRules.REVIEW_THREADS`: the threads' types (`ReviewThread`,
  `ReviewRemarks`, `ThreadsByPath`, `Reaction`, `Reactions`, `PullRequestDetails`, `PrNumber`, `GhCommit`), and what
  their loads run against and fail with (`GitHub` in the `Env`, `GhError`, `GhFailure`, `GhAccount` in its text).
  `Failure` stays closed and keeps `Failure.GitHub`: the loads of the threads are in `features-changes`, which cannot
  see a failure of `features-github`'s, and two kinds for gh failing would be worse than one in the exception.

## Consequences

- No kernel code reads GitHub's state; a second forge states bases, names its repository, adds worktrees, keeps
  stores and tells notices without a kernel edit.
- Without `features-github`, as in the tests of `features-branches` and of the views, nothing states a base or a root
  but the main branch; their tests give a part that states them.
- The exception of the rule is the list of what the review threads' own decision moves. When they leave the kernel,
  `Failure.GitHub` can go with them, `Env.gitHub` can become a service of `features-github`'s, and the rule loses its
  exception.
- `Bundles` skips the names of services, as it skips those of parts.
- ADR 162 and ADR 167 get a line "Amended by ADR 169".

Amended by ADR 171: the review threads leave the kernel, and with them the exception of the rule: `Failure.GitHub`
becomes GitHub's, through `Failure.Phrased`, and `Env.gitHub` a service of `features-github`'s.

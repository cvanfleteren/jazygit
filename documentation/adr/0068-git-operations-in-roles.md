# 68. Git operations in roles

Date: 2026-11-01

## Context

The review in issue #294 (section 1a, item 6) found that the git side of jazygit churns for a structural reason.
`GitInfoProvider` declares 83 operations. `CliGitInfoProvider` implements them almost only by delegating to static
helpers (`CliBranch`, `CliLog`, `CliStash` and so on), and `StubGitProvider`, in the test-jar of `jazygit-git`,
gives each a default. A new git operation edits four files: the helper that runs git, the delegate in
`CliGitInfoProvider`, the interface and the stub. The interface and the provider are among the most changed files of
the repository, and so the most likely to conflict between parallel work.

The review proposed role interfaces, grouped by the comments that already group the interface, exposed from a small
record. Two of its premises did not hold as written:

- Tests do not override all 83 operations: `StubGitProvider` gives every one a default, and a fake already overrides
  only what its test needs. Splitting into roles alone would gain the tests little.
- Splitting the interface alone does not lower the files a new operation touches. As long as a provider delegates to
  static helpers, there are still four. Only role classes that are themselves the implementation make it three.

Some state is also shared between the operations: the worktree (all of them), where git keeps the files of an
operation in progress (`status`), where the metadata is (`metadata`, `diffStamp`) and the credentials. The credentials
are passed to git not only by push, pull and fetch, but also by `deleteBranch`, whose scope can include the remote, and
by `pullRequestFiles`, which fetches the refs of a pull request. `withCredentials` rebuilds the whole provider.

And some tests need one object for several roles: `ProgramTest.FakeProvider` keeps about 12 mutable fields across 25
overrides, from the status to stashes, checkout, push, the index and commits.

## Decision

1. **The git operations are split into 13 roles, each implemented by a class that holds its own code.**
   `CliGitInfoProvider` goes, and a new git operation edits three files: the role interface, its `Cli` class and its
   stub. The roles:

   | Role | Operations |
   |---|---|
   | `Status` | status, metadata, diffStamp |
   | `Branches` | allBranches, deleteLocal, forceDeleteLocal, countOnlyOn, rename, parentBranch, parents, baseDistance |
   | `Checkout` | checkout, checkoutWithStash, checkoutNewBranch, checkoutNewTrackingBranch, resolve |
   | `History` | commits, search, ahead, log, divergence, firstSubject, countNotOn, commitRange, `MAX_COMMITS` |
   | `Diffs` | commitChanges, commitFiles, commitFileChanges, pullRequestFileChanges, rangeFiles, rangeFileChanges, diff |
   | `Stashes` | stashes, stashChanges, stashFiles, stashFileChanges, stash, pop, drop |
   | `Commits` | commit, amendLast, amend, reword, squash, drop, moveCommits, moveCommitsTo, reset |
   | `Integrate` | merge, cherryPick, revert, continueOrAbort, rebase |
   | `Remotes` | remotes, remoteUrls, push, pushSettingUpstream, pull, fetch, fastForward, deleteRemoteBranch, deleteBranch, pullRequestFiles |
   | `Index` | stage, unstageNew, discard, unstage |
   | `Worktrees` | worktrees, hasChanges, add, addDetached, addWithNewBranch, remove, removeAnyway, prune, lock |
   | `Config` | localConfig, setLocalConfig |
   | `Editor` | edit, editMessage, editText |

   A helper that serves one role becomes its class (`CliStash` becomes `CliStashes implements Stashes`, its static
   methods instance methods on the worktree). The helpers of a role that draws on several are merged into its class.
   Only what several roles share stays a package-private static helper: the plumbing of an interactive rebase in
   `CliRebase`, `CliLog.resolve`, `CliInProgress.markers`, `GitOutput` and `GitProcess`. Method names are short within
   their role: `branches().rename`, `stashes().drop`, `worktrees().remove`.
2. **Everything that passes credentials to git is in `Remotes`.** That moves `deleteBranch` out of the branches and
   `pullRequestFiles` out of the diffs, with their signatures as they are. `Remotes` is the only role holding the
   credentials, and `withCredentials` replaces only it.
3. **The roles are reached through a record.** `Git(Path worktree, String repositoryName, Status status, Branches
   branches, …)`, with Lombok's `@With`. The worktree and the name of the repository are values, no longer operations.
   One public factory, `CliGit.open(Path)` and `CliGit.init(Path)`, gives a `Git`. The `Cli` role classes are
   package-private, and an ArchUnit rule keeps the app away from them.
4. **The app takes the whole `Git`.** `Env.git()` is a `Git`, and `GitOperationCmd.perform` takes one: a command reads
   `git.branches().rename(...)`. Only a plain helper that uses a single role takes that role, as
   `GitHubAccount.configured` takes `Config`.
5. **Every signature keeps its error type.** The mix of `ProcessError`, `GitError`, `GitFailure<GitError>` and
   `GitFailure<ProcessError>`, and the `baseDistance` that swallows its errors, are left for a later change, so that
   this one only moves code.
6. **Each role has a stub, and a fake overrides only what its test needs.** The test-jar has a `StubX` per role, whose
   operations all succeed without running anything, as `StubGitProvider` does today. `StubGit.of()` gives a `Git`
   with every stub, and holds `ran`, `refused` and `executed`. A test replaces a role with `StubGit.of().withBranches(new
   StubBranches() { … })`. A fake that spans roles becomes one fake per role around state kept in its own test, as
   `ProgramTest` will with a `FakeRepo` of its own; there is no shared fake git.
7. **The tests of the `Cli` classes are split per role**, out of `CliGitInfoProviderTest`, around a shared temporary
   repository. They still run real git, through `CliGit.open`.
8. **`GitHub`, `CliGitHub` and `StubGitHub` stay as they are.** With 30 operations the gain is smaller; it is
   reconsidered once the git roles are in, if a new gh operation still costs too much.

The split is done one role at a time, each in a pull request stacked on the one before. Until the last,
`GitInfoProvider` gains an accessor per role and loses that role's operations; the last replaces it with `Git`.

## Consequences

- A new git operation is written in its role's interface, its `Cli` class and its stub; no file holds every operation
  any more, so parallel work on different roles no longer edits the same files.
- Every caller of git changes, from `git.renameBranch(...)` to `git.branches().rename(...)`, and every fake in the
  tests is rewritten against the roles.
- A test that faked several roles in one object needs one fake per role; `ProgramTest` the most.
- `Remotes` holds operations that, by the git command they run, belong elsewhere (`deleteBranch`, `pullRequestFiles`):
  where credentials go decides.
- The error types stay as inconsistent as before, now spread over the roles.
- `GitHub` keeps the three-file pattern for now.

Follows from issue #294, section 1a and item 6.

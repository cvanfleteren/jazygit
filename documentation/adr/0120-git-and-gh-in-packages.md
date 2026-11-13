# 120. git and gh in packages

Date: 2026-11-13

## Context

ADR 69 and ADR 114 split the operations of git and gh into roles, each implemented by a package-private class. All of
them stayed in one package per module: `net.vanfleteren.jazygit.git` held 48 classes, `net.vanfleteren.jazygit.gh` 38,
next to their tests and stubs. What belongs to a role (its interface, its `Cli` class, its queries and parsers, its
stub and its tests) could only be told from the names, and a change to one role was made among the classes of all.

## Decision

1. **gh is split by role, one package each**, under a package of its own for what the roles share:

   | Package | Classes |
   |---|---|
   | `gh` | `GitHub`, `CliGitHub` |
   | `gh.pullrequests` | `PullRequests`, `CliPullRequests` and its queries |
   | `gh.reviews` | `Reviews`, `CliReviews` and its queries and mutations |
   | `gh.checks` | `Checks`, `CliChecks` and its queries |
   | `gh.stacks` | `Stacks`, `CliStacks`, `StackMutations` |
   | `gh.account` | `Account`, `CliAccount`, `Browser`, `LoginsQuery` |
   | `gh.graphql` | the fragments more than one role reads: `CheckFragment`, `SuiteFragment`, `StackFragment`, `SummaryFragment`, `ReviewingFragment` |
   | `gh.process` | `GhCommand`, `GhProcess`, `GhToken`, `SshHost`, `GhError`, `GhFailure` |

   No role reads another: what the details of a pull request and its review threads both decode, the review decision
   and what the viewer needs to review it, is `ReviewingFragment`.
2. **git is split by what its roles share, not one package per role.** The `Cli` classes of git call each other
   (checking out stashes, a branch is deleted after its pull request is merged, a commit is reworded by a rebase), so
   one package per role would make 13 packages that depend on each other both ways. Instead:

   | Package | Classes |
   |---|---|
   | `git` | `Git`, `CliGit` |
   | `git.commits` | `Commits`, `CliCommits` |
   | `git.branches` | `Branches`, `Remotes`, `Checkout`, `Worktrees`, `Config`, their `Cli` classes, `CliBaseDistance`, `CliPullRequest`, `BranchesInUse` |
   | `git.files` | `Status`, `Diffs`, `Stashes`, `Index`, `Editor`, their `Cli` classes, `CliWorkingFiles`, `CliMetadata`, `RepositoryIdentity` |
   | `git.history` | `History`, `Integrate`, their `Cli` classes, `CliRebase`, `CliInProgress`, `Unfinished` |
   | `git.process` | `GitCommand`, `GitArgs`, `GitOutput`, `GitProcess`, `TempFiles`, `HeadFile`, `GitError`, `GitFailure` |

3. **The packages depend one way**, and an ArchUnit rule of each module keeps them so: for gh, from `gh` to the roles,
   to `gh.graphql`, to `gh.process`; for git, from `git` to `git.commits`, `git.branches`, `git.files`,
   `git.history` and `git.process`, each only on those after it.
4. **A role's interface, its `Cli` class, its stub and its tests share its package.** What a stub or a test reads of a
   `Cli` class stays package-private.
5. **What crosses a package is public, and the application is kept from it by ArchUnit instead of by the compiler.**
   The `Cli` classes are public, as their constructors are for `CliGit` and `CliGitHub`; so are the helpers one
   package calls in another. The rules that keep the application to `CliGit` and `CliGitHub` (ADR 69, 114) now look
   in the subpackages too, and new ones keep it from the process, the commands and the GraphQL of git and gh.

## Consequences

- Besides the models, which this leaves as they were, no package of git or gh holds more than 13 classes, not
  counting its tests.
- Package-private no longer hides what a role shares with another: a `Cli` class or a helper of it is public once
  another package calls it, and only the architecture tests stop the application from calling it too.
- The roots of git and gh hold only the entry points, `Git` and `CliGit`, `GitHub` and `CliGitHub`.
- This amends ADR 69, decisions 1 and 3, and ADR 114, decisions 1 and 3: the role classes are no longer all
  package-private.

# 71. The git roles as built

Date: 2026-11-03

## Context

ADR 69 split the git operations into 13 roles behind a `Git` record, one pull request per role (#304, #310 to #314,
#324 to #332). Doing it turned up a few places where the plan did not fit the code as written:

- Decision 1 merges the helpers of a role into its class. Two of them are pieces of their own: `CliBaseDistance`, a
  150-line count of how far each branch is from its base, and the half of `CliPullRequest` that finds the remote of a
  pull request's repository by its URL. Each has its own tests, and merging them would make `CliBranches` and
  `CliRemotes` about 500 lines each.
- Decision 1 asks for short names within a role, but the table keeps the names of `GitInfoProvider`
  (`allBranches`, `parentBranch`, `moveCommits`, `remoteUrls`, …), which repeat the role in a call:
  `git.commits().moveCommits(…)`, `git.remotes().remotes()`.
- The role `Diffs` has the name of the model record `git.model.Diffs`, the staged and unstaged diff of the working
  tree.
- Decision 3 puts `@With` on the roles only, so a test cannot give a stub `Git` another worktree or name than the
  default one.
- Decision 7 splits the tests of the `Cli` classes per role. The role steps only moved the tests that had a class of
  their own; the rest stayed in `CliGitInfoProviderTest`, which step 13 renamed `CliGitTest`.

## Decision

1. **`CliBaseDistance` and `CliPullRequest` stay static helpers**, each called by one role only:
   `Branches.baseDistance` and `Remotes.pullRequestFiles`. A helper that a single role calls, and that is a piece of
   its own, need not be merged into that role's class.
2. **The operations are named within their role**, where the provider's name would repeat it:

   | Role | Name | Was |
   |---|---|---|
   | `Diffs` | `uncommitted` | `diff` |
   | `Branches` | `all`, `deleteLocal`, `forceDeleteLocal`, `rename`, `parent` | `allBranches`, `deleteLocalBranch`, `forceDeleteLocalBranch`, `renameBranch`, `parentBranch` |
   | `Commits` | `amendLast`, `amend`, `drop`, `move`, `moveTo` | `amendLastCommit`, `amendCommit`, `dropCommit`, `moveCommits`, `moveCommitsTo` |
   | `Remotes` | `list`, `urls` | `remotes`, `remoteUrls` |

   `Remotes.deleteBranch` and `deleteRemoteBranch` keep their names: `remotes().delete(…)` would read as deleting a
   remote.
3. **The role `Diffs` keeps its name.** Where it meets the record, in the `git` package, the record is written out
   in full; the app imports the record by name and does not see the role's name.
4. **A stub `Git` is made where it is**: `StubGit.of(worktree)` and `StubGit.of(worktree, name)`, next to
   `StubGit.of()`, give a stub repository elsewhere or under another name. `@With` stays on the roles only.
5. **The tests of `CliGitTest` are split per role in a later change**, as decision 7 of ADR 69 asks.

## Consequences

- A new git operation in base distances or pull request files edits a helper next to the role class, as before.
- The role names differ from those in the table of ADR 69; this ADR's table says which is which.
- Inside the `git` package, `net.vanfleteren.jazygit.git.model.Diffs` is spelled out where it is used.
- `CliGitTest` still tests most roles through one repository until it is split.

Amends ADR 69 (`0069-git-operations-in-roles.md`).

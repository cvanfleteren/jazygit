# 165. What a repository is made of is `core-repo`

Date: 2026-11-20

## Context

`git.model` holds 69 values, and they are of two kinds. Some say what a repository is made of: a `Sha`, a `BranchName`,
a `Commit`, a `Branch` with its `Upstream`, a `Tag`, a `Stash`. The others are what a git command takes or gives back:
a `UnifiedDiff`, a `Blame`, a `ResetMode`, a `DiffOptions`.

The first kind is also what GitHub talks about, and gh cannot name it. `core-git` and `core-gh` cannot see each other
(ADR 159), so a commit that GitHub reports is a `String` in `Build.sha`, and a branch is a `String` in
`Build.Origin.branch`. ADR 40 made `GhCommit` for the head of a pull request because gh could not use `Sha`. A
`FailedBuild`, which both a build and a check become, holds its commit as a `String` too.

Of the 69, none uses what runs git: the first kind uses only itself and Vavr, and only the diff values use a library
(`lib-syntax`). So the first kind can leave `core-git` without taking anything with it.

## Decision

- **A new module `core-repo`**, in the core, with the package `net.vanfleteren.jazygit.core.repo`. It depends on Vavr
  and on no other module of jazygit; `core-git` depends on it, and so, when it needs a commit or a branch, will
  `core-gh`. That git and gh do not see each other stays.
- **It holds what a repository is made of**, values that carry no operation of git:
  - names and ids: `Sha`, `BranchName`, `RemoteName`, `Tag`;
  - refs: `RemoteBranch`, `BranchRef`, `Revision`, `RemoteRef`;
  - branches: `Branch`, `AllBranches`, `CaughtUpBranch`, `Upstream`, `Tracking`;
  - commits: `Commit`, `CommitMessage`, `CommitRange`, `CommitGraph`, `Divergence`, `Ahead`, `PullRequestCommits`;
  - the state of a repository: `Head`, `Stash`, `Worktree`, `InProgress`, `StackLayer`.
- **A value belongs in it when it names or describes the repository itself**, and some part other than the one that
  runs git may need to name it. What a command takes (`ResetMode`, `PushMode`, `DiffOptions`, `NewBranchStart`), what
  it gives back (`UnifiedDiff`, `Blame`, `RepoStatus`, `FileEntry`) and the git format it is read from (`ConfigValue`,
  `GitMetadata`) stay in `git.model`. So do the values that need `lib-syntax`.
- **`ModelRules` holds `core-repo` to the rules of the models**: pure, final, values all the way down, no
  inheritance. `core-repo` has an architecture test of its own, with `CoreRules` and `ModelRules`.
- **The values do not change.** Only their package: `core.git.model.Sha` is `core.repo.Sha`. No behaviour, no key and
  no message changes.

## Consequences

- The work goes in steps, each leaving the program as it was:
  1. `core-repo` and the move of the values above, with this ADR and the module table of `ARCHITECTURE.md`.
  2. In `core-git`, a `Sha` where a commit is a `String`: the base of `CliBaseDistance`, the commit of `CliStatus`.
     `FailedBuild.commit` is an `Option<Sha>`.
  3. In `core-gh`, `core-repo` as a dependency, and a `Sha` for `Build.sha`, `workflowFile` and `FileAt`, and a
     `BranchName` for the branch of a build's origin. Then `GhCommit`, which only holds an object id, can be folded
     into `Sha`, with an `Option` where GitHub leaves it blank.
- `RepoStatus`, `FileEntry` and the values of the working tree stay in `core-git`. They are what `git status` reports,
  and nothing outside git names them. They can move later, one by one, when something does.
- The tests of the moved values go with them when they need nothing of git. `Fixtures` and `SampleData`, which build
  commits and branches together with diffs and files, stay in the test-jar of `core-git`, as do the tests that use
  them.
- ADRs written before this one name `git.model.Sha`, `git.model.BranchName` and the others; they are left as they
  were, and this ADR is where they went.

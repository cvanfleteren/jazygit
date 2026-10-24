# Plan: delete a branch whose pull request was merged

Carries out [ADR 30](../adr/0030-delete-a-branch-whose-pull-request-was-merged.md). Each step is a commit that passes
`./mvnw verify` on its own; test first where there is behaviour.

## 1. gh: the heads of the merged pull requests of a branch

`jazygit-core/…/gh`

- Model: `record MergedPullRequest(int number, Sha head)` in `gh/model` (or a `String head` if `Sha` lives only in
  the git model and the core module keeps gh free of it).
- `GitHub.mergedPullRequests(String head): Either<GhError, List<MergedPullRequest>>`, oldest first.
- `CliGitHub`: a `MERGED_PRS_QUERY` beside the others, with `owner={owner}`, `name={repo}`, `head=<branch>`:
  `repository(owner, name) { pullRequests(headRefName: $head, states: MERGED, last: 10) { nodes { number headRefOid } } }`,
  and a jq that prints `number<TAB>headRefOid` per line. Parse with the existing line helpers; a malformed line is
  `UnexpectedOutput`.
- `StubGitHub`: answer from a map by head name, empty by default.
- Tests in `CliGitHubTest`: the arguments gh is run with, parsing none, one and several, and malformed output.

## 2. git: count what is lost, leaving out the merged heads

`jazygit-core/…/git`

- `CliBranch.countOnlyOn(Path, BranchName, List<String> kept)`: the current args, then `--ignore-missing`, and the
  `kept` shas after `--not`. The existing overload delegates with an empty list.
- `GitInfoProvider.countOnlyOn(BranchName, List<Sha> kept)`; `CliGitInfoProvider` and `StubGitProvider` follow.
- Tests in `CliGitInfoProviderTest`, on a real repository:
  - branch with two commits, their head kept: 0;
  - a third commit after the kept head: 1;
  - a kept sha the repository does not have: counts as though not given (`--ignore-missing` works with `--not`);
  - check `--ignore-missing` placement against the installed git before relying on it.

## 3. `ForceDeleteCmd.refused` asks GitHub before the popup

`jazygit-app/…/feature/forcedelete`

- `refused(OperationCmd cmd, BranchName branch, RemoteBranch pushTarget, GitFailure<?> failure, Env env)`: takes
  `Env` instead of `GitInfoProvider`, for git and gh.
- Flow when git refused with `NotFullyMerged`:
  1. `lost = countOnlyOn(branch)` as now; failure to count → `cmd.failedWith(failure)` as now; 0 → force delete as now.
  2. Otherwise `env.gitHub().mergedPullRequests(pushTarget.branch().value())`. A `GhError` or no pull requests →
     `NotFullyMerged(cmd, branch, lost, Option.none(), commands)`, the popup as now.
  3. Else `remaining = countOnlyOn(branch, heads)`; failure to count → the popup with `lost` as in 2.
  4. `remaining == 0` → force delete, as in 1. Else `NotFullyMerged(..., remaining, Option.some(lastNumber), …)`.
- `ForceDeleteMsg.NotFullyMerged` and `ForceDeleteBranchPopup` get `Option<Integer> mergedIn`.
- Keep `refused` readable: split the fallback into a private method returning the `Msg`, rather than nesting a
  fourth `fold`.

## 4. Callers pass the push target

- `DeleteBranchCmd.DeleteBranch.run`: `ForceDeleteCmd.refused(this, branch, remote, failure, env)`; `remote` is
  already `branch.pushTarget()` from `DeleteBranchUpdate.chosen`.
- `RemoveWorktreeCmd`: `deleting` becomes `Option<Branch…>` carrying the push target, or a second field
  `Option<RemoteBranch> pushing`; filled at its 4 construction sites from `model.repo().branch(name)`, falling back
  to `RemoteBranch` of `origin/<name>` as `pushTarget()` does. Pick the one that keeps `busyBranch()` and the
  confirm paths simplest.

## 5. The popup text

- `messages.properties`: `dialog.forceDeleteBranch.afterMerge` =
  `{0,choice,1#1 commit|1<{0} commits} on {1} {0,choice,1#was|1<were} made after #{2} was merged and {0,choice,1#is|1<are} on no other branch. Delete anyway?`
  (match the existing key's wording style; add to every other `messages_*.properties` there is).
- `ForceDeleteDialog`: the new key when `mergedIn` is defined, the old one otherwise.

## 6. Tests of the update and the command

- `ForceDeleteTest` with the stubs:
  - merged pull request whose head covers the commits → deleted with force, no popup, commands logged;
  - commits after the head → popup with the remaining count and `#n`;
  - gh fails → popup with the full count, no error shown;
  - no merged pull request → popup as before;
  - the worktree path (`RemoveWorktreeCmd`) gets the same behaviour.
- Dialog text test for both keys.
- Acceptance tests (`acceptance/test_branches.py`) need no change: they run without GitHub; run them to confirm the
  popup still shows.

## 7. Wrap up

- `./mvnw verify` (checkstyle runs only there), with `JAVA_HOME=~/.jdks/openjdk-25.0.1`.
- Try it in the app on a throwaway GitHub repository: push, open a PR, squash merge with branch deletion, fetch,
  delete the branch → no popup; then the same with a local commit after the merge → popup naming the PR.
- PR referencing ADR 30.

# 30. Delete a branch whose pull request was merged

Date: 2026-10-24

## Context

Deleting a local branch runs `git branch -d` (#131). When git refuses because the branch is not fully merged, the
commits no other branch, remote-tracking branch or tag keeps are counted (`CliBranch.countOnlyOn`). With none the
branch is deleted with force; otherwise `ForceDeleteBranchPopup` warns that they will be lost and asks to delete it
anyway. Both the delete dialog (`DeleteBranchCmd.DeleteBranch`) and removing a worktree with its branch
(`RemoveWorktreeCmd`) go through `ForceDeleteCmd.refused`.

The common flow trips over it: a branch is pushed, its pull request is squash merged (or rebase merged) on GitHub,
and GitHub deletes the branch there. The branch shows as gone, but its own commits are on no other ref: main has a
new commit with the same changes. Deleting it warns that its commits will be lost, while they were not.

That the upstream is gone says nothing on its own: a branch deleted on GitHub without being merged, or whose pull
request was closed, is gone too, and a branch can have commits made after its pull request was merged.

The branches pane shows a branch's open pull request after its name (`BranchSegs.pullRequest`), read with the
other open pull requests (`LoadPullRequests`); once it is merged, the branch shows nothing of it.

## Decision

A commit that a merged pull request had is not lost. The merged pull requests of the local branches are read along
with the open ones; a branch whose commits all landed shows that it was merged, and is deleted without asking.

### Which commits a merged pull request had

- GitHub keeps the last commit of a merged pull request's head: `headRefOid`. Every commit it had is that commit or
  one of its ancestors, whichever way it was merged: merge commit, squash or rebase.
- Not: that the upstream is gone, which a branch deleted without being merged shares. Not: comparing patches
  without GitHub (`git cherry` on a commit with the branch's tree), which guesses: merging the base into the branch,
  a conflict resolved in the squash or "Update branch" on GitHub defeat it, and a wrong guess loses commits.
- So this only works for repositories on GitHub, as the other pull request features; elsewhere nothing changes.

### Reading the merged pull requests

- Every local branch without an open pull request is looked up, but the default branch. Not only gone ones: a
  branch whose pull request was merged while its branch on GitHub was kept is just as merged.
- It is looked up by the name of the branch on GitHub, the one it pushes to (`Branch.pushTarget()`), not its local
  name. A branch that was never pushed is looked up all the same; it finds nothing.
- One `gh api graphql` per 50 branches, with a field per branch:
  `bN: pullRequests(headRefName: $head, states: MERGED, last: 10) { nodes { number headRefOid } }`, in the
  repository gh resolves, as the other queries. The last 10, not only the last one: a name used again for a later
  pull request has several, and each head that had commits keeps them. Not: the last 100 merged pull requests of the
  repository, matched by name, which loses old branches without saying so, the ones most worth cleaning up.
- Pull requests from forks count: whatever pull request had the commits, they were merged.
- Then, in the same command, git counts per branch with merged pull requests what is still only on it, leaving out
  the commits their heads have:
  `git rev-list --count <branch> --ignore-missing --not --exclude=<branch> --branches --remotes --tags <heads…>`.
  `--ignore-missing` skips a head this repository never had, as when it was pushed from elsewhere; its commits then
  count as left, which errs on the safe side. Not: comparing the tip with the heads alone, which misses a tip behind
  its head, as after "Update branch" or a suggestion committed on GitHub.
- It is read whenever the open pull requests are (`PullRequestsUpdate.load`, `loadAnyway`), behind the same
  `reading` guard, so a branch's open pull request and its merged mark never disagree for long.
- A read that fails keeps what was read before, as the other reads (#134): no mark flickers with the network. That
  it failed is not shown.
- The model keeps, per local branch: its merged pull requests (number and head) and how many commits are left.

### The mark

- A branch whose merged pull requests left no commit shows `#123 🟣` where an open pull request shows: the number of
  the last one merged, in the class of a pull request, and the symbol of a merged one (`PullRequestSymbols`). No new
  class.
- A branch with commits left shows nothing of them: its ↑ count already says it has work, and the mark means one
  thing only, that it can go.
- Only in the local branches pane; not for remote branches or worktrees.
- Asking for the pull request of a marked branch shows the merged one in the pull request dialog, which reads it by
  number, rather than offering to create one. A branch with commits left is still offered a new one: a follow-up
  pull request from the same branch is legitimate.

### Deleting

- When git refuses and commits would be lost, they are counted again with the heads the model has for the branch.
  gh is not asked: deleting stays as quick as it was, also offline. Stale heads can only make it warn when it need
  not, never lose commits without asking, as git counts what the branch has now.
- None left: the branch is deleted with force straight away, as when none were lost to begin with.
- Some left: the popup asks as before, with only those commits, and names the last merged pull request:
  "1 commit on feature was made after #123 was merged and is on no other branch. Delete anyway?".
- Without merged pull requests in the model, not read yet, or never read as gh fails, the popup is as it was.

## Consequences

- Each read of the pull requests runs one more `gh api graphql` per 50 local branches, and a `git rev-list` per
  branch with merged pull requests.
- "Merged" is GitHub's word for it: a pull request merged into a base that was itself never merged, as in a stack
  whose lower branch was abandoned, counts as merged.
- The mark can lag a commit made since the last read; deleting recounts, so that only affects what is shown.
- `ForceDeleteCmd.refused` needs the heads of the branch, which the commands that delete get from the model when
  they are made.
- Tested by tests of the query and its parsing (`CliGitHubTest`), of the count against a real repository whose
  branch is squash merged, has commits after it, or has a head it never had (`CliGitInfoProviderTest`), of the
  update (read, kept on failure, the mark, the dialog of a merged pull request, deleting with and without commits
  left) and of the rows and the popup text. The acceptance tests run without GitHub, so they keep showing the popup.

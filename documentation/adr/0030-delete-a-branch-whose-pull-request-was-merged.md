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

## Decision

A commit that a merged pull request had is not lost: when the commits only a branch has were all in a merged pull
request, the branch is deleted without asking.

### Which commits a merged pull request had

- GitHub keeps the last commit of a merged pull request's head: `headRefOid`. Every commit it had is that commit or
  one of its ancestors, whichever way it was merged: merge commit, squash or rebase.
- The merged pull requests are asked for by the name of the branch on GitHub, the one it pushes to
  (`Branch.pushTarget()`), not its local name: `pullRequests(headRefName: $head, states: MERGED, last: 10)
  { nodes { number headRefOid } }`, in the repository gh resolves, as the other queries do. Not only the last one: a
  name used again for a later pull request has several.
- Pull requests from forks are not left out: whatever pull request had the commits, they were merged.

### What is still lost

- The commits only the branch has are counted again, now also leaving out those reachable from the heads of its
  merged pull requests:
  `git rev-list --count <branch> --ignore-missing --not --exclude=<branch> --branches --remotes --tags <oids…>`.
  `--ignore-missing` skips a head this repository never had, as when it was pushed from elsewhere; its commits are
  then counted as lost, which errs on the safe side.
- None left: the branch is deleted with force straight away, as when none were lost to begin with.
- Some left: the popup asks as before, with only those commits, and names the pull request they are not in:
  "1 commit on feature was made after #123 was merged and is on no other branch. Delete anyway?". When there are
  several merged pull requests, it names the last one.
- That the upstream is gone is not required, nor looked at. A branch whose pull request was merged while it still
  exists on GitHub has its commits on the remote-tracking branch, so git does not refuse it in the first place.

### When GitHub cannot tell

- gh is only asked once git refused and commits would be lost; deleting a merged branch asks nothing more of it.
- Without gh, offline, without a GitHub remote, or when the query fails, nothing changes: the popup warns about all
  the commits only the branch has, as now. That gh failed is not shown: the popup is the fallback.
- A branch that was never pushed is asked about all the same, by the name it would be pushed to; it has no merged
  pull request, so the popup shows as now. Not: leaving it out by whether it has an upstream, as a branch pushed
  without `-u` has no upstream, yet can have a merged pull request.

### Out of scope

- Telling squash merges apart without GitHub, by comparing patches (`git cherry` on a commit with the branch's
  tree). It guesses: merging the base into the branch, a conflict resolved in the squash or "Update branch" on GitHub
  defeat it, and a wrong guess loses commits.
- Showing in the branches pane that a gone branch was merged, or offering to delete all such branches at once.

## Consequences

- Deleting a branch whose commits would be lost waits for one `gh api graphql` before the popup shows, or the
  branch is deleted.
- "Merged" is GitHub's word for it: a pull request merged into a base that was itself never merged, as in a stack
  whose lower branch was abandoned, counts as merged.
- `ForceDeleteCmd.refused` needs gh and the name the branch pushes to, next to git; `DeleteBranch` has that in
  `remote`, and `RemoveWorktreeCmd` gets it when it is made.
- Tested by tests of the query and its parsing (`CliGitHubTest`), of the count against a real repository whose
  branch is squash merged, has commits after it, or has a head it never had (`CliGitInfoProviderTest`), and of
  `refused` and the popup text with the stubs. The acceptance tests run without GitHub, so they keep showing the
  popup.

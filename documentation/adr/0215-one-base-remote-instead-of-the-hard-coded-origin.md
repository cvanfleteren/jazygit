# 215. One base remote instead of the hard-coded origin

Date: 2026-11-28

## Context

`RemoteBranch.DEFAULT_REMOTE`, `origin`, decided what the main branch is (`origin/HEAD`), which branches count as bases
(ADR 20, 143), what a branch is compared with (ADR 46), where a branch without an upstream is pushed, what delete and
nuke protect, and where pull requests are created, browsed and edited. Meanwhile the forge already looked at `upstream`,
then `github`, then `origin` to find where the pull requests live (`PullRequestHome`, ADR 177).

In a fork checkout (`origin` is the fork, `upstream` the real repository) the pull requests were read from `upstream`,
but the base, its ahead and behind and "rebase on base" used the fork's `origin/main`, usually behind. A clone whose only
remote is not called `origin` had no main branch at all, and the push dialog started out with a remote that does not
exist.

## Decision

- **One value, the base remote**, picked by `BaseRemote` (`core-repo`): the remote the forge put the pull requests on
  (the home, ADR 177), else `checkout.defaultRemote`, else the only remote, else `origin` when there is one, else none.
  A remote that does not exist is never picked.
- **It is read with the branches.** `Branches.all(preferred)` takes the home of the forge, reads the remotes and
  `checkout.defaultRemote`, `remote.pushDefault` and `branch.<name>.pushRemote` in one `git config --get-regexp`, and
  returns them as `AllBranches.setup` (`RemoteSetup`); the model keeps it in `Repo.remoteSetup`, and
  `Repo.baseRemote()` is what the features ask. The kernel knows only a remote name: the forge feeds it through the
  home it already keeps (`ForgeHome`), so the kernel stays forge-neutral.
- **The load is told the home**, as it is told what the features state of the bases (`LoadBranches.preferred`). When the
  home changes, as the forges decide or the user picks another, the branches are read again
  (`RereadUpdate.branches`).
- **Replaced**: the main branch is the `HEAD` of the base remote, else `main` or `master`; the branches that count as
  bases and as the remote ones of `BranchRef.bases`, the candidates of the base dialog, the distances, the divergence
  from the base, "rebase on base", the default-branch protection of delete and nuke, and the browse, create and edit of
  pull requests and the stacks, when nothing more specific says (the upstream of the branch).
- **A branch without an upstream is pushed to** `branch.<name>.pushRemote`, else `remote.pushDefault`, else the base
  remote (`Branch.pushRemote`, `RemoteSetup.pushRemote`). The push dialog and *set upstream…* start out with it, and a
  branch counts as on its remote when it is there.
- **`DEFAULT_REMOTE` stays** only as the last resort where something must be written when the repository has no remote,
  and in `PullRequestHome`, which names the remotes by their usual names. `GhAccount` still reads the account from
  `origin`: it is the account of the user, not a base.

## Consequences

- In a fork, the base, its ahead and behind, the divergence and "rebase on base" go by `upstream`; delete and nuke
  protect the default branch of `upstream`, and the fork's `main` can be deleted.
- A clone whose only remote is `github` has its main branch, and a first push starts out with `github`.
- A repository with several remotes, none called `origin` and none picked, has no base remote: no remote main branch,
  the local `main` or `master`.
- The first read of the branches comes before the forge decides, with the git-only rule; a read follows when the home is
  known, so a fork's base changes once, a moment after opening.
- Each read of the branches takes one more git process, the `git config`.

Amends ADR 213 (the starting remote of the dialogs) and ADR 214 (the remote of the base).

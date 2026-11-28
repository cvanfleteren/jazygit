# 213. A branch can be pointed at another upstream, or at none

Date: 2026-11-27

## Context

The only way to give a branch an upstream was the first push of a branch that has none (`SetUpstreamPopup`, ADR 40),
and `git branch --unset-upstream` was never run. That leaves no way out of three common states: a branch made from
`origin/main`, which git makes follow `origin/main` (`branch.autoSetupMerge`), so that its ahead and behind and its pull
are against main until it is pushed once; an upstream that is `[gone]` after its pull request was merged; and a branch
that is to follow another remote's copy. "Upstream options…" only offered divergence views and a reset, which
surprises a git user.

## Decision

- **Two more choices in the upstream options**, for the highlighted branch, not only the checked out one: *set
  upstream…* and *unset upstream*. Both work in a detached HEAD, as they name the branch.
- **Set upstream** asks `<remote> <branchname>` in the text field a first push asks it in, starting out with the
  branch of the same name on the remote the branch follows, or on `origin` when it follows none, and runs
  `git branch --set-upstream-to=<remote>/<branch> -- <name>`. Git needs the remote-tracking branch, and does not fetch
  it: when the remote branches are read and it is not among them, the dialog stays open saying to fetch first
  (`BranchError.UpstreamMissing`); otherwise git's refusal is the error of the operation.
- **Unset upstream** runs `git branch --unset-upstream -- <name>`. A branch that follows nothing is told so, as for
  the reset; one whose upstream is gone still has it configured, so it can be unset.
- **`Branches.setUpstream` and `unsetUpstream` in `core-git`**, as `rename` is: `Either<GitFailure,
  List<ExecutedCommand>>`, logged. The feature's commands (`UpstreamCmd`) are operations of their own, and the
  branches are read again after them, which brings the new ahead and behind.

## Consequences

- No new extension point: the choices, the popup and the commands live in the `upstream` package of the branches
  feature.
- Pointing a branch at an upstream does not push or fetch anything; only its configuration changes.
- A branch whose upstream has another name than its own is pushed as `push.default` says (ADR 40), as before.

Amended by ADR 215: the dialog starts out with the remote the branch is pushed to, not always `origin`.

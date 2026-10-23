# 25. Push before creating a pull request

Date: 2026-10-23

## Context

Asking for the pull request of a branch that has none opens the popup that creates one. A branch that is not on its
remote yet, or not with all its commits, cannot get one: GitHub only knows what was pushed. Until now that showed an
error, "Push branch stack-1 before creating a pull request for it", after which the user had to push it and ask
again.

## Decision

Jazygit asks to push the branch first, and goes on to create its pull request once the push succeeded.

- A branch without an upstream on a remote, or with commits its upstream does not have, gets a confirm dialog
  instead of the error. It says where the branch goes: "stack-1 is not on the remote yet. Push it to origin/stack-1,
  then create its pull request?", or "stack-1 has 3 commits not on origin/stack-1. Push them, then create its pull
  request?".
- Cancelling does nothing.
- Confirming pushes the branch as a push from the branches pane does, but without asking more: one without an
  upstream is pushed to `Branch.pushTarget()` (its gone upstream, or `origin/<name>`), which it then tracks, rather
  than asking where in the dialog of the upstream; one that is ahead is pushed to its upstream, fast-forward.
- A branch that has diverged from its upstream is not offered a force push: that stays a deliberate step from the
  branches pane. It keeps an error, which says so.
- The push carries that it was started for a pull request: `CreatePullRequestCmd.PushFirst` wraps the `PushCmd` and
  reports its success as `CreatePullRequestMsg.Pushed`. That does what the success of any push does (logged, the
  branch released, the repository and the pull requests read again) and then asks for the pull request as before:
  the branch it came from, the title, the popup. Nothing is kept in the model for it, so nothing goes stale when the
  push fails or the branch goes.
- Asking needs only the other branches, the bases, and the name of the branch on its remote, which is the push target
  it was just pushed to, so it does not wait for the repository to be read again.
- When another popup is open by the time the title is read, the popup of the pull request does not open, as before.
  The branch is pushed then, so asking again goes straight to it.
- A failed push fails as any push does; no pull request is asked for.

Out of scope: a branch stacked on another that is not pushed either. Only branches on the remote are offered as
bases, so its parent is not one of them until it is pushed too.

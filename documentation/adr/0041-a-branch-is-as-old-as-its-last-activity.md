# 41. A branch is as old as its last activity

Date: 2026-10-28

## Context

The age a local branch shows is the time since its tip was committed: `%(committerdate)` of `git for-each-ref`, as
`Branch.tipTime`, which also orders the branches. A branch just made from `main` points at the tip of `main`, so it
shows the same age, like `20m`, until the first commit on it (#188). What one wants to see is how long ago one
worked on it: since it was made, and once one commits, since the last commit. Not since the last pull.

Git does not store when a branch was made, but its reflog does (checked with git 2.56):

- The oldest entry of a branch made by `git branch`, `checkout -b`, `switch -c` or `worktree add -b` says
  `branch: Created from <start>`, at the time it was made. Git writes these messages untranslated.
- A rename keeps the reflog; deleting a branch removes it.
- The `main` of a clone starts with `clone: from <url>`, at the time of the clone, and a remote-tracking branch with
  `fetch` entries.
- Checking out a branch from a remote, with `git switch <name>`, or `checkout --track -b` as jazygit does, also makes
  it: `branch: Created from refs/remotes/origin/<name>`, or `origin/<name>` as it was typed. So does starting one's
  own branch from a remote one, `git switch -c feature origin/main`, which then follows `origin/main` by default.
- There is no reflog after a clone for the branches it did not make, when the entries have expired (90 days by
  default) or with `core.logAllRefUpdates` off. The oldest entry that is left can then be a pull or a reset.
- `git log --walk-reflogs --glob=refs/heads/` lists the reflogs of every branch in one call, each newest first, with
  the full ref name: `refs/heads/<name>@{<time>}` with `--date=unix`. A ref name has no `@{`. It took 11 ms for the
  440 entries of jazygit's own repository.

## Decision

- A local branch is as old as its last activity: when its tip was committed, or when it was made if that is later.
  A new branch shows the time since it was made, a branch one committed on since then the time since that commit.
  The branches are ordered by it, so a new one comes first after the one checked out.
- When it was made is the time of the oldest entry in its reflog, only when that says `branch: Created from`. Not:
  any oldest entry, which after a clone or an expiry is the time of the clone or a pull.
- A branch made from the remote branch of its own name that it follows was checked out, not made: that is getting
  someone's work, like a pull, so the tip is its age. A colleague's branch of three months does not show `0s` once
  checked out. Not: ignoring every branch made from its upstream, which would take the age of `main` for
  `git switch -c feature origin/main`, the case of #188.
- Without that, when it was made is not known and the tip is the age, as before, without telling the user so. Not:
  the oldest commit not on the default branch, which costs a `git log` per branch and is still the parent's age for a
  new branch, the one that matters here.
- `CliBranch.all` reads the reflogs with one `git log --walk-reflogs`, next to `git for-each-ref`. Not: reading
  `.git/logs` directly, which does not work with the reftable backend, nor one `git reflog` per branch.
- `Branch` keeps what git says, `tipTime` and `Option<Instant> created`, and tells `lastActivity()` from them.
- The remote-tracking branches keep the time of their tip: their reflog is that of the fetches.

## Consequences

- A branch made a while ago whose last commit has an older committer date, as a test can make with
  `GIT_COMMITTER_DATE`, shows the time it was made.
- Each read of the branches runs one more git command, which reads the whole reflog of every branch.

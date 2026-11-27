# 214. Rebase on the base takes the remote one when the local one has nothing of its own

Date: 2026-11-28

## Context

ADR 91 made `(b) rebase on base branch` rebase onto the local base, never its branch on the remote: a base that is
behind its remote is pulled first. jazygit fetches at the start and every minute, so `origin/main` is fresh while the
local `main` is usually stale, and the everyday "catch up with main" took three steps (#718).

The rule had a reason. With a stack (ADR 72), the parent `feat/a` may have been amended or rebased here and not yet
force-pushed. Rebasing `feat/b` onto the stale `origin/feat/a` replays the rewritten `feat/a` commits on top of their
old versions: duplicates or conflicts.

## Decision

- `(b)` rebases onto the base's remote-tracking branch on `origin` when that exists, the local base follows it, and the
  local base has no commits that are not on it (not ahead). Then the remote one is the newer, and nothing here is lost.
- Else onto the local base, as before: it follows nothing on `origin`, or has commits of its own, which may be rewritten
  and not pushed.
- The dialog names the branch it uses, and its details say that it is what the remote has.
- The remote base is busy while its local branch is: a push or a pull of `main` moves `origin/main` too.
- `(s)` and merge take any highlighted branch of the remote branches tab, as they take a local one.

## Consequences

- `r` `b` brings a branch up to date with a stale local `main` in one step, with no pull first.
- A rewritten parent in a stack is still rebased onto as it is here.
- A base whose upstream is another remote or another branch stays local.

Amends ADR 91.

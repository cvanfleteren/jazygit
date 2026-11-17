# 142. No listing of only the commits not on the base

Date: 2026-11-17

## Context

ADR 20 gave the logs two ways to tell a branch's own commits from those it shares with its base: the commits on the
base are dimmed, and `b` lists only the commits not on the base, then the fork point marked `⑂ origin/main`. ADR 23
made `b` work in the commits of a branch listed in place of the branches, and ADR 131 had it leave out the builds of
the commits on the base too. One setting, `CommitsView.shown`, held it for all of them, and a search (`/`) while it
was on left out the commits of the base.

The dimming already shows which commits are the branch's own, in every log and without a key. The title counts them.
`b` is wanted for setting the base of a branch, in the branches pane.

## Decision

`b` and what it did are gone:

- The logs and the commits of a branch always list everything, with the commits on the base dimmed and the count in
  the title, as ADR 20 has it otherwise. The builds list all builds of the branch, with nothing to set those of the
  base apart.
- `b` is no longer bound, in the commits pane, the branches pane, the commits of a branch, nor the builds.
- A search looks through the whole log of HEAD again, never leaving out the base: `History.search` takes no branch to
  leave out.
- `CommitsShown`, `AheadMsg`, `AheadAction`, `AheadView.only`, the fork point mark and `BuildsView.shown` are removed.

The fork point is still read with the commits not on the base (ADR 56), and found by its sha, as before.

## Consequences

- One way to see a branch's own commits, not two; the panes take one key less.
- `b` is free in the branches pane, for setting the base of a branch.
- A branch with many commits on its base shows them all, dimmed, where `b` hid them. The divergence view still lists
  only those that differ.
- Nothing lists the fork point any more but the log itself. Reading it could be left out of the read of what is
  ahead, a git process fewer now and then; that is left for later.

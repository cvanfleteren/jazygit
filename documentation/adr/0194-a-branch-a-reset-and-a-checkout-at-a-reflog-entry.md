# 194. A branch, a reset and a checkout at an entry of the reflog

Date: 2026-11-24

## Context

The reflog of HEAD shows where HEAD has been, marks what only it still keeps, and says what an entry is (ADR 186,
187, 188). Nothing could be done with an entry yet, so a dropped commit was found but not brought back. Step 6 of
`documentation/plans/reflog.md` adds the actions on an entry; this is its first part, settled in a grilling: a new
branch at an entry, a reset to it and a checkout of it. Copying an entry to cherry-pick it comes later.

The log already has the three: `n` makes a branch at a commit and checks it out, `g` resets the checked out branch to a
commit (mixed, soft or hard), and space checks a commit out detached (ADR 137). Each finds its commit through
`Model.commit`, which the reflog's part already answers for its entries.

The one thing the log cannot know is what a branch was called. A deleted branch is the most common thing to bring
back, and the reflog still names it: `checkout: moving from feature-x to main` is HEAD leaving `feature-x`. That entry
is HEAD arriving at `main`; where `feature-x` stood when HEAD left it is the commit of the entry just older than it.

## Decision

- **The commit actions, as they are.** Each is bound in `Scopes.REFLOG` by the feature it belongs to, with the key and
  the flow it has on a commit of the log: `n` new branch and space checkout from the branches, `g` reset from the
  changes. The reflog adds keys, not flows.
- **A new branch at an entry is checked out**, as on the log, in a linked worktree after asking where.
- **Its name starts out as the branch HEAD left there**: an entry whose newer neighbour is `checkout: moving from X to
  …` suggests `X`, when `X` reads as a branch name (not a sha, not a revision like `HEAD~1`) and no branch `X` is there
  now. Every other entry starts out as the template of ADR 121. The newer neighbour is always read, as the reflog is
  read from the top. The help of `n` in the reflog says so.
- **Reset and checkout as on the log**: a hard reset asks first only when it would discard changed files; a checkout
  is detached, also at the tip of a branch, offers to stash what is in the way, and does nothing at the commit HEAD is
  at (ADR 137).
- **A lost entry gets the same actions**: a branch at it is how its commit is brought back.
- **The tab stays shown** after an action. The reflog is read again after the operation, and the highlight stays on
  its entry as the new one comes on top.
- **Not now**: copying an entry to cherry-pick it; a new branch that is not checked out; a menu of the branches at a
  commit; a hard reset refused while a rebase or merge is unfinished; actions shown disabled at HEAD's commit. Each
  would change the action on the log as well.

## Consequences

- A dropped commit, or a deleted branch, is brought back with `n` on the entry before it was left, without typing its
  name when the reflog still has it.
- The three keys behave the same in the log and the reflog; changing one changes both.
- A reset or checkout from the reflog can leave commits only the reflog keeps: they show as lost in the same tab, and
  undo is still to come (`documentation/plans/reflog.md`, steps 7 to 10).

# 199. The plan of an operation that stopped shows in the commits pane

Date: 2026-11-25

## Context

ADR 197 says where a rebase stopped, `REBASING 2/3 onto main · 1 conflict`, and ADR 198 resolves its conflicts a file
at a time. Neither says *what* it stopped at, nor what comes after it:

- The commits pane shows the log of the detached HEAD (`rebasing feature`): `onto`, with the commits rebased so far on
  top, under their new hashes. The commit being applied, the one whose conflicts are being resolved, is in none of the
  panes, and neither are the commits still to come.
- A cherry-pick or a revert of several commits says only which commit it stopped at, not how far it is.
- The resolve dialog calls the side of a rebase "the rebased commit", as the app did not read which commit it is.
- The header says what is in progress on one line after the name of the repository, cut off in a column of usual
  width.

Git keeps all of it in the files of its git directory: a rebase of the merge backend in `rebase-merge` (`done`, whose
last line is the step it stopped at, `git-rebase-todo`, the steps still to come, `msgnum` and `end`, the step and how
many there are), a cherry-pick or revert of several commits in `sequencer` (`todo`, the commit it stopped at and those
still to come, and `head`, where it started). Each line of those is a command and its commit, with the commit's subject:
`pick 1466668bd57f… # feat touches f` for a rebase, `pick df33d9f feat touches f` for a sequence.

## Decision

1. **The plan is read from git's files with the status**, in `core-git` beside `CliInProgress`, as a `Plan` of
   `core-repo` in the `RepoStatus`: where it started (`base`), the step it stopped at, the steps still to come, and
   which step of how many it is. Only the worktree shown is read so; the others keep what ADR 197 says of them.
   - A rebase of the merge backend: the stopped step is the last line of `done`, the steps to come are the lines of
     `git-rebase-todo` but its comments, the base is `onto`, the step and how many are `msgnum` and `end`.
   - A cherry-pick or revert of several commits: the stopped step is the first line of `sequencer/todo` and the steps
     to come the others; the base is `sequencer/head`; the step is the commits made since it, counted with
     `git rev-list --count`, plus one, of as many more as are to come.
   - A single cherry-pick or revert keeps no `sequencer`: its plan is the commit it stopped at alone, one of one, its
     subject read with `git log -1`, based on HEAD.
   - A merge is one step, and a rebase of the apply backend (`rebase-apply`) keeps no list of its steps: no plan, and
     what ADR 197 shows.
   - Each step is its command and the rest of its line: the commit and its subject, as git wrote them, or what an
     `exec`, `label`, `reset` or `merge` does. A command written short (`f`, `x`) is named in full, and the options
     of a `fixup` are left out.
   - The commits of the steps are read with one `git log --no-walk`, for the content panel to show them (3), and
     `Repo.commit` finds them there as it finds those of the log. A line written by hand may have no subject; it takes
     its commit's. A sequence abbreviates its commits (`pick a01227b feat 1`), which `git rev-parse` gives in full,
     as the app hands git only full ones (ADR 40); a commit git does not find leaves the step without one, shown as
     it was written.
2. **The commits pane lists the plan above the log**, newest on top, as the log is: the last step to come at the top,
   down to the next one, then the step it stopped at, then the log of HEAD, whose commits made since the base are
   marked done with a `✓`. They are the commits listed above the base, which are not matched to the steps; nor do they
   say what hash they had before. The pane knows no feature: the rows above the log are a key of it,
   `CommitsPane.ABOVE` (ADR 172), with the commit below which the log is done, which `features-midway` fills.
   - A step to come is dim, with its commit and subject; a command but `pick` says itself in a tag before the
     subject (`fixup`, `squash`, `edit`, `reword`, `drop`). A step without a commit, `exec`, `break`, `label`, `reset`,
     `merge` or `update-ref`, is its line as git has it.
   - The step it stopped at is marked `✗` with its conflicts, `✗ 1466668 feat touches f · 2 conflicts`, or, with none
     left, why it stopped: to edit the commit, at a `break`, an `exec` that failed, or resolved and waiting to go on.
3. **The plan is looked at, not acted on.** Highlighting a step shows its commit in the content panel, the stopped one
   too, which is what the conflicts are about; a step without a commit shows what was shown before. The keys that act
   on a commit do nothing on a step: none is a commit of the log. Editing the steps to come is the interactive rebase
   of `wanted.md`, not this.
4. **The steps the app's own rebases take are shown as git has them.** Amending an older commit, dropping, squashing
   and moving commits rebase too (ADR 30 and others): their plan shows the `fixup!` commit and the commands they wrote,
   as for a rebase started outside the app.
5. **Where it is goes in the titles and the dialogs.**
   - The title of the commits pane says which step of how many, `Commits · rebasing feature · 2/3`, when there are
     more than one.
   - `M`'s dialog says where it stopped in its title, `Rebase stopped at 2 of 3: feat touches f`, and what continuing
     goes on with: `Next: 2b5a554 feat 5`.
   - The resolve dialog (ADR 198) names the side of a rebase by the commit it stopped at, or the branch it is the tip
     of, rather than as "the rebased commit"; the latter is left for a rebase without a plan.
6. **The repository panel says what is in progress on a line of its own.** While an operation is in progress, or
   conflicts are left without one, the panel on top of the left column is a row taller, and the panes under it a row
   shorter, as a branch takes a third line (ADR 197):
   - The first line names the repository and what is checked out: `shop-api → (rebasing) feature` while a rebase
     detaches HEAD, as the branches pane shows it checked out.
   - The second, in the style of the header, says what it is in the middle of, as on the row of its branch:
     `REBASING 2/3 onto main · 2 conflicts`, `MERGING feature · 1 conflict`, `CONFLICTS (1)`; and, with no conflict
     left, `· ready to continue`.

## Consequences

- The commit whose conflicts are resolved is in view, and a highlight away from its diff. The rows above the log move
  the log down while an operation is in progress; the highlight starts on the top row, as before.
- A step's tag and its subject are what git wrote: a todo edited by hand shows what it was edited to.
- Each status read while a rebase, cherry-pick or revert is stopped runs one more git process, the `log --no-walk` of
  its steps' commits; a sequence of cherry-picks or reverts also `rev-list --count`, and `rev-parse` for its
  abbreviated commits. A merge, and a repository in the middle of nothing, none.
- The layout of the left column takes the height of the repository panel from the model (`LeftPanes.statusRows`):
  the borders the user dragged stay where they are, and the last pane gives up the row.
- The ✓ marks follow the log: a base that is not in the log as read marks none.

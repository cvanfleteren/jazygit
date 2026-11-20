# 167. A feature draws in the pane of another

Date: 2026-11-20

## Context

ADR 158 foresaw that a group of features draws in the pane of another, "GitHub's badges on the branches", through an
extension point the pane declares, and said none was needed yet. The branches pane is the first. It still names GitHub:

- `BranchSegs` builds the pull request, the checks, the merge and the review of a branch, with their symbols and
  times, from `core.gh` types and the state GitHub's reads keep.
- `BranchNode`, `LocalBranchesList` and `RemoteBranchesList` read the same state to group a stack of pull requests
  under a header, indent its branches, and list the pull requests of the stack that are not checked out.
- The stack itself, its actions (check it out, rebase it, push it, make one) and its texts were 16 files of
  `features-branches` (`branch.stack`), although a stack of pull requests is GitHub's: other forges have none.

So the kernel could not be built without GitHub, and a second forge would have had to edit the pane.

## Decision

- **The badges are a typed key of the branches pane**, `BranchBadges`. A feature fills it with a function from the
  model and a `BranchRef` to the segments of the row, and the text of them that changes with the time (when the checks
  are expected to be done, or how long they have waited). The local and the remote branches share the key; the answers
  are joined in the order of `Features.ALL`, and the app gives them to the pane as a service, as it gives the keymap
  (ADR 136). The pane composes the time of a row from the age of the branch and that text (ADR 124), so the wake-up of
  the redraw is unchanged. `BranchSegs` keeps what git gives: sync, base distance, parent, worktree, spinner.
- **The order of a row is the one it has**: under the name, which the spinner follows while the branch is busy, its
  age, sync, base distance, parent, the badges, then whether another worktree has it checked out. No setting orders the
  badges.
- **A group of branches is a second key of the pane, forge-neutral.** A feature gives the pane an ordered tree: a
  header row, its branches indented under it, trailing dimmed rows, and whether it is open. The pane knows no pull
  request, and the kernel has no word "stack": its view state is `grouped` and `closedGroups`, as `Stacking` and
  `closedStacks` are today. A stack of pull requests is the only group; a forge without stacks does not fill it, and
  its merge needs no special handling.
- **The stack is GitHub's, in `features-github`**: the 16 files of `branch.stack`, with their messages, keys and
  texts, as a feature of that module. It uses the groups of features before it (ADR 159): `features-github` comes after
  `features-branches` and attaches the stack's actions to the branches' scope, as the failed build's actions are
  (ADR 161).
- **The symbols and the checks text move to `features-github` with the last of what draws them**
  (`PullRequestSymbols`, `CheckTimesText`, `CheckRows`). The badges draw them from `features-github` already, but the
  pull requests screen, still in the kernel, draws them too; they move with it, and the legend of the help becomes a
  section `features-github` adds, as its settings are (ADR 163).
- **An architecture rule closes it**: `kernel..` imports nothing from `core.gh..`.

It is built as stacked pull requests on one chain, so that nothing is merged in an in-between shape and no bridge is
written that the next one would undo:

1. The features add timers and answer the kernel's events (ADR 166).
2. What GitHub reports becomes a `Part` of the model, not a field. The part is declared in the kernel for now: the
   pane, the stack's code and the pull requests screen still read it from below `features-github`.
3. The stack moves to `features-github` as it is, its keys under the root `github` (ADR 136).
4. The badge key of the pane, with GitHub's badge in `features-github`, and this ADR.
5. The group key of the pane, so the pane no longer reads GitHub's state.
6. `GitHubState`, its stores, its `Env` fields and its errors, the stack's errors among them, move into
   `features-github` once nothing below it reads them; the pull requests screen (#7), with the symbols and the legend,
   may have to come first.
7. The architecture rule, last, as it can only pass at the end.

## Consequences

- The kernel names no forge in the branches pane. A second forge fills the badge key; it fills the group key only if
  it has something to group.
- A row may look different between the pull requests of the chain; the last one restores it. The tests of the GitHub
  segments move once, to `features-github`, and the tests of the pane give it a fake filler.
- Without the app, as in the tests of the views, the pane has no badges: `BranchBadges.standard()` is empty there.
- ADR 158 and ADR 161 name the extension point that none had; they get a line "Amended by ADR 167".
- The stack's actions are no longer in the branches feature: a build without `features-github` has no stack.
- The review threads of the rich diff, which touch about 20 kernel files, stay for their own decision.

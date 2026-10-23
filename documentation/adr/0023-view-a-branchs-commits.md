# 23. View the commits of a branch

Date: 2026-10-23

## Context

The commits pane lists the commits of HEAD only. Highlighting another branch, local or remote, shows its log in the
content panel, but as text: its commits cannot be highlighted, and what one of them changed cannot be seen without
checking the branch out, or leaving the app. The divergence view (ADR 0020, `u`) lists commits of a branch in place
of the branches, but only those that differ from its upstream, and only for a local branch.

## Decision

### Opening and closing it

- Enter on a branch, in the local and in the remote tab of the branches pane, lists the commits of that branch in
  place of the branches. The checked-out branch is no exception: it lists what the commits pane does.
- Esc goes back to the branches, with the branch that was opened highlighted. So does `[` or `]`, which then switch
  tab as they do otherwise.
- The list stays open when the focus goes to another pane, and is as it was left when it comes back.
- The key hints show Enter as `view commits` on a branch, and Esc as `back` in the list.

### What it shows

- The whole log of the branch, `git log <branch>`, as `GitInfoProvider.log(BranchRef)` reads it: at most 300
  commits, as the commits pane, and no paging.
- The rows look as those of the commits pane, rails of the graph included: they are drawn by the same functions,
  `CommitsPanel.rows` and `CommitsPanel.marked`, so that they do not drift apart.
- The commits are compared with the base of the branch as ADR 0020 sets out, as the commits pane and the log of a
  highlighted branch are: those on the base are dimmed, and the title counts the others, e.g.
  `feature/x · 4 ahead of origin/main`. More than 300 ahead shows `300+ ahead` and dims nothing. A branch without a
  base, such as the main branch, is shown unmarked.
- `b` lists only the commits not on the base, then the fork point, dimmed and marked `⑂ origin/main`, as in the
  other logs. It is the same setting, `RepoView.onlyAhead`: turning it on or off in one log does so in all, and it
  does nothing for a branch without a base.
- In all the logs, the title no longer says `only` while `b` is on: it reads `4 ahead of origin/main` either way,
  rather than one way listing all and another listing only those. The list itself shows which it is, ending at the
  fork point. This changes what ADR 0020 has the title say.
- The title names the branch in place of the tabs, so clicking the title does nothing while the list is open.
  Clicking a row highlights it.
- Highlighting a commit shows it in the content panel as a commit highlighted in the commits pane is shown: the same
  header, diff and title.

### No actions

Only moving through the list, Enter, Esc and `b` work in it. Enter on a commit does nothing. No action of the commits
pane (reword, fixup, cherry-pick, …), nor of the branches pane (`u`, checkout, …), nor `/` search, applies.

### How it is held

As the divergence view is, and next to it, not through it nor a stack of views:

- `RepoView.branchCommits: Option<BranchCommitsView>`, holding the `BranchRef` and a `Preview` of the highlighted
  commit of its own.
- The commits are not held twice. While the list is open, the branch is the one highlighted in the branches pane:
  Enter was pressed on it, and the branches, which are not drawn, cannot highlight another. So its commits are the
  log of the highlighted branch, `RepoView.branchLog`, and what of them is ahead of the base is
  `RepoView.branchAhead`. Both are already read for it, and read again as they are for it: the log when the tip of
  the branch moved, e.g. after a fetch, and what is ahead when the tip of its base moved. A log read for another
  branch is not shown as its: the list says it is loading. Until what is ahead arrives, or when reading it failed,
  the list is shown unmarked.
- A scope of its own, `Scopes.BRANCH_COMMITS`, with Esc, `[`, `]`, `b` and the moves; a
  `SelectionMsg.BranchCommitSelected`; and a content area of its own, mapped to the branches pane, as
  `DIVERGENCE_AREA_ID` is. Enter is named in the keymap, as `Keymap.ENTER`, for the binding on a branch.
- When the branches are read anew, the same sha stays highlighted while it is still listed. When the branch is gone,
  the list closes, quietly.

## Consequences

- Opening a branch takes no git process of its own: its log and what is ahead were read when it was highlighted.
  Each commit highlighted takes one, as in the commits pane.
- The commits of another branch can be looked at, but not acted on: cherry-picking from it, or searching
  are left for later.
- The divergence view and this list are two drill-downs of the branches pane held side by side. A third would be the
  time to bring them under one.

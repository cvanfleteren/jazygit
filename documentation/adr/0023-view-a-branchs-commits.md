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
- The rows look as those of the commits pane, rails of the graph included. The rendering of a row and of the graph
  is taken out of `CommitsPanel` into a widget both use, so that they do not drift apart.
- The commits are not compared with a base: none is dimmed, and `b` does nothing there.
- The title names the branch in place of the tabs, so clicking the title does nothing while the list is open.
  Clicking a row highlights it.
- Highlighting a commit shows it in the content panel as a commit highlighted in the commits pane is shown: the same
  header, diff and title.

### No actions

Only moving through the list, Enter and Esc work in it. Enter on a commit does nothing. No action of the commits
pane (reword, fixup, cherry-pick, …), nor of the branches pane (`u`, checkout, …), nor `/` search, applies.

### How it is held

As the divergence view is, and next to it, not through it nor a stack of views:

- `RepoView.branchCommits: Option<BranchCommitsView>`, holding the `BranchRef`, the commits as a
  `Loadable<List<Commit>>`, and a `Preview` of the highlighted commit of its own.
- A scope of its own, `Scopes.BRANCH_COMMITS`, with Esc and the moves; a `SelectionMsg.BranchCommitSelected`; and a
  content area of its own, mapped to the branches pane, as `DIVERGENCE_AREA_ID` is.
- When the branches are read anew (`BranchesLoaded`) and the tip of the branch moved, e.g. after a fetch, its log is
  read again and the same sha stays highlighted while it is still listed. When the branch is gone, the list closes.
  What arrives for another branch, or for a list since closed, is dropped. This is as `reloadDivergence` does.

## Consequences

- Opening a branch takes a git process for its log, and each commit highlighted one more, as in the commits pane.
- The commits of another branch can be looked at, but not acted on: cherry-picking from it, comparing with a base,
  or searching are left for later.
- The divergence view and this list are two drill-downs of the branches pane held side by side. A third would be the
  time to bring them under one.

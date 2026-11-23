# 189. The branches pane has the tabs of any pane

Date: 2026-11-24

## Context

ADR 185 gave a pane tabs that the features add, and the commits pane took them, but the branches pane kept its own:
`BranchesTab {LOCAL, REMOTE, WORKTREES}` in `BranchesView.tab`, its own messages for `[`, `]` and a click
(`BranchesTabMsg`), its own title and hit test in `BranchesPanel`, and a callback through the app to focus the pane on
a click. Two mechanisms did the same thing, and no feature could add a tab beside the worktrees.

Switching the branches pane does more than show another list: it closes the divergence and the commits of a branch
listed in place of the branches, and on the worktrees it reads which have changes right away. The kernel also reads
which list is shown: the divergence and the builds belong to the local branches, the files lock holds only there, and
the worktrees' changes are read on the tick only while they are shown.

## Decision

- **Which tab the branches pane shows is kept as any pane's** (ADR 185), by its id in `RepoView.tabs`.
  `BranchesView.tab` is gone, and so is `Landing.branchesTab`: a landing's tabs carry it.
- **Its three lists are its own tabs, named in the kernel.** `BranchesTab` stays, but only as the names of those tabs,
  each with its `TabId`, as `CommitsPane.LOG` names the log; the kernel reads them through `RepoView.branchesTab()`.
  That is none while the pane shows a tab a feature added.
- **The features add tabs after the worktrees**, through `BranchesPane.TABS`, drawn and keyed as in the commits pane.
- **`[`, `]` and a click are those of every pane with tabs**: `Scopes.PANE_TABS`, hosted by each list of the pane,
  the divergence and the commits of a branch among them, and a click sends `ViewMsg.TabChosen`. The app no longer
  focuses the pane itself on a click: its mouse handler already does, before the pane sees the click.
- **What switching does is the branches feature's answer to `KernelEvent.TabShown`** for the branches pane. That
  event is told only when the tab shown changes, so a click on the tab already shown no longer closes a divergence;
  Escape does.

## Consequences

- One tab mechanism: the help lists the same "Previous tab" and "Next tab" for both panes.
- An update test that switches the branches pane installs the branches feature's reaction, as the model carries none.
- A tab kept by an id that no feature gives any more shows the local branches; `branchesTab()` says none until another
  tab is shown.

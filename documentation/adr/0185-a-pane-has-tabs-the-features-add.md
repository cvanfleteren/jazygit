# 185. A pane has tabs the features add

Date: 2026-11-23

## Context

The reflog of HEAD is to be shown beside the log, as lazygit shows it in a tab of its commits window (ADR 186). The
commits pane has no tabs. The branches pane has, but they are its own: `BranchesTab {LOCAL, REMOTE, WORKTREES}` is a
closed enum in `kernel-state`, and its titles, keys and clicks live in `BranchesPanel` and `BranchesTabUpdate`. A
feature cannot add a tab to it.

The pane could have shown the reflog in place of the log, through the `LISTING` key commit search fills (ADR 172). But
a listing holds only `Commit`s, drawn as commit rows, under the commit keys, which would put drop, squash and reword on
reflog entries. And the next feature to want a tab is in sight: tags (wanted.md).

## Decision

- **A pane declares a key for its tabs**, a `PaneKey<PaneTab>` beside itself as for its other keys (ADR 172), in
  `kernel-ui`. A pane shows every tab the features give, in the order of `Features.ALL` (`Fills.all`), after its own
  first tab. The commits pane declares `CommitsPane.TABS`, and its own first tab is the log, "Commits": without any
  feature, the pane is what it was.
- **A tab brings all it needs**: its title, its rows (a loadable list of its own items, drawn by the feature), the key
  scope its rows are acted on in, and what selecting a row sends, so the content panel shows what the feature says.
  The pane knows none of them.
- **The selected tab is in the model**, not in the widgets: what is read depends on it (the reflog is read only while
  shown, ADR 186), and updates must know it (ADR 2). The kernel keeps, for each pane, the id of its selected tab, an
  open value the feature gives, as it names a part; a pane with no selection shows its first tab. It is not saved, so
  a restart opens on the first tab; it stays when another worktree is opened.
- **`[` and `]` switch tabs, and a click on a title selects it**, drawn and hit as in the branches pane, so the two
  panes behave alike.
- **Commit search belongs to the Commits tab**: its line and listing (ADR 172) replace the log there, and the other
  tabs offer no search.
- **The branches pane keeps its tabs for now.** The key leaves room for a pane's own tabs as well as the features', so
  that Local, Remote and Worktrees can move onto it; that is in `TODO.md`.

## Consequences

- A feature that shows a list beside the commits adds a tab, and neither the pane nor the kernel changes for it.
- Two tab mechanisms live side by side until the branches pane moves.
- A tab's rows are not commits, so a tab's keys are bound in its own scope; an action that also makes sense there
  (reset, checkout) adds a binding to it, as checkout does for `BRANCH_COMMITS`.

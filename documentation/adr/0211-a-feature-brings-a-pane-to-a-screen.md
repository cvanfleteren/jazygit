# 211. A feature brings a pane to a screen

Date: 2026-11-27

## Context

ADR 158 and 208 moved the four panes of the repository screen into their groups of features, but the app still built
them: `JazygitApp` handed `LeftColumn` four factories, one per pane, and imported `FilesPanel`, `BranchesPanel`,
`CommitsPanel` and `StashPanel`. `LeftColumn` kept a field for each, and had two paths: the repository screen's four
fixed panes, and the panes a feature's `ScreenSpec` made (ADR 168). `ShownClocks` and `Spinners` asked the branches and
files panes for their times and spinners by name, although the features already fill `KernelSlots.CLOCKING` and
`SPINNING` (ADR 202). A new pane on the repository screen meant an edit to the app and to the column (#705).

The branches pane's times are not its own alone: the badges the forges put on its branches carry times too
(`BranchBadges`, ADR 210), and the pane hides them while a divergence or the commits of a branch show in its place. A
filler is given only the model, so the branches could not fill `CLOCKING` with them.

## Decision

- **`Feature.pane(screen, factory)` brings a pane to a screen**, the repository's or a feature's own. The factory is a
  `ScreenPane.Factory`, which makes a `LeftPane` of a `ScreenPane.Context`: the dispatch, how to open the content of a
  pane, the theme's highlights, the app's `Fills` and the clock that turns the spinners. `ScreenPane` is in the shell
  (`kernel.ui`), beside `Fills`, which the subpackages may not use.
- **The screen orders its panes**: `LeftColumn` makes every pane the features bring once, and shows those of the screen
  shown in the order `Screen.panes()` lists their ids, as it did for a feature's screen. No pane carries an order of
  its own. `ScreenSpec` loses its `panes`: the pull requests screen brings its three lists with `Feature.pane` too, so
  there is one way to bring a pane.
- **`LeftColumn` treats every screen alike.** A pane that the content panel mirrors on its own (`Mirrored.of(PaneId)`,
  those of the repository) always says when its highlight changed; on a screen whose panes the content panel mirrors
  as one, only the pane in use does, as before (ADR 168). The files pane is found among the panes made as the one that
  is a `FilesLeftPane`; it is asked to step (ADR 117) and to follow the pane in use (ADR 141) with what each pane of the
  screen says it follows, so the column no longer names the branches pane.
- **The groups bring their panes with a feature of their `panes` package**: `changes.panes.ChangesPanesFeature` brings
  the files, commits and stash panes and the files pane's times; `branches.panes.BranchesPanesFeature` brings the
  branches pane, its times and its spinners. They have no keys: the keys on a pane are those of the features that act
  on it. `Features.ALL` lists them first.
- **A filler may read the other slots**: `Feature.reading(slot, (model, fills) -> …)` gives for a slot from the model
  and the fills it is asked through, as the branches pane's times join those of the badges the forges give. A gated
  filler that reads gives nothing while its feature is off, as one that does not.
- **The pane ids stay the kernel's.** `PaneId.FILES`, `BRANCHES`, `COMMITS`, `STASH`, `Screen.REPOSITORY`'s list of
  them, and `Mirrored.REPOSITORY` stay constants of the kernel: the focus, the layout settings (one height per pane but
  the last) and the content panel name them (ADR 158). Only the construction moves. A fifth pane on the repository
  screen still adds its id to `Screen.REPOSITORY`, and the saved heights would need to adapt to a new count.
- **The content panel's own sources stay in the kernel** (`PanelSources`). They draw commits, stashes and diffs with
  bodies (`DiffBody`, `CommitBody`, `StashBody`, `BranchLogBody`) that draw through `ContentText` and the rich diff in
  `kernel-content`, which no feature uses (ADR 187). Moving them means moving the drawing of a diff below the features
  first, which is its own decision.

## Consequences

- `JazygitApp`, `ShownClocks` and `Spinners` import no pane, and `LeftColumn` has no case for the repository screen.
- A new pane on a screen is one `Feature.pane` in the group that owns it, and its id in the screen's list.
- The selection messages of the repository's panes are sent in the order of the screen (files, branches, commits,
  stash) rather than in the order the column listed them; each is about its own pane.
- A test of the column hands it `ScreenPane`s, as the app hands it those of its features.

Amends ADR 168.

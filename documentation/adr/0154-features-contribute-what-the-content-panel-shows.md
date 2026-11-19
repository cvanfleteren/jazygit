# 154. Features contribute what the content panel shows

Date: 2026-11-19

## Context

`ContentView` decided, for every pane, what the content panel shows: the diffs of the files, what a commit or a stash
changed, the log of a branch, its builds, a pull request. It had grown to 965 lines, with a branch per kind of content,
and `ContentArea` was an enum of the twelve areas they are shown in. Builds (ADR 131) needed a new `AreaId`, a
`ContentArea` entry, a body and a branch in `ContentView.branches` (#536, #544).

ADR 152 opened `Body`, so each kind of content draws itself. What gives each area its body was still closed.

## Decision

- A `ui.content.ContentSource` says what the content panel shows next to the pane it mirrors (`Mirrored`), if it is
  its to show, and none if not, so the next one is asked. It declares the areas (`ContentArea`) it shows it in.
- `ContentArea` is a record of its id and the pane whose highlight it shows, declared by its source. The focus code
  finds an area among the areas the sources declare.
- `Shown`, `Tone`, `Mirrored`, `ContentArea`, `ContentSource` and `ContentSources` live in `ui.content`, beside
  `Body`, so a group of features can implement a source (ADR 152). The pull requests screen is a `Mirrored` of its
  own, as its lists are not panes of the repository screen.
- A `Feature` lists its sources, as it lists its actions (ADR 127). The app gives the sources as a service
  (`ContentSources.Provider`, `StandardContent`), as it gives the keymap (ADR 136): those of the features in the order
  of `Features.ALL`, then the content panel's own (`PanelSources`), which answer for every pane. A feature's source
  thus comes before the content panel's, as the builds of a branch come before its log.
- `ContentView.of` is a fold: what the first source that answers gives, with the lines expanded, the version of a file
  and the diff filters of its areas applied to it (`Expanded`), for every source alike. With no source that answers,
  as without the app, it is loading. The launcher refuses to start without the service, as it does without a keymap.
- Each branch of `ContentView` is a source of its own in `jazygit-content` for now; moving the builds into
  `jazygit-github` is #545.

## Consequences

- A new kind of content is a source, a body and a line in its feature; `ContentView`, `ContentArea` and
  `ContentPanel` do not change for it.
- The order of the sources is their priority; nothing checks that two sources do not both answer for the same model,
  the first wins. Within one pane, the content panel's own sources go from what is open in it (the files of a commit, a
  divergence) to what it shows otherwise (the log).
- A source must declare each area it shows: an area it shows but did not declare has no element to draw it.
- What the files pane says in place of a diff becomes a note (ADR 117) in each of the sources of the files pane, not
  in `ContentView`.
- The expansion of the areas now applies to the pull request of the pull requests screen as well; it has no diff, so
  nothing changes for it.

Amended by ADR 155: a body can have keys of its own, content that is no diff is a `ScrolledContent.Other` of its
source, an area says what it takes the focus for, and the content panel is given its sources.

# 155. What an area shows has its own keys, scroll value and focus

Date: 2026-11-19

## Context

ADR 152 opened `Body`, and ADR 154 let a feature contribute a `ContentSource`. Three places still named a kind of
content, so moving the builds of a branch into `jazygit-github` (#545) would still have edited `jazygit-content` and the
app (#576):

- `AreaKeys` handled the keys of the builds by checking `state.content() instanceof ScrolledContent.Builds`.
- `ScrolledContent`, what an area keeps where it is scrolled to for, was a sealed interface in `jazygit-state` with ten
  variants, the builds among them: a new kind of content added one there, as `Body` had before ADR 152.
- `FocusKeeper.followBuilds` moved the focus into `AreaId.BUILDS` once the builds showed after `B`, with two fields of
  its own.

And `ContentPanel` found its sources through the static `ContentSources.standard()`, so only the app's sources could be
drawn, and a feature's tests of its content had to live in `jazygit-app`.

## Decision

- A `Body` can have keys of its own: `key(KeyEvent, KeyContext)`, none by default, which the area asks after the keys
  of its cursor. `KeyContext` holds the keymap and whether a search is kept in the area. The builds answer with their
  scope (ADR 131).
- `ScrolledContent` keeps the five diffs sealed: the content panel opens, versions and expands them, and so tells them
  apart. All other content is a `ScrolledContent.Other`, a non-sealed interface. Each such kind is a record of the
  source that shows it, in that source's package. An area keeps where it is scrolled to for it while it stays `equals`.
- A `ContentArea` says what is opened to show in it in a model, `opened`, none by default. `FocusKeeper` follows it
  for every area the sources declare: when it changes to another value, the area takes the focus from its pane once a
  frame draws it. When it goes while the area has the focus, the pane gets the focus back. The builds area opens for
  the branch `B` opened its builds for.
- `ContentPanel` is given its `ContentSources`: the app gives `ContentSources.standard()`, a test the sources it tests.
  Which id is an area, and what a step of `J` or `K` sees, go through those sources.
- `ContentArchitectureTest` checks that nothing in `jazygit-content` asks which kind of `ScrolledContent.Other` a value
  is, as it checks for `Body` (ADR 152). A test of the app's sources checks which of them answer for each pane, as
  nothing else checks that two sources do not both answer (ADR 154).

## Consequences

- A kind of content that is no diff is a source, a body, and if it needs them, a value to scroll for, keys and a
  focus of its own, all in its feature. `AreaKeys`, `ScrolledContent` and `FocusKeeper` do not change for it.
- Outside their own four files, their texts in the bundle and their line in `PanelSources`, which #545 moves, nothing
  in `jazygit-content` or the app names the builds now. `AreaId.BUILDS` stays in `jazygit-state` until then.
- The scroll area still keeps its own tools, search and go-to-line (ADR 126, 140), behind `AreaTool.standard()`. A
  body's keys are what a feature adds; a tool of its own is not needed yet.
- A body's keys are only asked once the area has drawn it, as the area needs the body it styled. The keys of the
  builds were asked as soon as the model showed them.

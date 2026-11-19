# 156. A feature keeps what it shows in the content panel in a content package

Date: 2026-11-19

## Context

ADR 154 and 155 made what the content panel shows open: a feature can contribute a `ContentSource`, whose `Body`
draws itself and has keys of its own. The builds of a branch (ADR 131) were the first kind of content to move out of
`jazygit-content`, into the GitHub feature (#545): their source, their body, and the text they draw (`BuildsText`,
`JobTrain`, `TabStrip`).

The rules of the features (`AppRules`) know one kind of view in a feature, its dialogs, by their name (`*Dialog`):
those may use TamboUI and the translated texts, and keep what is not a value. What a feature draws in the content
panel is a view too, but its parts are not dialogs, nor named after one thing: a source, a body, the text of a chart.

## Decision

- A feature keeps what it shows in the content panel in a `content` package of its own, beside its update and its
  messages: `github.builds.content`. `AppRules` takes the classes in such a package for views, as it takes the dialogs:
  they may use TamboUI and the translated texts and hold what is no value, as a `DateTimeFormatter`, and must only send
  messages, never run git or gh.
- Their texts are in the bundle of the feature's module, under its root (`github.builds.…`).
- The messages a source shows in place of its content, while it is read, when it could not be, or when there is none,
  are `Shown.loading`, `Shown.dim` and `Shown.whenLoaded` in `ui.content`, which the content panel's own sources use as
  well.
- A feature's sources are asked before the content panel's own (ADR 154). The builds were asked after a divergence and
  the commits of a branch, which can be opened over them, so the builds source leaves the branches pane to them while
  one is open, and they keep showing in its place.
- The model of the builds stays in `jazygit-state` (`BuildsView`, `AreaId.BUILDS`): only the views moved.

## Consequences

- `jazygit-content` has no file about builds; a kind of content a feature adds is a `content` package, a line in its
  `Feature` and texts in its bundle.
- The tests that draw the builds through the content panel stay in `jazygit-app`, which sees both the panel and the
  feature, in the package of the builds' content. A small `ContentViews` there gives them what the content panel shows.
- The rules `ContentArchitectureTest` keeps for `jazygit-content`, as holding no tuple, do not cover a feature's
  content package.
- A source that must give way to another that is asked after it says so itself, as the builds do; the order of the
  sources alone no longer says which wins.

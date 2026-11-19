# 152. Each kind of content draws itself

Date: 2026-11-19

## Context

What an area of the content panel shows was `ContentView.Body`, a sealed interface of seven records: a diff, the log
of a branch, its builds, a commit, a stash, a pull request and a note. What sets one apart from another was found by
switching over it, in several places: how `ContentPanel` draws it, whether `ContentText` draws it to the width of its
area, which keys its cursor takes in `AreaFit`, the hunk a range is open in, what a click on it does in `AreaMouse`.
Each new kind of content added a record and a case to each of those places (#536). `AppError` had the same closed
shape, and opening it let each feature keep its errors in its own files (#444, #464).

The groups of features cannot see `jazygit-content`: it is beside them, and only the app uses it (ADR 136). An
interface that a feature is to implement later (#544, #545) has to be below both.

## Decision

- `Body` is an open interface, `ui.content.Body`. A body draws itself, `draw(DrawContext)`, and says what sets it
  apart, each with a default for what most bodies are: whether it is drawn to the width of its area, the diff it shows
  and that diff changed, the keys of its cursor, the hunk a range from a line is open in, what a click on it does.
- It lives in `jazygit-ui`, in `ui.content`, with what it is drawn into and from: `Drawn`, `Halves`, `ShownChanges`,
  `ShownDiff` and `DrawContext`. Not in a module of its own: the features see `jazygit-ui` already, and `ui.content`
  uses only the parts of ui below the panes (`UiArchitectureTest`).
- The seven bodies are records of their own in `content` for now (`DiffBody`, `BranchLogBody`, `BuildsBody`,
  `CommitBody`, `StashBody`, `PullRequestBody`, `NoteBody`).
- No class in `jazygit-content` but a body asks which kind of body a value is (`ContentArchitectureTest`).

## Consequences

- A new kind of content is a new file that implements `Body`; `ContentPanel`, `ContentText`, `AreaFit` and
  `AreaMouse` do not change for it.
- The compiler no longer finds a place that forgets a kind of content, as an exhaustive switch did. A method of `Body`
  without a default is what makes each body answer; a default is right for most bodies, and a body that differs
  overrides it.
- `Drawn` and the halves of the split view are in `jazygit-ui`, though only the rich diff makes them so far; the note
  of the title joining two notes is a key of the views' bundle.
- What gives each area its body is still `ContentView`; letting a feature contribute one is #544.

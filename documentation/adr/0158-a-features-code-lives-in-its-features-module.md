# 158. A feature's code lives in its feature's module

Date: 2026-11-19

## Context

ADRs 152 to 157 opened the content panel and the model to the features: a feature draws its own content (ADR 156) and
keeps its own part of the model (ADR 157), as the builds of a branch do. Most of what the features keep and draw is
still in `jazygit-state` and `jazygit-ui`, below every group of features, as those name the features:

- `jazygit-state` holds the state, messages, updates and loads of many features: the search of the commits, the
  divergence, the commits of a branch, the stacks, the file filter, the pull requests, the checks, the reviews.
- The kernel names features where it reads the model as a whole: `RereadUpdate.shown` lists the views to read again,
  `Model.commit` looks in the results of the search, `LoadUpdate.commitsLoaded` runs the search again,
  `Update.stamped` tells `GitHubState` what started, and `Popup.checkTimesChanging` is a hook of the checks in every
  popup.
- `Targeted`, `Load` and `LoadMsg.Read` list the loads of features in their `permits`.
- The panes in `jazygit-ui` draw the state of features: the commits pane draws the search and the cherry-pick, the
  branches pane the divergence, the stacks and GitHub's badges, the files pane the filter, the lock and the reviews.
  `LeftColumn` builds the five panes itself.
- `AreaId`, `Scopes`, `Cmd.Slot` and `Notice` name the ids and notices of features, and the bundles of `state` and
  `ui` hold their texts.

So adding or changing a feature edits the kernel, and the features cannot be read, built or tested on their own.

## Decision

- **A feature's state, messages, updates, loads, keys, texts and drawing live in its feature module.**
  `jazygit-state` and `jazygit-ui` keep what every feature needs, and the extension points the features fill. Where
  the kernel names a feature, that place becomes an extension point the feature fills, once that feature moves. A
  later move needs an ADR only when it adds a kind of extension point.
- **The hooks of a part.** A `Part` (ADR 157) declares, with itself, what the kernel asks of it, so all a part does is
  declared in one place. `Parts` keeps each part with its value, by its name, to run them. There are two kinds:
  - *Queries*, a function each, which the kernel asks the parts it keeps for, as `rereads` (the loads of what it shows,
    which the kernel reads again once they failed, or with the diff options changed) and `knowsCommit` (a commit it
    lists).
  - *Events*, one function, `reacting`, which a part answers with a `Next`. The kernel tells the parts of a
    `KernelEvent`, a small sealed set of what happened, as `LogRead`: the log of HEAD was read again.

  A query or an event is added when the first part needs it. Only the parts that are kept are asked, in the order of
  their names, so a part at its initial value has nothing open, nothing to read again and nothing to answer.
- **The loads are open to the features**, as `Msg` is: `Targeted` and `LoadMsg.Read` are no longer sealed, so a
  feature declares its own loads and what they read, and `Target.inPart` keeps what one reads in a part. The retries,
  the jobs, and dropping what finds no target then hold for them as for the kernel's loads. `Load` and `DiffLoad`
  stay sealed: the kernel tells its own loads apart.
- **The model keeps the questions every feature asks**, as `Model.commit(sha)`, a commit the user can act on, and the
  parts answer them too: the log, then the parts, then the commits ahead of the base.
- **A pane lives in the group of features that owns most of what it shows**, in a `panes` package of it, as
  `changes.panes.commits`: the commits, stash and files panes in `jazygit-changes`, the branches pane in
  `jazygit-branches`, the pull requests screen in `jazygit-github`. A pane reads the parts of its group, and no
  feature uses a pane, so the group has no cycle. The rules take a `panes` package for views, as they take a
  feature's `content` package (ADR 156). The app gives `LeftColumn` the panes that live in the groups, as a
  `LeftPane`; the id of a pane is a constant of `PaneId` in `jazygit-state`, as the content panel and the focus name
  the panes.
- **A group that draws in a pane of another** fills an extension point the pane declares beside itself, a typed key
  as a part is: GitHub's badges on the branches, its reviews in the files pane. The app gathers what each feature
  gives for each, from its `Feature`, and hands it to the pane. None is needed yet; the first comes with the branches
  pane.
- **The ids and texts of a feature move with it**: its `AreaId` and its key `Scope` are its own constants, as
  `BuildsView.AREA` and `BuildsAction.SCOPE` are; `Cmd.Slot` becomes a value with an id, as `AreaId` is; `Notice`
  becomes open, each one with its own text, as `Body` is; and the texts go to its group's bundle.
- **The search of the commits is the first to move**: the line it is typed in and the search last run are one part,
  `changes.commit.search`, with its messages, its update and its load; the commits pane moves to
  `changes.panes.commits`, and draws them. `Parts` gains `rereads`, `knowsCommit` and the event `LogRead` for it.

## Consequences

- The search of the commits is no longer named in `jazygit-state` or `jazygit-ui`. The rows of a commit, which the
  branches pane and the blame draw as well, stay in `ui.commits.CommitRows`.
- `LeftColumn` builds the files, branches and stash panes and the pull requests screen itself until they move: the
  app gives it the commits pane only, for now. The cherry-pick stays in `CommitsView` until it moves; the commits pane
  reads it there.
- A part's hooks run on every message that asks them (a tick, a log read): they read a part's value and must stay
  cheap.
- A part's load writes into the part only while the part is kept, and while it still wants what was read, as any
  `Target`: a search cleared since is dropped.
- With each move, `jazygit-state` and `jazygit-ui` lose a feature, and the groups of features gain what tests it; the
  tests that need the app's keymap stay in `jazygit-app`, in the package of what they test.

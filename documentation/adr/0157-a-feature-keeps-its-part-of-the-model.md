# 157. A feature keeps its part of the model

Date: 2026-11-19

## Context

ADRs 152 to 156 opened what the content panel shows, so a feature draws its own content from its own module. What a
feature keeps in the model was still closed: the `Model` is a record in `jazygit-state`, below every group of features,
so each field of it, and each type it holds, had to be in `jazygit-state` too. ADR 156 left the model of the builds
there (`BuildsView`, `BuildsShown`, `Workflow`, `WorkflowRuns`, their messages and loads, `AreaId.BUILDS`), in a field
of `BranchesView`, and their keys' scope in `ui.keys.Scopes`. A feature that keeps something had to edit
`jazygit-state`, and a record of a pane's view, which every such feature touched.

The model already holds values it does not name: the open popup is a `Popup`, an interface each feature implements,
and content that is no diff is a `ScrolledContent.Other` of its source (ADR 155).

## Decision

- A feature keeps what it holds in the model beyond a popup under a `state.Part`, a constant it declares in its own
  package: `BuildsView.PART`, a `Part<Option<BuildsView>>`. A part has a name and an initial value.
- The `Model` holds the parts in one field, `parts` (`Parts`), without naming them: `model.part(PART)` reads one,
  `withPart` and `mapPart` change it, and `PART.lens()` is where it sits. A part never set has its initial value.
- A part is kept under its name, and is a plain value: two parts of one name and initial value are equal. Its name
  starts with the root package of the feature's module (`github.builds`). No list of the parts is kept: a test of the
  app (`PartNamesTest`) finds every constant that holds a part in the app's classes, and fails when two share a name,
  as the second would read the value of the first. A part kept by identity instead would need no such test, but it
  would be a record that is not equal to its copy, and the order of the parts, and so what a model prints, would
  change from one run to the next.
- A part set back to its initial value is not kept, so two models with the same parts are equal, and `drawn()`
  (ADR 14) needs nothing for them.
- The builds are the first: their model, their messages, their loads and their texts move to `github.builds`, and
  `BranchesView.builds`, `AreaId.BUILDS` and `Scopes.BUILDS` go (`BuildsView.AREA`, `BuildsAction.SCOPE`). As they
  share a package now, the messages the model of the builds had for itself (`BuildsViewMsg`) are `BuildsMsg`.
- What the panes or several groups of features read stays a field of the `Model`, as `gitHub` and the panes' views:
  a part is only seen by the modules that see the module that declares it.

## Consequences

- A feature that keeps something in the model changes nothing in `jazygit-state`, `jazygit-ui` or the app for it;
  `jazygit-state`, `jazygit-ui`, `jazygit-content` and the app name the builds nowhere but in comments.
- What reads the model as a whole, as `Model.drawn()`, `RereadUpdate.shown` or `Update.stamped`, cannot ask a part
  what it needs: no part has a say in what is drawn, read again or started yet. The builds need none of it, as only
  their own messages change them. A part that needs it, as a view that is read again on a tick, will need a hook
  declared with its part, which is for a later ADR.
- The `Model`'s Javadoc no longer lists all it holds; what is in its parts is in the features that declare them.
- A part's value is cast when read; only `withPart`, which takes a value of the part's type, puts one there, under a
  name no other part has.
- A part declared other than as a constant, as in a local variable, escapes `PartNamesTest`.

## Folded in: ADR 156 (2026-11-22)

ADR 156 gave a feature a `content` package for what it shows in the content panel, as `github.builds.content`.
`AppRules` takes the classes in such a package for views, as it takes the dialogs: they may use TamboUI and the
translated texts, and hold what is no value, and only send messages, never run git or gh. Their texts are in the
feature's bundle; what a source shows while it reads, when it could not, or when there is nothing, is `Shown.loading`,
`Shown.dim` and `Shown.whenLoaded` in `ui.content`. ADR 156 left the model of the builds in the kernel; this ADR moved
it.

One gotcha stays: a feature's sources are asked before the content panel's own (ADR 154), so a source that must give
way to one asked after it, as the builds do to a divergence or a branch's commits opened over them, has to say so
itself. The order of the sources alone does not say which wins.

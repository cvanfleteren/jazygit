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
- A part is the key it is kept under, equal only to itself: two parts never share a value, whatever their names, so
  no list of the parts is kept and nothing checks their names. The name tells a reader which part it is.
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
- A part's value is cast when read; only `withPart`, which takes a value of the part's type, puts one there.

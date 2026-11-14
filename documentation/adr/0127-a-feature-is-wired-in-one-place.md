# 127. A feature is wired in one place

Date: 2026-11-14

## Context

Since #444 and #468 the errors and the settings of a feature belong to it. What a new feature still edits outside its
package is two lists (#482):

- `Popups`, which named 14 classes, as `BranchFeature.popups(dispatch)`, `StashDialogs.popups(dispatch)`,
  `ThemeDialog.popups(dispatch)`, `ErrorDialog.popups(dispatch)`, …;
- `keys/Standard`, which named 18, as `StageKeys::register`, `CommitFeature::register`, `CommitFilesAction.ACTION.keys()`,
  `FileStepAction.ACTION.keys()`, …, and then `GlobalKeys::register`.

They name the features apart from each other, in another order and in three styles. Only `branch`, `commit`, `github`
and `worktree` had an `XxxFeature`; the other packages registered through `XxxKeys`, an `XxxAction.ACTION` or an
`XxxDialogs`. A contributor had to find both lists, pick the style, and keep the order.

`FeatureAction` already bundles an operation's keys and popups, and the four `XxxFeature`s were each a list of them with
the same two methods over it.

The help lists the keys of a scope in the order they are added. `Standard` interleaved the actions of `diff` and `files`
(`CommitFilesAction`, `FileFilterAction`, `AllFilesAction`, `FetchAction`, `ExpandAction`, `FoldAction`,
`VersionAction`, `CopyAction`, `FileStepAction`), so no order of one feature per package keeps every scope's order as it
was.

Only one popup is open at a time (`Model.popup` is an `Option`), and each dialog shows its own popup record, so the order
of the popups decides nothing.

## Decision

1. **A `Feature` is a record of the feature's actions**, `Feature(List<FeatureAction> actions)`, in `feature`, next to
   `FeatureAction`: `keys(Keymap)` adds their keys in their order, `popups(dispatch)` gives their popups. It is data,
   not an interface: no feature has more to it than its actions. `FeatureAction.register` and `FeatureAction.popups`
   move into it.
2. **Each top-level feature package that has keys or popups declares one**, as `public static final Feature FEATURE` in
   its `XxxFeature`. A package that registered through `XxxKeys` or `XxxDialogs` keeps them, and its feature wraps them in
   `FeatureAction.ofKeys` and `FeatureAction.ofPopups`. The help and the error popup are features too.
3. **`Features.ALL` lists them all, once**, in `feature`, in the order their keys are listed in a panel.
   `keys/Standard` folds the keys of `Features.ALL` and then adds `GlobalKeys`; `Popups` takes the popups of
   `Features.ALL`. Neither names a feature. `keys` depending on `feature` stays the one exception to the parts having no
   cycles, through `Features` now; taking it away would mean handing the keymap to every panel, and `Keymap` and
   `GlobalKeys` use the types of features anyway.
4. **The order is** stage, commit, cherrypick, discard, edit, stash, branch, forcedelete, github, worktree, files,
   fetch, diff, copy, and then those with popups only: theme, repos, settings, inprogress, help, error. This is the
   order that moves the fewest keys. In the files of a commit, `/` (filter) now comes first, before `esc`; in the diff
   and in the diff of a pull request, `J` and `K` (the next and previous file) come before the keys of the diff itself.
   No other scope, nor the global keys, changes.
5. **Not every package is a feature.** `ahead` has two actions, which `branch` and `commit` list where their keys
   belong; `layout` has only global keys; `start` shows its popups on the start screen (`StartApp`), not through
   `Popups`. A test checks that every other top-level feature package declares one `Feature` and that `Features.ALL`
   lists it, and that the three are left out on purpose.
6. **`GlobalKeys` stays as it is.** Its keys are the app's: some belong to no feature package (quit, the screen of the
   pull requests), and moving the others into their features would reorder the global keys in the help.
7. **No feature asks for `Keymap.standard()` while its class is initialised**: making the standard keymap initialises
   every feature. The help asks for it when its dialog is made, which is after.

## Consequences

- A new feature is wired in by its `XxxFeature.FEATURE` and one line in `Features.ALL`; a new operation of a larger
  feature by its subpackage and one line in its `XxxFeature`, as before.
- Every feature package has the same entry point, whatever it registers.
- The order of the keys in a panel is the order of `Features.ALL` and, within a feature, of its actions. A key that
  must stand elsewhere in a panel than its feature does is a reason to move the feature in the list, not to split it.
- `GlobalKeys` still names the features whose keys work everywhere; spreading those over their features is left for
  later.

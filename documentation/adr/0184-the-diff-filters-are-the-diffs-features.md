# 184. The diff filters are the diffs feature's

Date: 2026-11-23

## Context

The diff filters (ADR 144) were chosen in `features-changes`, but kept in the kernel: the settings `ui.diff-filters`
and `[[diff-filters]]` were `Settings.DIFF_FILTERS` and `FILTER_DEFINITIONS` in `kernel-settings`, with the filters
that come with jazygit in a file of its own there; the filters to choose from were `DiffFilters` in the kernel's
`preferences`; and whether all are switched off for the session was a field of `Preferences`. Since the features keep
their settings as `SettingKey`s of their own (ADR 163) and their state in a `Part` (ADR 157), these were the last
settings of a feature the kernel declared. The kernel itself needs only the filters to hide hunks with, as the content
panel draws a diff and as `J` and `K` step past a file whose changes they hide all of.

## Decision

- **`changes.diff` keeps them.** `DiffFilterSettings` declares the two settings, which `DiffFeature` keeps, and a part,
  `changes.diff.filters-off`, for whether all are switched off for the session. `FilterDefinition`, `FilterDefinitions`
  with its `diff-filters.toml`, and `DiffFilters` move beside it. The paths in the settings file stay as they were.
- **The kernel asks for `HunkFilters`.** `kernel.state.diff.HunkFilters` holds the filters enabled that can be used and
  whether they are off; `HunkFilters.of(model)` asks the `HunkFilters.Provider` found as a service, which the app gives
  (`StandardFilters`), as the content panel finds its sources (ADR 154). Without the app, as in the tests of the kernel,
  there are none.
- **A feature reads a file that comes with jazygit as the settings file is read**, through `SettingsFile.bundled`, so the
  TOML reader stays the kernel's.

## Consequences

- The kernel declares no setting of a feature any more.
- A new settings file writes `ui.diff-filters` after the settings of the features listed before the diffs, not right
  after the kernel's.
- The filters the user set are still only read, never written by the app (ADR 144). Editing them in the settings panel,
  as a list with a form as the agents are (ADR 164), would have the app write `[[diff-filters]]`; that is left for
  later.

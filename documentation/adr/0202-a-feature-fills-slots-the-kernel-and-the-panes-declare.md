# 202. A feature fills slots, which the kernel and the panes declare

Date: 2026-11-26

## Context

A `Feature` had two ways to contribute. Most kinds are a component of the record, and every new kind added one, with a
builder that copies all the others: `Feature.java` was edited by 14 commits from 11-18 on. Since ADR 172 there is a
second, open way: a pane declares a `PaneKey<T>` beside itself, a feature fills it with `filling`, and a new key edits
no kernel code; `Fills.gated` silences every filler of a feature that is off (ADR 171) without code of its own.

Three components had that shape but were not keys, as their kinds came from the kernel, not from a pane: `spinning`
(whether a view shows a spinner, ADR 171), `clocking` (the times a popup shows, ADR 171) and `picturing` (the pictures a
view may draw, ADR 191). Each had its own builder that joined it with the one before, and its own line in `gated()`.
Two of them give several values in a model, where a filler gave one or none.

## Decision

- **`PaneKey<T>` is `Slot<T>`**: the same record, a name and a type, as it is no longer only for panes.
- **A filler gives what is `Iterable`**: `filling(slot, Model → Iterable<T>)`, so an `Option` gives one or nothing and a
  `List` as many as it holds. `Fills.of` is the first that is given, `Fills.all` all that is, in the order of the
  features.
- **The kernel declares its own slots in `KernelSlots`**: `SPINNING` (a `Boolean`; a feature gives `true` while a view
  of it spins), `CLOCKING` (`ShownTime`) and `PICTURING` (`Picture`). `Feature` loses `spinning`, `clocking` and
  `picturing`, components and builders; `gated()` loses their lines, as `Fills.gated` covers them. The app asks
  `Features.FILLS` for them (`Spinners`, `ShownClocks`, `JazygitApp.sharpPictures`).
- **The other components stay**: what is read without a model (actions, setting keys, screens, running, applying), or
  gated otherwise than by giving nothing while off (timers are owned, reactions get the detection in front, legends
  are tagged with the feature, contents keep their areas, settings stay), does not fit a slot.

## Consequences

- A new kind of contribution that is worked out from the model, and gives nothing while its feature is off, is a slot
  declared beside what reads it, and edits no `Feature`. A kind of the other sort still adds a component.
- The kernel's slots are listed in one place, `KernelSlots`, as the components of `Feature` listed them.
- A spinner is filled as `model -> Option.when(showsSpinner(model), true)`, a little longer than the predicate it was.

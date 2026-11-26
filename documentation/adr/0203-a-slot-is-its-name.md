# 203. A slot is its name

Date: 2026-11-26

## Context

A `Slot<T>` (ADR 202) was a name and a `Class<T>`. The class was used for two things: `Fills` cast what a feature gave
to it, and two slots of one name but different types were different slots. A slot of a generic type had to write the
class with a double cast, as `(Class<PaneTab<?>>) (Class<?>) PaneTab.class` in `BranchesPane.TABS` and
`CommitsPane.TABS`. A `Part` and an `Env.Service` are a name without a class, and the model casts what it keeps
under them.

## Decision

- **A slot is a name**: `Slot.of(name)`, whose type is the one it is declared with. Two slots of one name are the
  same slot.
- **`Fills` casts unchecked**: a feature gives for a slot through `Fills.with` (by `Feature.filling`), which takes
  only what gives the slot's type, as the compiler checks where a feature fills it. The record's own constructor
  checks nothing, and is for `Fills`' methods.
- **No two slots share a name**: `SlotNamesTest` finds every constant of the app's classes that holds a slot, as
  `PartNamesTest` does for the parts, and fails when two names are the same. Without it, the second slot would be
  given what was meant for the first, and its reader would fail with a `ClassCastException`.

## Consequences

- A slot of a generic type is declared as any other, with no cast.
- A slot declared outside a constant, as in a test, is found by no test; it is the test's to keep its names apart.

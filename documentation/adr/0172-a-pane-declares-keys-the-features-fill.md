# 172. A pane declares keys the features fill

Date: 2026-11-21

## Context

ADR 158 moved the search of the commits into `features-changes` (`changes.commit.search`), with its part, messages,
update and load, and the commits pane into the same module (`changes.panes.commits`). The kernel names the search
nowhere, but the pane still does: `CommitsPanel` reads `CommitSearchView.PART`, makes the search's messages for the
line it is typed in and for Escape, and builds its title (with "(first 300)") and "No commits match". A feature that
lists other commits in the pane, or opens another line above it, would edit the pane.

ADR 158 said a group that draws in a pane of another fills "an extension point the pane declares beside itself, a
typed key as a part is". ADR 167 made the first two, `BranchBadges` and `BranchGroups`, but as fixed fields of
`Feature`, declared in `kernel-ui`: the branches pane is still there, though ADR 158 put it in `features-branches`. A
pane that lives in its group cannot declare such a field: `Feature` is in the kernel.

## Decision

- **A pane declares its keys beside itself, as `PaneKey<T>`s**: a name and the type the features give for it, in
  `kernel-ui` beside `Feature`. Two keys are the same when their names are. Their names are shaped as keys of a bundle
  but name no text, as those of a part are; the bundle tests skip them.
- **A `Feature` fills a key with `filling(key, Model → Option<T>)`**, kept in one open value, `Fills`, in place of a
  field per key. Every filler has that shape, so `gated()` silences them all alike while the feature is off (ADR 171).
- **The app gives a pane the fills of its features**, joined in the order of `Features.ALL` (`Features.FILLS`), through
  its constructor: the app builds the panes it gives `LeftColumn`, so no service is needed. Without the app, as in the
  tests of the views, a pane has `Fills.NONE`. For each key, the pane shows what the first feature that gives anything
  gives; nothing checks that two give something at once.
- **The commits pane has two keys**, in `CommitsPane`:
  - `LINE`, a `PaneLine` (in `kernel-ui` `widgets`, beside `FilterLine`, as the files pane can use it too): its text,
    and what to send when it is edited, on Enter and on Escape. While a line is open, it takes the keys that edit it,
    Enter, Escape and what is pasted.
  - `LISTING`, a `CommitsListing`: its title, its commits, what the pane says when there are none, and what to send on
    Escape. The pane lists them in place of the log, always flat, as a part of the log never joins up on the rails.
  Escape goes to an open line first, then to a listing, then lets go of the files locked to the commits (ADR 149), as
  it did.
- **The search fills both** from `CommitFeature`, through `changes.commit.search.content.CommitSearchShown`, which
  makes the title and the text of no matches; those texts move to `changes.commit.search.*`. The pane names nothing of
  the search.

## Consequences

- A feature that lists commits in the pane in place of the log, or opens a line above it, fills a key; the pane does
  not change for it.
- Turning `BranchBadges` and `BranchGroups` into keys belongs with moving the branches pane to `features-branches`; the
  files filter can become a `PaneLine` of the files pane once that moves. Neither is done here.
- What the search shows through the pane is tested without the pane (`CommitSearchShownTest`), and the pane with keys
  any feature could fill (`CommitsPaneKeysTest`); the tests that type a search in the pane stay in `jazygit-app`,
  which has the features' fills and the standard keymap.

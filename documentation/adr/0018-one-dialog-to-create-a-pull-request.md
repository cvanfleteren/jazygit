# 18. One dialog to create a pull request

Date: 2026-10-22

## Context

Creating the pull request of a branch took two popups: first a list to pick the branch it merges into, then a
dialog for its title, its body and whether it is a draft. The base offered first is nearly always the right one
(ADR 3), so the first popup was mostly an extra Enter; and once in the second, a wrong base meant cancelling and
starting over.

## Decision

The base is picked in the same dialog as the rest: one popup, `NewPullRequestPopup`, from top to bottom:

1. **The bases**: a list about 5 rows high, which scrolls, in the order of ADR 3, the parent first. The highlighted
   row is the base; there is no pick to confirm. Its border names it: `Into: development`, and while a filter is
   typed, `Into: development · filter: dev`.
2. **Title**, **Body** and **Draft**, as before.

The header is `Create pull request: '<branch>'`; the base is in the list.

### Keys

- The focus starts in the title. Tab and Shift+Tab go round the list and the three fields.
- In the list, the arrows move the highlight, and typing filters it straight away: no `/` first, as no letter means
  anything else there. Enter moves to the title, so it does not create the pull request by accident; Escape clears
  the filter, and when there is none, cancels.
- Ctrl+S anywhere, and Enter in the title, create it. Escape in the fields cancels.
- When the filter matches none, the list says so, and the base stays the one highlighted last, which its border
  still names. There is always a base.

### Opening

- The dialog opens once the parent and the title for the base offered first are read: `ReadParent`, then
  `ReadTitle`. Nothing moves or changes under the cursor once it shows, and nothing in it is loading.
- With one base, there is no parent to read; it still shows in the list.
- With none, there is no dialog: the error says there is no branch on the remote to merge into.

### The title follows the base

The title starts out as the subject of the oldest commit not on the base (as before), so picking another base reads
it again (`RereadTitle`, at every move, without a delay: it takes milliseconds). What is read replaces the title
only when it is for the base still picked, and the title is still the one read before: the popup keeps that one, and
a title typed back to it counts as untouched again. A title of the user's own is never replaced; the body and the
draft box are never touched. Ctrl+S while a title is read creates the pull request with the title shown.

### State

The popup holds the bases, the base picked, and the title read last. The highlighted row and the filter are the
model's `ListCursor`, as in every list of a popup. `BasePopup` and its messages are gone.

## Consequences

- This amends ADR 3 where it speaks of the base list as a popup of its own; the order of the bases and how the
  parent is found stay as they are.
- Moving through the bases runs a git process per base highlighted, while the title is untouched.

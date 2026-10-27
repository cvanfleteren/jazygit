# 38. A popup closes by itself

Date: 2026-10-27

## Context

Every feature that asks something in a popup declares a message for Escape, and a case that handles it. Of the 48
cases for a `Cancelled` message, 43 only closed their popup: `case XCancelled() -> Next.of(model.withoutPopup(X.class))`.
The error, help, checks, pull request and settings popups did the same under other names (`Dismissed`, `Closed`,
`InfoClosed`). Each was a record, a case in the update, the message handed to the dialog's spec, and a test of the
update, in 48 places; a new popup added one more.

`Model.withoutPopup(type)` already closes the open popup only when it is of that type, so that a popup that opened in
the meantime stays open.

The confirming side is alike: of the 33 `Confirmed` messages, about 14 only start the operation their popup stands
for, with `Update.confirm(model, X.class, popup -> Option.of(new XCmd(...)))`.

## Decision

A popup that does nothing but close on Escape needs no message of its own feature. The core has one message for it,
and the dialogs send it unless told otherwise. Built in two steps; the second, confirming, follows the same pattern.

### Closing (step 1)

- `Msg.PopupDismissed(Class<? extends Popup> type)` closes the open popup if it is a `type`, and does nothing else:
  `Next.of(model.withoutPopup(type))`. It is keyed by the class, not by a function, so that two of them are equal and
  the tests can compare what a dialog sent. Not: a message per feature, which is what is removed.
- The specs of the standard dialogs, `ConfirmDialog`, `TextInputDialog`, `ChoiceDialog` and `PickDialog`, send it for
  Escape by default, with the popup type they already hold. Their shorter constructors lose the `onCancel` argument;
  the canonical constructor keeps it. Not: an overload with and one without, which doubles the constructors.
- A popup that does more on Escape says so with `cancelledBy(msg)` on its spec, as `openingAt` reads for the choice
  dialog. These keep their own message: the theme, which puts back the theme it previewed; starting without a
  repository, whose folder and recent popups go back to the choice, and whose choice quits; a comment and the comments,
  which go back to the popup they came from.
- The dialogs that are not built on a spec (the commit and reword dialog, the new pull request, the checks, the help
  and the pull request dialog) send `PopupDismissed` themselves, as does `?` from the help.
- A message that closed two popups, one or the other (amend, and an operation in progress), becomes two specs that
  each close their own.
- What is not a popup keeps its message: the commit search line, the divergence and the commits of a branch.

### Confirming (step 2)

- A popup whose confirmation only starts an operation implements `ConfirmablePopup`, with
  `Option<? extends OperationCmd> confirm()`: the command it stands for, or none when what was entered cannot be used.
- `Msg.PopupConfirmed(Class<? extends ConfirmablePopup> type)` does `Update.confirm(model, type,
  ConfirmablePopup::confirm)`, and the specs send it for Enter by default when their popup is confirmable.
- A confirmation that reads the model, or does more than start the popup's command, keeps its own message: switching
  to a worktree, for one.

### The rule

A feature declares a message for closing or confirming its popup only when that does more than close it, or start the
command the popup stands for.

## Consequences

- Step 1 removes 43 `Cancelled` records and 5 others (`Dismissed`, three `Closed`, `InfoClosed`), with their cases;
  the error feature's message and update are gone altogether.
- A new dialog that asks and runs needs a popup, a spec and the message that opens it.
- Escape that should do more than close must be asked for with `cancelledBy`; forgetting it closes the popup, which is
  what the user asked for in most cases, rather than doing nothing.
- The tests of the updates that tested closing now test `PopupDismissed` for that popup; the dialog tests expect it
  for Escape. `ConfirmDialogTest` tests the default and `cancelledBy`.

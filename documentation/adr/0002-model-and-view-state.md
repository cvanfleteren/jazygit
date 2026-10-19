# 2. What lives in the Model, and what may stay in the widgets

Date: 2026-10-19

## Context

jazygit follows the Elm architecture (see `ARCHITECTURE.md`): one immutable `Model`, `Msg`s, a pure update, IO as
`Cmd`s. TamboUI, however, is a retained-mode toolkit: its lists, trees and text inputs keep mutable state of their
own (a selected index, expanded nodes, a text buffer with a caret), and focus lives in its `FocusManager`.

Mirroring all of that into the `Model` would mean re-implementing list navigation, tree expansion and line editing
as updates, and syncing it back into the widgets on every render, for state that no update ever reads. Leaving
everything in the widgets would put decisions the update has to make (what is selected, what was typed, which
dialog is open) out of reach of the pure update and its tests.

## Decision

**The `Model` holds everything an update reads or decides on. State that only the view needs to draw or navigate
may stay in the TamboUI widgets, as mutable view state.** The test is: does any update need it? If so, it's in the
`Model`.

For dialogs this means: **a dialog's content is in the model, only its caret is in the widget.** Every field sends
an edit message for each change, and the confirming message carries nothing, as its update reads the popup.

### In the `Model`

- The repository, GitHub and everything loaded from them (`Loadable`s).
- Which popup is open, and what it is about (`Model.popup`).
- What a dialog is filled in with: the cursor and filter of a popup's list (`ListCursor`), the text of a
  `TextInputDialog` (`TextPopup.text`, sent as `Msg.TextEdited` on every change), the message of the commit and
  reword dialogs (`MessagePopup.message`, sent as `MessageEdited`), the fields of a new pull request
  (`NewPullRequestPopup.pullRequest`, sent as `PullRequestEdited`).
- The item highlighted in each pane, which the view reports back (`SelectionMsg`, `PrOverviewMsg.Highlighted`).
- Which pane had the focus last, per screen, outside the popups (`Msg.Focused`, `Model.shownPane`,
  `PrOverview.listPane`), so a closing popup or a screen shown again can give it back.
- Where each area of the content panel is scrolled to, for the content it showed (`Model.scrolled`,
  `Msg.ContentScrolled`).

### In the widgets

- The cursor (highlighted row) of each pane's list or tree, and its scroll offset.
- The focus itself (`FocusManager`).
- Which directories of the files tree are collapsed.
- The caret of a text field. Its `TextInputState`/`TextAreaState` holds a copy of the text, synced from the
  popup whenever the two differ.
- Where Up and Down are in the earlier summaries in the commit dialog (`SummaryHistory`); the summary it fills in
  is sent to the model like any edit.

### How the two meet

- **Model → view.** Every render draws one `Model`; a panel keeps no copy of it between renders. Where a widget
  holds a copy of model data (a text field showing a filter, a dialog filled in from its popup), it is synced
  from the model whenever the two differ, e.g. `ListFilter.sync` and the `TextInputDialog`. So the fields show
  every change to the popup: the user typing, the message coming back from the external editor, a popup opening
  with its initial text.
- **View → model.** After each frame `JazygitApp.reports` compares the widgets with the model they were drawn from,
  and dispatches what differs (`SelectionMsg`, `Msg.Focused`), via `TuiRunner.runLater`, never during a render.
  Each report is idempotent. This is the only way view state reaches the model other than a user event.
- **Memoisation is not state.** Fields that only cache what was last built from the model (`FilesPanel.shown`
  and `roots`, `JazygitApp.shownScreen`) are allowed, as long as they are rebuilt whenever the model they came from
  changes.

## Consequences

- Updates stay pure and testable without a terminal: what they need is in the `Model`.
- List and tree navigation, scrolling and line editing come from TamboUI for free.
- Some state is not in the model, so the app can't be restored from a `Model` alone: a model replayed into a
  fresh view gets cursors at the top and expanded trees. Acceptable for now.
- The reports-after-render loop costs an extra frame when the view moves something, and requires each report to
  be idempotent.
- Revisit, per piece of state, when an update starts needing it: it moves into the model then.

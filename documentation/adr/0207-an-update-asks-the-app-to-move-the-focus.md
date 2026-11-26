# 207. An update asks the app to move the focus, and to step the files

Date: 2026-11-26

## Context

The focus, and the highlight of the files pane, are view state: the app and its widgets keep them, not the model
(ADR 2). Four messages move them, and the update could not ask for that, so `JazygitApp.send` matched each of them
before it handed it to the program:

- `CommandLogFocused` (`@`) moved the focus into the command log (ADR 129).
- `FileStepMsg` (`J`, `K` in the diff) moved the highlight of the files pane (ADR 117).
- `CommitFilesMsg.Closed` (Escape in the files of a commit) moved the focus back to the list they were opened from
  (ADR 141).
- `SelectionMsg.FilesUnlocked` (Escape in the working tree the locked list follows) did so too, but only from the
  files pane (ADR 149).

Run from the help, three of them came wrapped in `HelpMsg.Chosen`, and the app matched them again. There it dismissed
the help itself, and kept a flag of its own for each focus to take once the help was gone (`logAfterPopup`,
`listAfterPopup`), since a closing popup gives the focus back to the pane the model knows first. So the app imported
`HelpMsg`, `HelpPopup` and `CommitFilesMsg` of two features. Each new message that moves the focus would have been
one more case there, and one more if it can be run from the help.

## Decision

- **An update asks, with a `ViewIntent`**: a sealed set in `kernel.state.view`, of `ToCommandLog`, `ToPane` (with the
  pane that has to have the focus for it, if any) and `StepFile`. `Update.Next` carries them, as it carries whether
  the app stops; `Next.asking` adds them, and `then`, `map`, `and`, `quitting` and `Update.update` keep them, in
  order. `Update.Step` carries them on to `Program`, which hands each to a callback the app gives it, as it calls
  `quit`.
- **The update knows what it moves to**: `CommitFilesUpdate` and `SelectionUpdate` read the list to go back to from the
  model before they let go of the lock, so the app no longer reads it before it dispatches.
- **The help closes itself**: `HelpUpdate` already closed the help and ran the chosen message in one step. What that
  message asks comes out of the same step, so the app needs no case for `HelpMsg.Chosen`.
- **The app does what is asked once the message is handled**: it gathers the intents while the program applies the
  step, and follows them once the program returns, so a step of the files that tells the model there is no next file
  dispatches outside the program's own `apply`. A move of the focus while a popup has it, as the help that is closing,
  waits until the popup is gone; one field, `afterPopup`, holds it, and also the command log a popup opened from
  gives the focus back to.

## Consequences

- `JazygitApp.send` dispatches, and follows what was asked; it imports neither `HelpMsg`, `HelpPopup`,
  `CommitFilesMsg` nor `SelectionMsg`. `app.keys.Standard` still names `HelpMsg.Requested`, as wiring.
- A feature whose key moves the focus asks for it in its update, and it works from the help without the app knowing
  the message.
- The focus moves just after the model is updated, not just before. No frame is drawn in between, so nothing shows of
  it.
- `ViewIntent` is not only about the focus: `StepFile` moves a highlight the widget keeps. A new kind of view state an
  update needs to change is one more record there, which the app's switch lists.
- A message a command reports could ask too: the app follows that after the next frame is drawn.

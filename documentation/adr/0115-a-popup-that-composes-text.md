# 115. A popup that composes text

Date: 2026-11-13

## Context

Three popups write text for GitHub: a comment on a pull request (ADR 5), a comment on the lines of its diff, a reply
or the edit of one (ADR 60, 65), and the text of a review that approves it or asks for changes (ADR 100). They look
alike, as one view draws them all (`CommentComposer`), and they work alike: the text lives in the model as it is
typed; Ctrl+O opens it in the editor and puts back what was saved; Ctrl+S sends it, and the popup stays open while it
is sent, so text that fails to send is not lost; while it is sent, it can be neither changed, sent again nor left.

The view was shared, the rest was not. Each feature had its own six messages for it (`Edited`, `Confirmed`,
`Cancelled`, `EditorRequested`, `EditorClosed`, `EditorFailed`), its own command that opens the editor, its own
`editable` and `changed`, and the same cases in its update. `ReviewUpdate` had grown to over 800 lines, part of it
this.

## Decision

1. **A new part of the state, `state.compose`, holds what writing text in a popup is**, for any feature:
   - `ComposePopup`, a popup that holds text as far as it was typed and whether it is being sent;
   - `ComposeMsg`, the messages of what the user does in one: typing, sending, going back, and the editor;
   - `ComposeUpdate`, which handles them for whichever such popup is open;
   - `ComposeCmd.OpenInEditor`, which opens the text in git's editor, with the terminal handed over to it;
   - `EditorFile`, the file the editor opens and the operation it is logged under, which each popup names.
2. **The popup says what differs.** A `ComposePopup` says what sending it starts (`send`) and where going back takes
   the model (`back`), as a `ConfirmablePopup` says what confirming it starts (ADR 38). `ComposeUpdate` calls them
   only while the text is not being sent. What it leaves to them: whether blank text is sent, the checks that refuse
   it, and the popup it goes back to.
3. **What comes back from GitHub stays with the feature**: sending is a feature's own operation, and its `Sent` and
   `SendFailed` are its own messages. `ComposeUpdate.unsent` lets a failed send open the text for a new try.
4. **The view sends the messages itself.** `CommentComposer` sends `ComposeMsg`s; a dialog no longer passes it the
   messages of its feature.
5. **Editing a pull request (ADR 59) stays as it is.** It is a form, a title above a body, which is sent and left as a
   form; only its body is written in the editor.

## Consequences

- A popup that writes text for GitHub implements `ComposePopup` and its feature only handles what it sends.
- The comment, review and verdict features lose their six messages and their editor command each. What composing does
  for any popup is tested once, in `ComposeUpdateTest`; a feature's tests still send `ComposeMsg`s, for what its popup
  says.
- The popups keep the file their editor opens and what it is logged as: the text of a review is still logged as
  writing a comment.
- A popup now holds behaviour, `send` and `back`, that calls into its feature's update; a `ConfirmablePopup` already
  did, with less.
- Only one popup is open at a time, so a `ComposeMsg` needs to name none: it goes to the one that is open.

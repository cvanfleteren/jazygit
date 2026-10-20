# 5. Commenting on a pull request from its dialog

Date: 2026-10-20

## Context

The pull request dialog shows a pull request and lets the user open, draft, merge or close it. Adding a comment meant
going to the browser. The dialog only shows how many comments there are; reading them is wanted later, not now.

## Decision

The dialog gets a **Comment** action, key `c`, second in the list (after open, before draft). It adds a plain
conversation comment: `gh pr comment <number> --body <text>`. Reviews (approve, request changes) and inline comments on
the diff are not part of this.

It is available once the details are read, whatever the state: closed and merged pull requests take comments too.

### Writing the comment

`c` opens a comment popup in place of the pull request dialog, with a multi-line text area, as the body of the new
pull request dialog has: Enter adds a line, Ctrl+S sends, Escape goes back to the pull request. The text lives in the
model (`CommentPopup`). Only one popup is open at a time, so the comment popup keeps the pull request dialog it came
from, to go back to.

Ctrl+O opens the text in git's editor, as the commit dialog does. What is saved comes back into the popup, and Ctrl+S
still sends it, so the text is seen before it goes to GitHub.

- Blank text (only whitespace) is not sent: Ctrl+S does nothing. Other text is sent as typed, not trimmed.
- Escape drops the text without asking; nothing is kept for the next comment.

### Sending

Sending is an operation (`AddComment` cmd, `GitHub.commentOnPullRequest(number, body)`), whose command goes to the
log. The comment popup stays open until it is done, saying it is sending; meanwhile the comment cannot be changed, sent
again or left.

The branch of the pull request is **not** marked busy. A comment does not touch the branch, so it need not wait for
what is done to it, like a push or turning the pull request into a draft, nor hold that up. Marking it busy would also
overwrite the mark of such an operation still running, which would then free the branch too early when the first of
the two is done.

While the comment popup is open, what changes the pull request dialog (its details read, it becoming a draft) changes
the dialog the comment popup goes back to, so that one is never out of date.

- On success it closes, back on the pull request dialog, whose details are read again so the count of comments is
  current.
- On failure the error is shown under the comment, and the popup stays open with the text, so a long comment is not
  lost.

### The editor

`CliEditor.editMessage` only edits a `CommitMessage`, in a file named `COMMIT_EDITMSG`. It becomes a general
`editText(workTree, fileName, text)`, with editing a commit message built on it. The comment is edited in
`PR_COMMENT.md`, so editors treat it as Markdown.

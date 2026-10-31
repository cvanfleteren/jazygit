# 65. Edit a comment

Date: 2026-10-31

## Context

A comment can be deleted from the diff of a pull request's files and from the comments popup (ADR 64), but not
changed: a typo in a remark means going to the browser. ADR 64 left editing for later, with what it decided: the same
ids, the same rights, the same stop of the cursor, and the same rule of when it may be done. It already reads the
`edit` right of each comment, `viewerCanUpdate` in the diff and `viewerDidAuthor` in the conversation, and gives the
remarks of the tree and the comments of the popup a version, which goes up each time jazygit changes them itself.

Both reads have the `body` of each comment, its Markdown as written, so an edit can start from what GitHub holds. The
popup that `c` and `r` open in the diff (ADR 60) and the one `c` opens from the comments popup (ADR 5) write a comment:
Enter adds a line, Ctrl+S sends, Ctrl+O opens it in the editor, Escape goes back. The comments popup marks a comment
that was edited (`includesCreatedEdit`); the diff does not.

GitHub changes a review comment with GraphQL's `updatePullRequestReviewComment` and a comment of the conversation with
`updateIssueComment`, both by node id, and answers with the comment as it now is. It does not check whether the comment
changed since it was read: the last edit wins. Unlike deleting, the description of a pull request and the text of a
submitted review can be changed too: the description is already edited from the pull request dialog (ADR 59), a
review with `updatePullRequestReview`.

## Decision

### Where

- In the diff of a pull request's files: a comment of a review thread.
- In the comments popup: a comment of the conversation. Not: the description, which `e` points to the edit of the pull
  request dialog for (ADR 59): not two ways to change the body. Not: the text of a review, which is for later
  (`wanted.md`): it is one row of the popup, its rights are not read, and it is seldom changed; `e` on one says so.

### The key

- `e`, in `Scopes.PULL_REQUEST_DIFF` and in the comments popup, edits the comment of the cursor or the one
  highlighted. It is free in both; only `Scopes.FILES` has an `e`, which opens a file in the editor.
- In the diff, the comment is the one `d` takes (ADR 64): a comment, or the first comment of an open thread, whose stop
  is the thread; on a collapsed thread, only when it has one comment and GitHub none more than were read, else it says
  to open the thread to choose a comment.

### When it may

As `d` may (ADR 64), and else `e` says why, without asking GitHub:

- When the viewer has the `edit` right of the comment.
- Whatever the commits the remarks were read at, and whether the thread is outdated.
- Not while the comment is being deleted.
- Not on a comment of a thread that is being sent to, a reply or a resolve. Not: letting it, which an edit, unlike a
  delete, would not hurt: the version covers a read that meets it, but `d` and `e` keep one rule and one message.

### The popup

- The popups that write a comment, opened on the body of the comment, each where it writes one now, so that each goes
  back where it came from:
  - In the diff, `ReviewPopup` (ADR 60), with a target `Edit`, of the comment and the body it opened with, beside
    `Start` and `Reply`. It keeps the scroll of the content panel, as for a reply, to go back to.
  - In the comments popup, `CommentPopup` (ADR 5), with the comment it edits and the body it opened with, beside the
    popup it came `from`: it goes back to the comments popup, on Escape and once saved, with the edited comment still
    highlighted. Not: `ReviewPopup` there, which has no popup to go back to: the comments popup would be lost.
- Its title says it edits, and where: `edit · src/Foo.java · line 40` in the diff, `edit comment` from the comments
  popup. Above the text, in the diff, the lines of the thread, as `c` shows them, and for a reply the comment before
  it, as `r` shows the one it replies to.
- Enter adds a line, Ctrl+O opens it in the editor, Escape drops what was changed without asking, as ADR 5 and ADR 59
  do. It holds input, which stops a quit, only once its text differs from the body it opened with. Not: once it is not
  blank, as a new comment holds it, which an edit always is, from the start.
- Ctrl+S with nothing changed against the body the popup opened with closes it, and nothing runs, as ADR 59.
- Ctrl+S on a text that is empty or only blanks says, in the popup, that a comment cannot be empty and that `d`
  deletes it, without asking GitHub. Not: doing nothing, as Ctrl+S on a blank new comment does (ADR 5, ADR 60): an
  edit emptied is a delete, which `d` does. A new comment keeps doing nothing.

### Saving

- With GraphQL, by the node id: `updatePullRequestReviewComment` in `ReviewThreadMutations`, `updateIssueComment` for
  the conversation. Not: REST, by the other id.
- The popup stays open while it saves, saying so; meanwhile nothing in it can be changed, sent again or left, as a
  reply (ADR 60) and the edit of a pull request (ADR 59). So nothing else can be pressed meanwhile either, and the
  comment in the view needs no `· editing…`. Not: closing the popup at once with the comment marked in the view, which
  would leave the text to lose when GitHub refuses it.
- Once GitHub took it, the popup closes, and the comment of the model takes the body GitHub answers with, not the text
  typed: what is shown is what GitHub holds. In the comments popup it is marked edited. The version of the remarks, or
  of the comments, goes up, as for a delete (ADR 64), so that a read under way that started before is dropped and read
  again. Not: reading again, which what GitHub answered makes needless.
- In the diff, the cursor stays on the comment, whose anchor is the same.
- On failure, the error is shown under the text, and the popup stays open with it. A `NOT_FOUND`, as when someone else
  deleted the comment meanwhile, is a failure like the others, which says the comment is gone: the text stays, to keep
  with Ctrl+O. The comment stays in the view until `R`. Not: taken as done, as a delete takes it.

### Delivery

1. This ADR.
2. In `jazygit-gh`: the two mutations, each tested on its own.
3. `e` in the diff: the popup on the body, what refuses it, the body GitHub answers with put into the model and the
   version raised, with an acceptance test against a fake gh.
4. `e` in the comments popup, with the comment marked edited, and an acceptance test against a fake gh.

## Consequences

- A comment can be changed without the browser, from the diff or the conversation.
- The text of a review is still changed in the browser only, and so are the comments of others, by an admin.
- An edit overwrites one made meanwhile by someone else, unseen: GitHub does not check, and the last edit wins.
- The diff does not say that a comment was edited, as it did not before.

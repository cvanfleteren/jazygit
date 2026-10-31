# 64. Delete a comment

Date: 2026-10-31

## Context

A review remark can be written from the diff of a pull request's files (ADR 60), and a comment from the comments
popup (ADR 6, ADR 5), but not taken back: deleting a comment means going to the browser. ADR 60 read the node id of
each comment of a thread so that editing and deleting it could come later (`wanted.md` 15).

The cursor of that diff stops on each comment of an open thread; the first comment is the thread, and a collapsed
thread is one stop (ADR 62). The comments popup highlights one comment of the conversation, its description, comments
and reviews (ADR 6), by its id. The remarks are read when the tree opens, with `R`, and again after each comment sent
or thread resolved, to learn what GitHub made of it, as the ids of a new comment; not every so often (ADR 55).

GitHub deletes a review comment with GraphQL's `deletePullRequestReviewComment` and a comment of the conversation with
`deleteIssueComment`, both by node id; REST deletes them by their REST id. Neither deletes a whole thread: a thread is
gone once its last comment is. A review that was submitted cannot be deleted, so neither can what it says; one without
text whose comments are all deleted is gone too. GraphQL says of each comment whether the viewer may change it
(`viewerCanUpdate`) or delete it (`viewerCanDelete`); `gh pr view --json comments`, which reads the conversation
(ADR 6), has neither, only whether the viewer wrote it (`viewerDidAuthor`). A repository's admins may delete what
others wrote.

What GitHub does when the first comment of a thread with replies is deleted, tried on a pull request of a test
repository:

- The thread stays, with the same id, and its next comment becomes its first: GitHub moves the replies onto it
  (`replyTo` of the new first is `null`, those after it point at it).
- A reply to the REST id of the deleted comment is refused, `Parent comment not found` (404); to the new first, it is
  taken.
- Deleting a comment that is gone already answers `NOT_FOUND`.

## Decision

### Where

- In the diff of a pull request's files: a comment of a review thread.
- In the comments popup: a comment of the conversation. Not: the description, nor a review, which GitHub cannot delete;
  `d` on one says so.
- Editing is for later (`wanted.md`), with what is decided here: the same ids, the same rights, the same stop of the
  cursor, and the same rule of when it may be done.

### The key

- `d`, in `Scopes.PULL_REQUEST_DIFF` and in the comments popup, deletes the comment of the cursor or the one
  highlighted.
- In the diff, it deletes the one comment the stop is: a comment, or the first comment of an open thread, whose stop
  is the thread. On a collapsed thread, only when it has one comment, which deletes the thread; else it says to open
  the thread to choose a comment. Not: `d` on a thread deleting all its comments, more than what is seen.
- It asks first, always, in a popup that says who wrote the comment, when, and its first line: Enter or `y` deletes,
  Escape goes back. Not: an undo after it, which GitHub does not have, nor `d` pressed twice.

### When it may

- When the viewer may: `viewerCanDelete` in the diff. In the comments popup, `viewerDidAuthor`, which is all
  `gh pr view` says: an admin cannot delete what others wrote from jazygit. Not: reading the conversation with GraphQL
  for its rights, a change of the whole read for what is seldom done (`wanted.md`). Else `d` says why, without asking
  GitHub.
- Whatever the commits the remarks were read at, and whether the thread is outdated: unlike starting a thread
  (ADR 60), deleting does not number a line.
- Not while the comment is being deleted: then `d` and `Space` on it, and `r` and `x` on its thread when it is its
  first comment, say it is being deleted.

### Reading

- `ReviewThreadsQuery` reads `viewerCanUpdate` and `viewerCanDelete` of each comment, and `CommentsQuery`
  `viewerDidAuthor`, into rights of a comment, `edit` and `delete`, shared by the comments of a thread and those of
  the conversation; in the conversation, both are `viewerDidAuthor`. `edit` is not used yet, as the ids of ADR 60 were
  not, but editing will need it, and it costs a field of a query that is made anyway.

### Deleting

- With GraphQL, by the node id: `deletePullRequestReviewComment` in `ReviewThreadMutations`, `deleteIssueComment` for
  the conversation. Not: REST, by the other id.
- While it is sent, the comment says `· deleting…`, as a thread being sent to does (ADR 62).
- Once GitHub took it, the comment leaves the model: from its thread, and the thread with it when it was the last; from
  the comments of the popup. The first comment taken from a thread makes the next one its first, as GitHub does, so
  `r` replies to that one. Not: read again, which only the ids of what was sent need: what GitHub did is known.
  Others' changes come with `R`, or when the popup opens again.
- A `NOT_FOUND`, as when someone else deleted it first, is taken as deleted. Any other failure says why, and the
  comment stays.

### The cursor

- In the diff, the anchor of a deleted comment is gone, and the cursor goes to its thread, else to its line, as
  ADR 60 and ADR 62 say.
- In the comments popup, the next comment is highlighted, else the one before.

### Delivery

1. This ADR.
2. In `jazygit-gh`: the two mutations, the rights of a comment in `ReviewThreadsQuery` and `CommentsQuery`, each tested
   on its own.
3. `d` in the diff: the popup that asks, `· deleting…`, what refuses it, the comment taken from the model and where the
   cursor goes, with an acceptance test against a fake gh.
4. `d` in the comments popup, with an acceptance test against a fake gh.

## Consequences

- A comment can be deleted without the browser, from the diff or the conversation.
- Nothing deletes a whole thread at once: one comment at a time.
- An admin deletes the comments of others in the browser only.
- What is deleted is not read again: comments written by others meanwhile show after `R`.
- Editing a comment can follow by the same ids, rights and keys' rules.

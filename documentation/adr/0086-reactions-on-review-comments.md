# 86. Reactions on review comments

Date: 2026-11-06

Status: prototype (issue #359)

## Context

On GitHub, anyone who can read a comment can react to it with one of eight emoji: 👍 👎 😄 🎉 😕 ❤️ 🚀 👀. Reviewers
use them for a quick answer, such as 👀 for "looking at it" or 👍 for "agreed", without writing a reply. jazygit showed
none of them in the diff of a pull request's files (ADR 55), and giving one meant going to the browser (ADR 85).

GraphQL reads a comment's reactions as `reactionGroups`, one group per kind, with how many gave it
(`reactors.totalCount`) and whether the viewer did (`viewerHasReacted`). `addReaction` and `removeReaction` give and
take back one by the node id of the comment, which jazygit already reads (ADR 60). Giving a reaction twice, or taking
back one not given, changes nothing.

## Decision

### Reading

- `ReviewThreadsQuery` reads `reactionGroups` of each comment into `Reactions`: the kinds someone gave, in the order
  GitHub's site shows them, each with its count and whether the viewer gave it. It costs a field of a query that is
  made anyway.
- A kind of reaction jazygit does not know, as one GitHub adds later, is left out: not decoded as an enum that fails
  on it, which would fail the read of every thread for what is only shown beside them.
- It reads `viewerCanReact` too, into a third right of a comment, `react`, beside `edit` and `delete` (ADR 64). GitHub
  refuses a reaction on a locked conversation or in an archived repository, also to who may read the comment. In the
  conversation, which `gh pr view` reads without it, the viewer is taken to have it.

### Showing

- Under the text of an open comment, a line of its reactions: `👍 2  👀 1`. Those the viewer gave stand out, as the
  author does; the others are dimmed. A comment nobody reacted to has no such line. A collapsed comment or thread does
  not show them.

### Reacting

- `+`, in `Scopes.PULL_REQUEST_DIFF`, opens a popup of the eight reactions on the comment of the cursor, chosen by
  their number `1`–`8` or with the arrows and Enter. Choosing one the viewer gave takes it back, and the popup says so.
- The comment is chosen as `e` and `d` choose it (ADR 64, ADR 65): the comment of the cursor, or the first of an open
  thread. On a collapsed thread, only when it has one comment. Not while that comment is being deleted, or something is
  sent for its thread.
- When the viewer may (`viewerCanReact`); else `+` says why, without asking GitHub. The pull request may be at any
  commits, merged or closed: reacting does not number a line.
- Whether a reaction is given or taken back is told from the comment as it is when it is chosen, not as it was when
  the popup opened: a read of the threads may have landed meanwhile, with the viewer's reaction from elsewhere. The
  comment is looked up again then, as a delete is confirmed (ADR 64); one gone meanwhile, or now refused, closes the
  popup and says why.
- Not while a reaction to the same comment is being sent: `+` then says so. A second one would be chosen from what
  was read before the first landed, and could undo it rather than what was meant. Other comments take one meanwhile.
  The ids of the comments a reaction is sent for are kept beside those being deleted (`reacting`), and kept when the
  files are read again at commits that moved on.
- Once GitHub took it, the reaction is counted in the model without reading the threads again, at a newer version,
  as an edit is (ADR 65). A failure says why and changes nothing.
- A comment GitHub no longer has, as one someone deleted meanwhile, fails as gone (`GhError.Gone`, as an edit does,
  ADR 65): it then leaves its thread as one deleted here does (ADR 64), and the failure says so.

### Not, for now

- Reactions on the comments of the conversation (ADR 6). `gh pr view --json comments` reads `reactionGroups` too, so
  the same popup can follow.
- Showing who reacted.
- Marking a comment as `· reacting…` while it is sent: a reaction is quick, and `+` on it says it is being sent.
- A popup that changes with the comment while it is open: it says which reactions the viewer gave as it opened, and
  what is chosen is told from the comment then (above).

## Consequences

- A thread's reactions are visible in jazygit, and a reaction is one key and one digit away.
- Every comment read carries its reactions, a few more bytes per read.
- Emoji need a terminal and font that draw them two columns wide, as the markdown of comments already does.

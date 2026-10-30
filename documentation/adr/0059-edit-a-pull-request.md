# 59. Edit a pull request

Date: 2026-10-30

## Context

The title, body and base of a pull request are set once, when jazygit creates it (ADR 18). Changing them afterwards
meant going to the browser. Whether it is a draft can be changed from the pull request dialog (`d`), but only on its
own.

`gh pr edit` changes the title, the body and the base; it does not change whether a pull request is a draft, nor
does the `updatePullRequest` mutation behind it. That takes `gh pr ready`, as `d` does. The details the dialog reads
(`DetailsQuery`) have the title, the base and whether it is a draft, but not the body.

## Decision

The pull request dialog gets an **Edit** action, key `e`, after Comment (`c`). It edits the title, the body, the base
and whether it is a draft. Reviewers, labels, assignees and the milestone are not part of it.

It is available once the details are read, whatever the state, and whoever wrote the pull request: GitHub lets
collaborators edit the pull requests of others, and closed and merged ones too. When gh refuses, its error is shown.
The dialog only opens for a pull request of this repository (a pull request of another one opens in the browser), so
gh, and the branches the base is picked from, are always those of the right repository.

### Reading the body

`DetailsQuery` also reads the `body`, and `PullRequestDetails` keeps it. The popup starts out from the details read
last; there is no read of its own when it opens. The dialog does not show the body: it has a fixed height, and the
body would need a scrolling area of its own.

### The popup

`EditPullRequestPopup` opens in place of the pull request dialog and keeps it, to go back to, as the comment popup
does (ADR 5). It is laid out and keyed as the popup to create one (ADR 18), from top to bottom:

1. **The bases**: the list of ADR 18, 5 rows high, filtered by typing, its border naming the base: `Into: main`.
2. **Title**.
3. **Body**.
4. **Draft**.

The header is `Edit pull request #95: '<branch>'`. The focus starts in the title; Tab and Shift+Tab go round the
parts.

- The bases are the branches the popup to create one offers, the branches on the remote but the pull request's own:
  the current base first, highlighted, then the default branch, then the others in their old order. The parent of
  ADR 3 is not looked for; it only matters to a new pull request. The current base is listed also when it is not on
  the remote, as when it was deleted on GitHub or not fetched yet, so leaving it as it is is always possible.
- The title does not follow the base, as it does in ADR 18: it is a title of its own already, not one read from the
  commits.
- **The base cannot be changed** of a pull request stacked on another one (in a stack, at a position above 1, ADR 24),
  nor of a closed or merged one. **Whether it is a draft cannot be changed** of a closed or merged one. Such a part
  is still shown, dimmed, with what it is now; it does not take the focus, and Tab goes past it.
- Ctrl+O opens the body in git's editor, in `PR_BODY.md`, as a comment is edited in `PR_COMMENT.md` (ADR 5). What is
  saved comes back into the popup.
- Escape clears the filter of the bases while there is one. Otherwise it goes back to the pull request dialog and
  drops what was changed, without asking, as the comment popup does.

### Saving

Ctrl+S anywhere, and Enter in the title, save. Saving is an operation, whose commands go to the log:

1. `gh pr edit <number>` with `--title`, `--body` and `--base`, each only when it was changed, and only when one of
   them was;
2. then `gh pr ready <number>`, or `gh pr ready <number> --undo`, only when the draft box was changed.

- When nothing was changed, Ctrl+S closes the popup, back on the dialog, and nothing runs.
- A blank title (only whitespace) is not saved: Ctrl+S does nothing.
- What is changed is told against what the popup opened with, so a title someone changed on GitHub meanwhile stays
  when only the body is saved. A field changed both here and there is overwritten: the last one saved wins.
- The popup stays open while it saves, saying so; meanwhile nothing in it can be changed, saved again or left.
- The branch of the pull request is not marked busy, for the reasons of ADR 5.
- On success it closes, back on the pull request dialog, whose details are read again. When the base was changed,
  the pull requests are read again too, so what the branches show of them is current.
- On failure the error is shown under the fields, and the popup stays open with what was typed. When `gh pr edit`
  succeeded and `gh pr ready` did not, what was saved is what the popup counts as opened with from then on, so the
  next Ctrl+S only changes the draft.

### While it is open

The details read again while the popup is open (ADR 19) change the dialog it goes back to, so that one is never out
of date. They never change the fields, nor what the popup counts as opened with.

## Consequences

- This amends ADR 18: its layout and keys are shared by the popup to create a pull request and the one to edit it.
- The details read the body every time they are read, also when it is never edited.
- Changing whether a pull request is a draft can be done in two places, `d` and the popup. `d` stays for now; it may
  go later.

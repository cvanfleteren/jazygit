# 6. Viewing the comments of a pull request

Date: 2026-10-21

## Context

The pull request dialog says how many comments a pull request has, and can add one (ADR 5), but reading them meant
going to the browser. Comments are Markdown, often long, with code blocks and tables, so the 60 columns of the pull
request dialog are not enough to read them in.

tamboui renders Markdown (`tamboui-toolkit-markdown`, in the BOM jazygit already uses): headings, lists, quotes, code
blocks with syntax highlighting, tables and links. It measures how high the rendered text is, so it can be scrolled
to its end.

Three layouts were tried in a prototype (branch `prototype`, commit `b311148`): side by side, stacked, and one row per
comment with the highlighted one opened under it. Side by side reads best.

## Decision

The pull request dialog gets a **View comments** action, key `v`, after Comment. It is there once the details are
read, whatever the state of the pull request, as Comment is. It opens a comments popup in place of the pull request
dialog, which it keeps to go back to, as the comment popup does.

### What is shown

The conversation of the pull request, oldest first, as GitHub shows it:

- its description, first, unless it is empty;
- the comments;
- the reviews, with their text, or without when they approve, ask for changes or were dismissed. A review that only
  commented and has no text is left out: that is what inline comments on the diff hang from. Pending reviews, not
  submitted yet, are left out too: only their author sees them.

Inline comments on the diff (threads on a file and line) are not part of this. Bots are shown as anyone else: `gh pr
view` gives a bot's login without `[bot]`, so they cannot be told apart without asking GitHub more.

It is read with one `gh pr view <number> --json body,author,createdAt,comments,reviews` each time the popup opens (the
author and date are those of the description), and not kept: the pull request details are read as fast as before,
and the comments are always current. While they are read, the right of the popup says so, with a spinner. When reading fails, it shows why; Escape goes back, and `v` tries again.

### Layout

The popup is as big as the screen allows while still showing it is a popup: the screen less 4 columns on either side
and 1 line above and under, at most 160 columns wide, so lines of text stay readable.

- On the left, the list: one row per comment, at most 56 columns or two fifths of the popup wide. A row has a symbol,
  the author, how long ago, and the first line of the text, cut to fit. The symbol is 👍 for an approval, ✎ for
  changes asked, ● for a review that commented, ⊘ for a dismissed review, and none for a comment. The description
  says "description" instead of its first line. A comment GitHub hides (outdated, resolved, off-topic, spam…) is a
  dimmed "hidden · reason", and still shows in full on the right.
- On the right, the highlighted comment, as Markdown, in a box titled with its author, its date and time, the state
  of the review, and whether it was edited.

On a screen less than 100 columns wide, the list goes above the comment instead, as high as its rows need but at
most a third of the popup.

HTML comments (`<!-- … -->`), which bots fill their comments with, are taken out before rendering. Other HTML is
shown as tamboui shows it, as dimmed text. Every theme styles the Markdown in its own colours.

### Keys

- `j`/`k` or the arrows highlight the next or previous comment, Home and End the first and last. The popup opens on
  the last, the newest.
- `J`/`K` scroll the highlighted comment a line, Page Up and Page Down a page, as far as its end. Highlighting another
  comment scrolls back to its top.
- The mouse wheel over the list moves the highlight, over the comment scrolls it; a click on a row highlights it.
- `o` opens the pull request in the browser, as the pull request dialog does. Opening the highlighted comment itself
  was wanted, but gh only opens pull requests (`gh pr view --web` drops the `#issuecomment-…` of a link), and jazygit
  starts no browser of its own; that is for a decision of its own, as copying is.
- `c` comments on the pull request: see below.
- Escape goes back to the pull request dialog.

Quoting the highlighted comment in a reply, and copying it, are not part of this. jazygit cannot copy to the
clipboard yet; that is for a decision of its own, which SHAs and branch names want too.

### Commenting from the comments

`c` opens the comment popup of ADR 5. The comment popup now keeps the popup it was opened from, the pull request
dialog or the comments popup, and goes back to that one on Escape and once the comment is sent. Sent from the
comments popup, the comments and the details of the pull request are both read again, and the new comment is
highlighted.

### The model

The comments popup is in the model: the pull request dialog it goes back to, the comments as far as they were read,
and the id of the highlighted comment. The id, rather than a line, keeps the highlight on its comment when they are
read again, and selects the new one after commenting. The model's `ListCursor` cannot hold it: opening the comment
popup resets it, so it would be lost on the way back.

How far the highlighted comment is scrolled is view state (ADR 2), in the dialog: it is only known once the comment is
rendered, at a width, and starts at the top for every comment.

As ADR 5 does for the comment popup, what changes the pull request dialog (its details read, it becoming a draft)
changes the one the comments popup goes back to, also when the comment popup is open on top of that.

# 96. Opening the links of a comment

Date: 2026-11-09

## Context

The comments dialog shows a comment as Markdown, through TamboUI's `MarkdownElement`. A link `[text](url)` shows only
its text: the link is not on the screen. TamboUI puts it on the style of each cell of the text, as a `Hyperlink`, and
the backend writes it to the terminal as OSC 8, `ESC ] 8 ; ; <url> ESC \`. Whether that can be clicked is up to the
terminal: Konsole ignores it unless a setting of the profile allows it, tmux drops it without
`terminal-features hyperlinks`, and some terminals do not know it at all.

A link that is on the screen as it is, as in the log of a branch, is found by most terminals themselves. In a comment
the pane is narrow, and a long link is wrapped over lines, where the terminal no longer finds it whole.

And a terminal opens a link in the browser of the desktop, while a repository's links should open in the browser of the
account it uses (ADR 88): the one that is logged in to GitHub as that account.

## Decision

- A left click on a link in the comment of the comments dialog opens it in the browser, from jazygit.
- The browser is the one for the account the repository uses (ADR 88), else the one gh would open a link to the
  repository's host in, in gh's order (ADR 85), started by jazygit as it starts one for a review comment. gh opens no
  link of its own choosing, unlike a pull request: without a browser, on another system than Linux without one set,
  opening it fails with an error that says how to set one.
- Only a link to the web, `http` or `https`, opens. A link within the page (`#notes`), a relative one or a `mailto:`
  opens nothing; it is clicked on as text.
- `MarkdownScroll` keeps where it drew each link, from the cells its markdown drew, when it renders: view state, as
  how far it is scrolled is. It forgets them when another comment, or none, is shown.
- The click is a `CommentsMsg.LinkChosen`, with the link; the update starts an `OpenLinkCmd`, through
  `GitHub.openLink`, as `o` starts opening the pull request. The dialog stays open.
- The command log has the link as its subject, so it can be copied from there.
- The hint of the dialog stays as it is: a click on a link is what a user tries anyway.
- OSC 8 is still written: a terminal that knows it, and is asked with a modifier held as it is for any link while the
  app captures the mouse, opens the link itself, in its own browser.
- Not: a key to open a link. A comment can have many; choosing one by key needs a list of them, left for when it is
  asked for.
- Not: the links of review threads in a diff (`RemarkLines`). A click there moves the cursor of the rich view; they
  can follow through the same `GitHub.openLink`.
- Not: links that are only text, without Markdown's brackets. The Markdown parser makes no link of them; the
  terminal can still find one that fits on a line.

## Consequences

- Links in comments open with a click in any terminal, logged in as the repository's account.
- Opening a link runs gh to read its active account and its browser setting, as opening a review comment does.
- A link opened by mistake opens a browser; there is no confirmation.

# 94. Copying lines of a diff

Date: 2026-11-08

## Context

The text of the content panel cannot be selected: the app captures the mouse, for clicks, the wheel and dragging the
borders, which keeps the terminal from selecting. With Shift held most terminals select all the same, but they select
whole rows of the screen, the panes and the borders with them. TamboUI 0.6.0 can neither select text nor copy it.
jazygit copies nothing yet; ADR 6 left that for a decision of its own, which SHAs and branch names want too (#291).

The rich view has a cursor, on the lines of a diff, the bar of each file and more, in every rich diff (ADR 60). In the
diff of a pull request's files, `v` opens a range from it in one hunk; elsewhere the cursor only moves, though the
content panel draws and keeps a range in any rich diff. Its keys reach the features as a `DiffCursorAt`, with the area, what it shows and the anchors of
the cursor and the mark; `DiffLines` gives the lines the cursor stops on in the order of the diff, as the review
quotes a range from them, also those expanded in the area (ADR 77). A line of a hunk has its text without its marker.

A terminal is asked to copy text with OSC 52, `ESC ] 52 ; c ; <base64> BEL`: kitty, WezTerm, Alacritty, foot,
Ghostty, iTerm2 and Windows Terminal do, also over SSH. tmux passes it on with `set-clipboard on`. The terminal does
not answer, so whether it copied cannot be told.

## Decision

- `y` in the rich view copies what the cursor is on to the clipboard: its line, or the lines of an open range, in the
  order of the diff, each without its marker and joined by newlines; on the bar of a file, its path. Elsewhere, the
  lead, a marker or a thread, it does nothing. It is a key of every rich diff (`Scopes.DIFF`), and of the diff of a
  pull request's files next to the keys of its review (`Scopes.PULL_REQUEST_DIFF`).
- `v` opens a range in every rich diff, as it does in the diff of a pull request's files, and drops it: a range of a
  commit, a stash or the working tree is only copied yet. Off a line it does nothing there, as there is no review to
  say why.
- The range stays open after `y`, as it does after `c`: `v` or Escape drops it.
- The update works the text out, from the diff the area shows and what is expanded in it, and asks for a command that
  copies it: `CopyCmd`. Copying is IO, as it writes to the terminal.
- `state.Terminal` gets `copy`, which says whether it asked a terminal to copy, owns none, or could not write to it.
  `TerminalHandOver` writes the OSC 52 sequence through the backend of the app on the render thread, between two
  frames, so it never lands in the middle of one. Without a backend of its own it copies nothing, nor does `Terminal.NONE`.
- The command log says what was copied, how many lines or which path, as a notice. When nothing was, it says why:
  the app owns no terminal, or the terminal could not be written to.
- Not: selecting with the mouse. A drag would have to tell itself from a click that moves the cursor, and select
  within a line; that is left for later. The plain view (Ctrl+D), the log of a branch and the lead have no cursor on
  their lines, and copy nothing yet.
- Not: copying through `wl-copy`, `xclip` or `pbcopy`. They differ per system, are not always there, and do not
  reach the clipboard of the machine the terminal runs on over SSH.
- Not: the lines with their `+`, `-` or space. What is copied is the code, to paste elsewhere; a range across removed
  and added lines has both.

## Consequences

- Lines of any rich diff can be copied without the panes and the borders around them.
- Every rich diff has ranges now, which staging lines can build on (`wanted.md` 1).
- A terminal without OSC 52, or tmux without `set-clipboard on`, copies nothing, and the notice still says it did.
- SHAs, branch names and the text of comments can be copied through the same `Terminal.copy` later.

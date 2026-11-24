# Terminals and tmux

What terminals and tmux do that jazygit relies on, learned by testing, reading the source or docs, or measuring. Each
fact says when it was learned, so a fact that has grown old can be checked again; see README.md.

## Focus reports

- `ESC [?1004h` asks the terminal to report focus changes as `ESC [ I` (gained) and `ESC [ O` (lost). *Why it matters:*
  the timers pause while the terminal is away. — learned 2026-11-22 · [ADR
  0058](../adr/0058-less-reading-in-the-background.md)
- A terminal that does not report focus, or tmux without `focus-events`, never reports losing it (not every terminal
  reports focus changes); a report can get lost, be split over a slow link, or never be sent after a tmux detach; a key,
  click or paste only comes from a terminal that has the focus. *Why it matters:* any input also counts as the focus
  coming back, so the app is never paused for good; reading less in the background has no effect in such a terminal;
  with `git.auto-refresh` off, such a terminal never reads on a timer. — learned 2026-11-22 · [ADR
  0058](../adr/0058-less-reading-in-the-background.md), [ADR
  0067](../adr/0067-reading-less-rather-than-nothing-in-the-background.md), [ADR
  0125](../adr/0125-reading-the-status-every-3-s-and-turning-the-timers-off.md)

## Keys and keyboard layouts

- Terminals and multiplexers take Ctrl or Alt with the arrow keys for themselves. *Why it matters:* pane resizing uses
  `+` and `-` instead. — learned 2026-11-22 · [ADR 0092](../adr/0092-resizing-and-maximizing-panes-with-keys.md)
- `+` and `_` are next to each other only on QWERTY. *Why it matters:* the resize keys are chosen as characters that
  work on AZERTY, QWERTY and the numpad. — learned 2026-11-22 · [ADR
  0092](../adr/0092-resizing-and-maximizing-panes-with-keys.md)
- Many terminals send Ctrl+J as Enter. *Why it matters:* `J`/`K` were chosen for next and previous file instead. —
  learned 2026-11-22 · [ADR 0117](../adr/0117-the-next-file-from-the-diff.md)
- A terminal sends a key that is held down as the same key over and over. With only the kitty keyboard protocol's
  disambiguation and alternate-keys enhancements, a repeat reads as a press. The "report event types" flag marks repeats
  as repeats and also sends key releases (e.g. `ESC [ 1;1:3D` for Left released). *Why it matters:* jazygit asks for
  that flag so that holding Left stops at the start of the lines. — learned 2026-11-22 · [ADR
  0118](../adr/0118-left-held-down-stops-at-the-start-of-the-lines.md)
- kitty, WezTerm, foot, Ghostty and Konsole honour "report event types"; terminals without the kitty protocol, and tmux,
  ignore it and send a held key as presses. *Why it matters:* holding Left still leaves the diff in those terminals. —
  learned 2026-11-22 · [ADR 0118](../adr/0118-left-held-down-stops-at-the-start-of-the-lines.md)
- A terminal may send a key release just after it lost the focus, such as the Alt of Alt+Tab. *Why it matters:* a key
  release is not taken as a sign that the terminal has the focus again. — learned 2026-11-22 · [ADR
  0118](../adr/0118-left-held-down-stops-at-the-start-of-the-lines.md)

## Paste

- With bracketed paste turned off, a terminal sends pasted text as ordinary key events. *Why it matters:* the passphrase
  popup turns bracketed paste off so a paste arrives as keys, never as a `String` paste event. — learned 2026-11-22 ·
  [ADR 0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- With bracketed paste off, a newline in pasted text arrives as Enter, and whatever was pasted after it arrives as
  further keys. *Why it matters:* a pasted newline confirms the popup, and the rest of the paste reaches the panels. —
  learned 2026-11-22 · [ADR 0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)

## Mouse, selection and clipboard

- A terminal with mouse tracking reports a move for every cell the mouse crosses. *Why it matters:* a drag produces a
  burst of events that must not each be drawn. — learned 2026-11-22 · [ADR 0008](../adr/0008-resize-left-column.md)
- While an app captures the mouse, the terminal cannot select text; with Shift held most terminals select anyway, but
  only whole rows of the screen, including panes and borders. *Why it matters:* copying diff lines needs jazygit's own
  copy. — learned 2026-11-22 · [ADR 0094](../adr/0094-copying-lines-of-a-diff.md)
- OSC 52 (`ESC ] 52 ; c ; <base64> BEL`) asks a terminal to copy text. kitty, WezTerm, Alacritty, foot, Ghostty, iTerm2
  and Windows Terminal support it, also over SSH; tmux passes it on only with `set-clipboard on`. The terminal sends no
  reply, so success can't be detected. *Why it matters:* `y` copies through OSC 52, and the notice says it copied even
  where the terminal did not. — learned 2026-11-22 · [ADR 0094](../adr/0094-copying-lines-of-a-diff.md)
- `wl-copy`, `xclip` and `pbcopy` differ per system, are not always installed, and do not reach the clipboard of the
  machine the terminal runs on over SSH. *Why it matters:* they were rejected in favour of OSC 52. — learned 2026-11-22
  · [ADR 0094](../adr/0094-copying-lines-of-a-diff.md)

## Links

- OSC 8 hyperlinks (`ESC ] 8 ; ; <url> ESC \`) are clickable only if the terminal allows it: Konsole ignores them unless
  a profile setting allows them, tmux drops them without `terminal-features hyperlinks`, and some terminals don't know
  them at all. *Why it matters:* comment links are opened by jazygit on a click instead of relying on the terminal. —
  learned 2026-11-22 · [ADR 0096](../adr/0096-opening-the-links-of-a-comment.md)
- Terminals detect plain URLs on screen by themselves, but not a URL wrapped over several lines; a terminal opens a link
  in the desktop's browser. While an app captures the mouse, a terminal that supports OSC 8 opens a link when it is
  clicked with a modifier held. *Why it matters:* wrapped links in narrow panes need jazygit's click, and that click
  opens the browser of the repository's account. — learned 2026-11-22 · [ADR
  0096](../adr/0096-opening-the-links-of-a-comment.md)

## Character widths, emoji and fonts

- The emoji ✅ ❌ ⏳ 💤 👍 👎 🔀 🚦 💥 🟣 📚 are all East Asian Width `W` and every terminal draws them two columns
  wide. *Why it matters:* each takes two columns, which crowded the branch rows. — learned 2026-11-22 · [ADR
  0045](../adr/0045-two-lines-per-branch.md), [ADR 0048](../adr/0048-one-set-of-pull-request-symbols.md)
- Emoji need a terminal and font that draw them two columns wide. *Why it matters:* reactions rely on that, as Markdown
  in comments already does. — learned 2026-11-22 · [ADR 0086](../adr/0086-reactions-on-review-comments.md)
- A colour emoji ignores dimming in most terminals. *Why it matters:* the text after it carries the dim style. — learned
  2026-11-22 · [ADR 0032](../adr/0032-how-long-checks-take.md)
- `⏸` is drawn one column wide by most terminals and two by some. *Why it matters:* it is not used, as names after it
  would not line up. — learned 2026-11-22 · [ADR 0032](../adr/0032-how-long-checks-take.md), [ADR
  0035](../adr/0035-a-workflow-that-waits-to-start-is-queued.md)
- `●`, `≡`, `◆`, `↑` and `↓` are of ambiguous East Asian Width (`A`): one column in Western terminals, two in terminals
  set to draw ambiguous characters wide; `✓ ✗ ◌ + − ? » !` and `⇣` are `N`/`Na`, one column everywhere, and `◔`, `≣`,
  `⬥` are neutral alternatives. *Why it matters:* in an ambiguous-wide terminal, line 2 of a branch shifts and
  right-aligned check rows in dialogs push on the border. — learned 2026-11-22 · [ADR
  0045](../adr/0045-two-lines-per-branch.md), [ADR 0046](../adr/0046-how-far-a-branch-is-behind-its-base.md), [ADR
  0048](../adr/0048-one-set-of-pull-request-symbols.md)
- `◇`, like `◆`, `●` and `≡`, is of ambiguous East Asian width: one column in Western terminals, two in terminals set to
  draw ambiguous characters wide; `⬦` (U+2B26) is of neutral width. *Why it matters:* in wide-ambiguous terminals the
  rest of line 2 shifts right, and `⬦` is the fallback. — learned 2026-11-22 · [ADR
  0112](../adr/0112-merging-a-pull-request-when-it-is-ready.md)
- A terminal does not say which font it draws with, and a glyph the font lacks still takes a cell, so whether a Nerd
  Font is installed can't be detected. *Why it matters:* the collapsed column uses no icons. — learned 2026-11-22 · [ADR
  0104](../adr/0104-the-left-column-collapses.md)

## Images

- A terminal cell is about 8 pixels wide and 16 high. *Why it matters:* an image drawn in half blocks gets one cell
  column per 8 pixels and two pixel rows per cell. — learned 2026-11-22 · [ADR
  0084](../adr/0084-an-image-in-a-review-remark-is-drawn-in-half-blocks.md)
- Image protocols (Kitty, iTerm2, Sixel) draw past the cells of the buffer, so an image has to be placed, clipped and
  cleared again as the view scrolls. Half-block characters are ordinary cells. *Why it matters:* images are drawn in
  half blocks. — learned 2026-11-22 · [ADR 0084](../adr/0084-an-image-in-a-review-remark-is-drawn-in-half-blocks.md)

## tmux windows and programs in a terminal

- `tmux new-window -c {dir}` opens only a directory, not a file at a line. *Why it matters:* editors get separate
  `dir-command` and `file-command` entries. — learned 2026-11-22 · [ADR
  0145](../adr/0145-a-file-opened-in-an-editor-at-a-line.md)
- A program started in a new tmux window (`tmux new-window`) gets the environment of the tmux server, not that of the
  process that asked for the window. *Why it matters:* a token passed through the environment would silently never reach
  an agent left running in tmux. — learned 2026-11-22 · [ADR
  0162](../adr/0162-the-agent-is-told-the-account-of-the-repository.md)
- A text editor shows the ANSI escapes in a file as raw characters unless it renders them. *Why it matters:* cached
  GitHub job logs opened in an editor show escape codes in most editors. — learned 2026-11-22 · [ADR
  0160](../adr/0160-the-log-of-a-check.md)

## Images

- Kitty and Ghostty draw an image with the Kitty graphics protocol's Unicode placeholders: an image sent once with an
  id, as a virtual placement (`a=T,U=1`) of so many columns and rows, is drawn in each cell that is `U+10EEEE` with
  diacritics for its row and column, in the colour whose 24 bits are the id; a third diacritic gives the id's highest
  byte. WezTerm, Konsole and iTerm2 have no placeholders. *Why it matters:* a picture is cells like any other, which
  scroll and are covered by popups, and only Kitty and Ghostty use the protocol. — learned 2026-11-06 · [ADR
  0188](../adr/0188-the-terminal-draws-a-picture-sharp-where-it-can.md)
- A Sixel image is pixels written at the cursor; the terminal keeps no image to refer to, and text written over its
  pixels takes their place. `P2=1` leaves the pixels not drawn as they were. *Why it matters:* an image is written
  again after the cells of each frame that changes near it. — learned 2026-11-06 · [ADR
  0188](../adr/0188-the-terminal-draws-a-picture-sharp-where-it-can.md)
- No variable tells that a terminal has Sixel; asking (DA1) is answered on stdin, among the keys. tmux and screen pass
  neither protocol on without being set up to. *Why it matters:* the protocol is chosen from `TERM`, `TERM_PROGRAM`
  and a few variables a terminal sets, and is half blocks inside a multiplexer. — learned 2026-11-24 · [ADR
  0188](../adr/0188-the-terminal-draws-a-picture-sharp-where-it-can.md)
- `TIOCGWINSZ` tells the size of the window in pixels as well as in cells, where the terminal tells it; not on Windows.
  *Why it matters:* Sixel draws in pixels, so a cell's size has to be known. — learned 2026-11-06 · [ADR
  0188](../adr/0188-the-terminal-draws-a-picture-sharp-where-it-can.md)
- Konsole draws the last band of a Sixel image whole, six pixels high, past the height the raster attributes give.
  At a fractional scale (125% on Wayland) it tells a cell as 10 x 21 pixels (`TIOCGWINSZ`, `CSI 16 t`) but draws it
  about 12.46 device pixels wide, a fraction less than 10 x 1.25, so an image as wide as its cells by what it tells
  goes past them. Text written over an image takes its place. *Why it matters:* an image is cut to whole bands, and
  the cells right of and under it are written again after it. — learned 2026-11-24 · [ADR
  0188](../adr/0188-the-terminal-draws-a-picture-sharp-where-it-can.md)
